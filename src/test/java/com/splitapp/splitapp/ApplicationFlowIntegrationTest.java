package com.splitapp.splitapp;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationFlowIntegrationTest {
    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldRegisterUserAndCreateGroupForThem() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        send(user, "/groups", """
                {"groupName":"Wyjazd"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupName").value("Wyjazd"))
                .andExpect(jsonPath("$.createdById").value(user.id()))
                .andExpect(jsonPath("$.members[0].userId").value(user.id()))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    void shouldReturn409WhenRegisteringSameEmailTwice() throws Exception {
        String email = uniqueEmail();
        registerUser(email);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void shouldCreateExpenseInGroup() throws Exception {
        TestUser user = registerUser(uniqueEmail());
        long groupId = createGroup(user);

        send(user, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","description":"Obiad","paidById":%d,"participantIds":[%d]}
                """.formatted(user.id(), user.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.paidById").value(user.id()))
                .andExpect(jsonPath("$.createdById").value(user.id()))
                .andExpect(jsonPath("$.splits[0].userId").value(user.id()))
                .andExpect(jsonPath("$.splits[0].amount").value(10.0));
    }

    @Test
    void shouldReturn422WhenParticipantIsNotInGroup() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser outsider = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        send(owner, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                """.formatted(owner.id(), owner.id(), outsider.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"));
    }

    @Test
    void shouldReturn404WhenCreatingExpenseInUnknownGroup() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        send(user, "/groups/9223372036854775807/expenses", """
                {"amount":10.00,"category":"FOOD","paidById":1,"participantIds":[1]}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldReturn422WhenPaymentReceiverIsNotInGroup() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser outsider = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        send(owner, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":10.00}
                """.formatted(owner.id(), outsider.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"))
                .andExpect(jsonPath("$.detail").value("Receiver is not a member of this group"));
    }

    @Test
    void shouldReturn404WhenCreatingPaymentInUnknownGroup() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        send(user, "/groups/9223372036854775807/payments", """
                {"senderId":1,"receiverId":2,"amount":10.00}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldSplitExpenseBetweenGroupMembers() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        send(owner, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                """.formatted(owner.id(), owner.id(), friend.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splits[0].userId").value(owner.id()))
                .andExpect(jsonPath("$.splits[0].amount").value(5.0))
                .andExpect(jsonPath("$.splits[1].userId").value(friend.id()))
                .andExpect(jsonPath("$.splits[1].amount").value(5.0));
    }

    @Test
    void shouldRecordPaymentBetweenGroupMembers() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        send(friend, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":10.00}
                """.formatted(friend.id(), owner.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(friend.id()))
                .andExpect(jsonPath("$.receiverId").value(owner.id()))
                .andExpect(jsonPath("$.createdById").value(friend.id()))
                .andExpect(jsonPath("$.amount").value(10.0))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
    }

    @Test
    void shouldReturnGroupWithAllMembers() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        fetch(owner, "/groups/%d".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].userId").value(owner.id()))
                .andExpect(jsonPath("$.members[1].userId").value(friend.id()));
    }

    @Test
    void shouldReturnMembersInTheOrderTheyJoined() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser first = registerUser(uniqueEmail());
        TestUser second = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, second);
        addMember(groupId, owner, first);

        fetch(owner, "/groups/%d".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[0].userId").value(owner.id()))
                .andExpect(jsonPath("$.members[1].userId").value(second.id()))
                .andExpect(jsonPath("$.members[2].userId").value(first.id()));
    }

    @Test
    void shouldReturn404WhenGettingUnknownGroup() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        fetch(user, "/groups/9223372036854775807")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldListExpensesNewestFirst() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        createExpense(groupId, owner, "10.00");
        createExpense(groupId, owner, "20.00");

        fetch(owner, "/groups/%d/expenses".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(20.0))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].amount").value(10.0))
                .andExpect(jsonPath("$[0].splits[0].userId").value(owner.id()));
    }

    @Test
    void shouldListPaymentsNewestFirst() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        createPayment(groupId, friend, owner, "5.00");
        createPayment(groupId, owner, friend, "7.00");

        fetch(owner, "/groups/%d/payments".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(7.0))
                .andExpect(jsonPath("$[1].amount").value(5.0));
    }

    @Test
    void shouldReturn404WhenListingExpensesAndPaymentsOfUnknownGroup() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        fetch(user, "/groups/9223372036854775807/expenses")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
        fetch(user, "/groups/9223372036854775807/payments")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldComputeBalancesAndClearThemOnceTheReceiverConfirmsPayment() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        createSharedExpense(groupId, owner, friend, "10.00");

        fetch(owner, "/groups/%d/balances".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", owner.id()).value(5.0))
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", friend.id()).value(-5.0))
                .andExpect(jsonPath("$.transfers.length()").value(1))
                .andExpect(jsonPath("$.transfers[0].fromUserId").value(friend.id()))
                .andExpect(jsonPath("$.transfers[0].toUserId").value(owner.id()))
                .andExpect(jsonPath("$.transfers[0].amount").value(5.0));

        long paymentId = createPayment(groupId, friend, owner, "5.00");

        // recorded by the sender, so it does not count until the receiver confirms it
        fetch(owner, "/groups/%d/balances".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", owner.id()).value(5.0))
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", friend.id()).value(-5.0))
                .andExpect(jsonPath("$.transfers.length()").value(1));

        send(owner, "/groups/%d/payments/%d/confirm".formatted(groupId, paymentId), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        fetch(owner, "/groups/%d/balances".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", owner.id()).value(0.0))
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", friend.id()).value(0.0))
                .andExpect(jsonPath("$.transfers.length()").value(0));
    }

    @Test
    void shouldCountPaymentRecordedByReceiverAtOnce() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        createSharedExpense(groupId, owner, friend, "10.00");

        send(owner, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":5.00}
                """.formatted(friend.id(), owner.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        fetch(owner, "/groups/%d/balances".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfers.length()").value(0));
    }

    @Test
    void shouldAllowOnlyTheReceiverToConfirmAPayment() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        TestUser third = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        addMember(groupId, owner, third);
        long paymentId = createPayment(groupId, friend, third, "5.00");
        String url = "/groups/%d/payments/%d/confirm".formatted(groupId, paymentId);

        fetch(owner, "/groups/%d/payments".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        for (TestUser caller : List.of(friend, owner)) {
            send(caller, url, "")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail")
                            .value("Only the receiver can confirm this payment"));
        }

        send(third, url, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        send(third, url, "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Payment is already confirmed"));
        send(third, "/groups/%d/payments/9223372036854775807/confirm".formatted(groupId), "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"));
    }

    @Test
    void shouldReturn404WhenGettingBalancesOfUnknownGroup() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        fetch(user, "/groups/9223372036854775807/balances")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldSplitExpenseByExplicitAmountsAndReflectItInBalances() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        send(owner, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","paidById":%d,"splitType":"AMOUNT",
                 "shares":[{"userId":%d,"value":3.00},{"userId":%d,"value":7.00}]}
                """.formatted(owner.id(), owner.id(), friend.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splits[0].amount").value(3.0))
                .andExpect(jsonPath("$.splits[1].amount").value(7.0));

        fetch(owner, "/groups/%d/balances".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", owner.id()).value(7.0))
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", friend.id()).value(-7.0));
    }

    @Test
    void shouldSplitExpenseByPercentagesAndRejectWrongTotal() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        send(owner, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","paidById":%d,"splitType":"PERCENT",
                 "shares":[{"userId":%d,"value":60},{"userId":%d,"value":40}]}
                """.formatted(owner.id(), owner.id(), friend.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splits[0].amount").value(6.0))
                .andExpect(jsonPath("$.splits[1].amount").value(4.0));

        send(owner, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","paidById":%d,"splitType":"PERCENT",
                 "shares":[{"userId":%d,"value":60},{"userId":%d,"value":30}]}
                """.formatted(owner.id(), owner.id(), friend.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Percentages must add up to 100"));
    }

    @Test
    void shouldReturn401WithoutCredentials() throws Exception {
        mockMvc.perform(get("/groups/9223372036854775807"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void shouldAuthenticateRegisteredUserWithBasicAuth() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        // 404 means the request got past authentication and reached the controller
        fetch(user, "/groups/9223372036854775807")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldReturn401ForWrongPasswordAndUnknownUser() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        mockMvc.perform(get("/groups/9223372036854775807").with(httpBasic(user.email(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mockMvc.perform(get("/groups/9223372036854775807").with(httpBasic("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenNonMemberUsesGroupEndpoints() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser outsider = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        for (String path : List.of("", "/expenses", "/payments", "/balances", "/invitations")) {
            fetch(outsider, "/groups/%d%s".formatted(groupId, path))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Forbidden"));
        }
        send(outsider, "/groups/%d/invitations".formatted(groupId), """
                {"email":"%s"}
                """.formatted(outsider.email()))
                .andExpect(status().isForbidden());
        send(outsider, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","paidById":%d,"participantIds":[%d]}
                """.formatted(outsider.id(), outsider.id()))
                .andExpect(status().isForbidden());
        send(outsider, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":10.00}
                """.formatted(outsider.id(), owner.id()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldGrantAccessAfterBeingAddedToGroup() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        fetch(friend, "/groups/%d".formatted(groupId)).andExpect(status().isForbidden());

        addMember(groupId, owner, friend);

        fetch(friend, "/groups/%d".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void shouldCreateGroupForLoggedInUserEvenWhenBodyContainsAnotherUserId() throws Exception {
        TestUser user = registerUser(uniqueEmail());
        TestUser other = registerUser(uniqueEmail());

        send(user, "/groups", """
                {"groupName":"Wyjazd","userId":%d}
                """.formatted(other.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdById").value(user.id()))
                .andExpect(jsonPath("$.members[0].userId").value(user.id()));
    }

    @Test
    void shouldRecordWhoCreatedAnExpensePaidBySomeoneElse() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        send(friend, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                """.formatted(owner.id(), owner.id(), friend.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidById").value(owner.id()))
                .andExpect(jsonPath("$.createdById").value(friend.id()));

        fetch(owner, "/groups/%d/expenses".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].createdById").value(friend.id()));
    }

    @Test
    void shouldReturn403WhenPaymentIsRecordedByNeitherSenderNorReceiver() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        TestUser third = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        addMember(groupId, owner, third);

        send(friend, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":5.00}
                """.formatted(third.id(), owner.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail")
                        .value("You can only record payments you are part of"));

        createPayment(groupId, third, owner, "5.00");
        send(owner, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":6.00}
                """.formatted(third.id(), owner.id()))
                .andExpect(status().isCreated());

        fetch(owner, "/groups/%d/payments".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldDeleteExpenseAndRestoreBalances() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        long expenseId = createSharedExpense(groupId, owner, friend, "10.00");

        remove(owner, "/groups/%d/expenses/%d".formatted(groupId, expenseId))
                .andExpect(status().isNoContent());

        fetch(owner, "/groups/%d/expenses".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        fetch(owner, "/groups/%d/balances".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfers.length()").value(0));
    }

    @Test
    void shouldAllowOnlyAuthorOrOwnerToDeleteAnExpense() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        TestUser third = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        addMember(groupId, owner, third);
        long expenseId = createSharedExpense(groupId, friend, third, "10.00");
        String url = "/groups/%d/expenses/%d".formatted(groupId, expenseId);

        remove(third, url)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        remove(owner, url).andExpect(status().isNoContent());
        remove(owner, url)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Expense not found"));
    }

    @Test
    void shouldAllowOnlyTheAuthorTheOwnerOrTheReceiverOfAPendingPaymentToDelete() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        TestUser third = registerUser(uniqueEmail());
        TestUser bystander = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        addMember(groupId, owner, third);
        addMember(groupId, owner, bystander);
        long pendingId = createPayment(groupId, friend, third, "5.00");
        String pendingUrl = "/groups/%d/payments/%d".formatted(groupId, pendingId);

        remove(bystander, pendingUrl)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        // the receiver may reject a payment that is still waiting for confirmation
        remove(third, pendingUrl).andExpect(status().isNoContent());

        long confirmedId = createPayment(groupId, friend, third, "5.00");
        String confirmedUrl = "/groups/%d/payments/%d".formatted(groupId, confirmedId);
        send(third, confirmedUrl + "/confirm", "").andExpect(status().isOk());

        // once confirmed, the receiver cannot take the confirmation back, the author can
        remove(third, confirmedUrl)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        remove(friend, confirmedUrl).andExpect(status().isNoContent());

        fetch(owner, "/groups/%d/payments".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldLetTheReceiverRejectAPendingPaymentAndThenLeave() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        // the owner records a payment that the friend never agreed to
        long paymentId = createPayment(groupId, owner, friend, "5.00");
        String leaveUrl = "/groups/%d/leave".formatted(groupId);

        send(friend, leaveUrl, "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("You have pending payments in this group; "
                        + "wait until they are confirmed or delete them"));

        remove(friend, "/groups/%d/payments/%d".formatted(groupId, paymentId))
                .andExpect(status().isNoContent());
        send(friend, leaveUrl, "").andExpect(status().isNoContent());

        fetch(owner, "/groups/%d".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    void shouldReturn404ForEntryOfAnotherGroupAndForbidNonMembers() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser other = registerUser(uniqueEmail());
        long groupA = createGroup(owner);
        long groupB = createGroup(other);
        long expenseId = createExpense(groupA, owner, "10.00");

        remove(other, "/groups/%d/expenses/%d".formatted(groupB, expenseId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Expense not found"));
        remove(other, "/groups/%d/expenses/%d".formatted(groupA, expenseId))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldJoinGroupAfterAcceptingInvitation() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        // the address is matched case-insensitively
        long invitationId = invite(groupId, owner, friend.email().toUpperCase());

        // before accepting, the invitee sees only the group name and who invited them
        fetch(friend, "/invitations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(invitationId))
                .andExpect(jsonPath("$[0].groupName").value("Wyjazd"))
                .andExpect(jsonPath("$[0].members").doesNotExist());
        fetch(friend, "/groups/%d".formatted(groupId)).andExpect(status().isForbidden());

        send(friend, "/invitations/%d/accept".formatted(invitationId), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[1].userId").value(friend.id()))
                .andExpect(jsonPath("$.members[1].role").value("MEMBER"));

        fetch(friend, "/groups/%d".formatted(groupId)).andExpect(status().isOk());
        fetch(friend, "/invitations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldLetOnlyTheInvitedPersonAnswerAnInvitation() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        TestUser stranger = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        long invitationId = invite(groupId, owner, friend.email());
        String accept = "/invitations/%d/accept".formatted(invitationId);

        send(stranger, accept, "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Invitation not found"));
        send(owner, accept, "").andExpect(status().isNotFound());
        fetch(stranger, "/invitations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        fetch(stranger, "/groups/%d".formatted(groupId)).andExpect(status().isForbidden());

        send(friend, "/invitations/%d/decline".formatted(invitationId), "")
                .andExpect(status().isNoContent());
        send(friend, accept, "").andExpect(status().isNotFound());
        fetch(owner, "/groups/%d/invitations".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // a declined invitation does not block inviting the same person again
        invite(groupId, owner, friend.email());
    }

    @Test
    void shouldAllowOnlyTheOwnerToInviteAndManageInvitations() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        TestUser outsider = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        String invitationsUrl = "/groups/%d/invitations".formatted(groupId);
        String newcomerEmail = uniqueEmail();

        send(friend, invitationsUrl, """
                {"email":"%s"}
                """.formatted(newcomerEmail))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the group owner can invite members"));
        send(outsider, invitationsUrl, """
                {"email":"%s"}
                """.formatted(newcomerEmail))
                .andExpect(status().isForbidden());
        fetch(friend, invitationsUrl).andExpect(status().isForbidden());

        long invitationId = invite(groupId, owner, newcomerEmail);
        send(owner, invitationsUrl, """
                {"email":"%s"}
                """.formatted(newcomerEmail))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("This e-mail already has a pending invitation"));
        send(owner, invitationsUrl, """
                {"email":"%s"}
                """.formatted(friend.email()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("User is already a member of this group"));

        fetch(owner, invitationsUrl)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(newcomerEmail));

        String cancelUrl = "%s/%d".formatted(invitationsUrl, invitationId);
        remove(friend, cancelUrl).andExpect(status().isForbidden());
        remove(owner, cancelUrl).andExpect(status().isNoContent());
        remove(owner, cancelUrl)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Invitation not found"));
        fetch(owner, invitationsUrl)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldLetSomeoneWhoRegistersLaterAcceptAnInvitation() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        String lateEmail = uniqueEmail();

        // the invitation succeeds although nobody has registered with this address yet
        invite(groupId, owner, lateEmail);

        TestUser late = registerUser(lateEmail);
        MvcResult pending = fetch(late, "/invitations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        Number invitationId = JsonPath.read(pending.getResponse().getContentAsString(), "$[0].id");

        send(late, "/invitations/%d/accept".formatted(invitationId.longValue()), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void shouldTransferOwnershipAndSwitchPrivileges() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        TestUser outsider = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        String ownerUrl = "/groups/%d/owner".formatted(groupId);

        send(owner, ownerUrl, """
                {"userId":%d}
                """.formatted(owner.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("You are already the owner of this group"));
        send(owner, ownerUrl, """
                {"userId":%d}
                """.formatted(outsider.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("The new owner must be a member of this group"));
        send(friend, ownerUrl, """
                {"userId":%d}
                """.formatted(friend.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Only the group owner can transfer ownership"));

        send(owner, ownerUrl, """
                {"userId":%d}
                """.formatted(friend.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].userId").value(owner.id()))
                .andExpect(jsonPath("$.members[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.members[1].userId").value(friend.id()))
                .andExpect(jsonPath("$.members[1].role").value("OWNER"));

        send(owner, "/groups/%d/invitations".formatted(groupId), """
                {"email":"%s"}
                """.formatted(outsider.email()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the group owner can invite members"));
        invite(groupId, friend, outsider.email());
    }

    @Test
    void shouldLetMemberLeaveOnlyAfterSettlingUp() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        createSharedExpense(groupId, owner, friend, "10.00");
        String leaveUrl = "/groups/%d/leave".formatted(groupId);

        send(friend, leaveUrl, "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Settle your balance before leaving the group"));

        long paymentId = createPayment(groupId, friend, owner, "5.00");
        send(friend, leaveUrl, "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("You have pending payments in this group; "
                        + "wait until they are confirmed or delete them"));

        send(owner, "/groups/%d/payments/%d/confirm".formatted(groupId, paymentId), "")
                .andExpect(status().isOk());
        send(friend, leaveUrl, "").andExpect(status().isNoContent());

        fetch(friend, "/groups/%d".formatted(groupId)).andExpect(status().isForbidden());
        fetch(owner, "/groups/%d".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1));
        // the history stays
        fetch(owner, "/groups/%d/expenses".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // a former member can be invited again
        addMember(groupId, owner, friend);
        fetch(friend, "/groups/%d".formatted(groupId)).andExpect(status().isOk());
    }

    @Test
    void shouldNotLetTheOwnerLeaveUntilOwnershipIsTransferred() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        String leaveUrl = "/groups/%d/leave".formatted(groupId);
        String ownerMessage = "The group owner cannot leave; "
                + "transfer ownership to another member first";

        send(owner, leaveUrl, "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(ownerMessage));

        send(owner, "/groups/%d/owner".formatted(groupId), """
                {"userId":%d}
                """.formatted(friend.id()))
                .andExpect(status().isOk());
        send(owner, leaveUrl, "").andExpect(status().isNoContent());

        fetch(friend, "/groups/%d".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
        // the new owner is now alone and cannot leave either
        send(friend, leaveUrl, "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(ownerMessage));
    }

    @Test
    void shouldReturn403OrNotFoundWhenOutsiderChangesMembership() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser outsider = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        send(outsider, "/groups/%d/leave".formatted(groupId), "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        send(outsider, "/groups/%d/owner".formatted(groupId), """
                {"userId":%d}
                """.formatted(outsider.id()))
                .andExpect(status().isForbidden());
        send(outsider, "/groups/9223372036854775807/leave", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldNotDeleteEntriesThatInvolveAFormerMember() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        long expenseId = createSharedExpense(groupId, owner, friend, "10.00");
        // recorded by the receiver, so it counts at once and the friend can leave
        long paymentId = idOf(send(owner, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":5.00}
                """.formatted(friend.id(), owner.id()))
                .andExpect(status().isCreated())
                .andReturn());
        send(friend, "/groups/%d/leave".formatted(groupId), "").andExpect(status().isNoContent());

        remove(owner, "/groups/%d/expenses/%d".formatted(groupId, expenseId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "Cannot delete an expense that involves a user who has left the group"));
        remove(owner, "/groups/%d/payments/%d".formatted(groupId, paymentId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "Cannot delete a payment that involves a user who has left the group"));

        // nothing was removed, so the balances still add up
        fetch(owner, "/groups/%d/expenses".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        fetch(owner, "/groups/%d/payments".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        fetch(owner, "/groups/%d/balances".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].balance").value(0.0))
                .andExpect(jsonPath("$.transfers.length()").value(0));

        // entries that do not involve the former member can still be deleted
        long ownExpenseId = createExpense(groupId, owner, "7.00");
        remove(owner, "/groups/%d/expenses/%d".formatted(groupId, ownExpenseId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectPostsThatAnHtmlFormCouldSend() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        long paymentId = createPayment(groupId, friend, owner, "5.00");
        String url = "/groups/%d/payments/%d/confirm".formatted(groupId, paymentId);

        // valid credentials, but a form content type, or no content type at all
        mockMvc.perform(post(url).with(as(owner))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post(url).with(as(owner)))
                .andExpect(status().isUnsupportedMediaType());

        fetch(owner, "/groups/%d/payments".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void shouldKeepASingleOwnerWhenOwnershipIsTransferredTwiceAtOnce() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser first = registerUser(uniqueEmail());
        TestUser second = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, first);
        addMember(groupId, owner, second);
        String url = "/groups/%d/owner".formatted(groupId);

        List<Integer> statuses = runAtTheSameTime(List.of(
                () -> statusOfPost(owner, url, """
                        {"userId":%d}
                        """.formatted(first.id())),
                () -> statusOfPost(owner, url, """
                        {"userId":%d}
                        """.formatted(second.id()))));

        // the second transfer finds out that the caller is no longer the owner
        assertEquals(1, statuses.stream().filter(status -> status == 200).count());
        assertEquals(1, statuses.stream().filter(status -> status == 403).count());
        assertEquals(1, ownerCount(owner, groupId));
    }

    @Test
    void shouldNotChargeAMemberWhoLeftFirst() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        // the friend's leaving reaches the database first, the expense queues up behind it
        List<Integer> statuses = runBehindRowLock(memberRowLock(groupId, friend), List.of(
                () -> statusOfPost(friend, "/groups/%d/leave".formatted(groupId), ""),
                () -> statusOfPost(owner, "/groups/%d/expenses".formatted(groupId), """
                        {"amount":10.00,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                        """.formatted(owner.id(), owner.id(), friend.id()))));

        // the expense finds out that the friend is no longer a member
        assertEquals(List.of(204, 422), statuses);
        assertEquals(403, statusOfGet(friend, "/groups/%d".formatted(groupId)));
        fetch(owner, "/groups/%d/expenses".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldNotLetTheNewOwnerLeaveWhileOwnershipIsBeingTransferred() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        // the transfer reaches the database first, the friend's leaving queues up behind it
        List<Integer> statuses = runBehindRowLock(memberRowLock(groupId, friend), List.of(
                () -> statusOfPost(owner, "/groups/%d/owner".formatted(groupId), """
                        {"userId":%d}
                        """.formatted(friend.id())),
                () -> statusOfPost(friend, "/groups/%d/leave".formatted(groupId), "")));

        // by the time the leaving request gets the lock the friend is the owner, and an
        // owner cannot leave, so the group is never left without an owner
        assertEquals(List.of(200, 422), statuses);
        assertEquals(1, ownerCount(owner, groupId));
        assertEquals(200, statusOfGet(friend, "/groups/%d".formatted(groupId)));
    }

    @Test
    void shouldNotExposeTheDefaultLogoutEndpoint() throws Exception {
        mockMvc.perform(get("/logout"))
                .andExpect(status().isUnauthorized());

        TestUser user = registerUser(uniqueEmail());
        fetch(user, "/logout").andExpect(status().isNotFound());
    }

    @Test
    void shouldLetAClientDiscoverItsOwnUserAndGroups() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long first = createGroup(owner);
        long second = createGroup(friend);
        addMember(second, friend, owner);
        createGroup(friend);

        fetch(owner, "/users/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.id()))
                .andExpect(jsonPath("$.email").value(owner.email()))
                .andExpect(jsonPath("$.password").doesNotExist());

        // only the groups the caller belongs to, newest first, with the caller's own role
        fetch(owner, "/groups")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].myRole").value("MEMBER"))
                .andExpect(jsonPath("$[1].id").value(first))
                .andExpect(jsonPath("$[1].myRole").value("OWNER"))
                .andExpect(jsonPath("$[0].members").doesNotExist());
    }

    @Test
    void shouldRequireCredentialsToDiscoverOwnUserAndGroups() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectNulCharactersInTextFields() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"A\\u0000B","lastName":"Kowalski","email":"%s","password":"%s"}
                                """.formatted(uniqueEmail(), PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists());

        send(owner, "/groups", """
                {"groupName":"G\\u0000H"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.groupName").exists());

        send(owner, "/groups", """
                {"groupName":"Wyjazd","groupDescription":"a\\u0000b"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.groupDescription").exists());

        send(owner, "/groups/%d/expenses".formatted(groupId), """
                {"amount":10.00,"category":"FOOD","description":"a\\u0000b","paidById":%d,"participantIds":[%d]}
                """.formatted(owner.id(), owner.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists());

        send(owner, "/groups/%d/invitations".formatted(groupId), """
                {"email":"a\\u0000b@example.com"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void shouldNotAcceptAnInvitationThatWasCancelledFirst() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser invitee = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        long invitationId = invite(groupId, owner, invitee.email());

        // the cancellation reaches the database first, the acceptance queues up behind it
        List<Integer> statuses = runBehindRowLock(
                "select id from group_invitations where id = " + invitationId + " for update",
                List.of(
                        () -> statusOfDelete(owner,
                                "/groups/%d/invitations/%d".formatted(groupId, invitationId)),
                        () -> statusOfPost(invitee,
                                "/invitations/%d/accept".formatted(invitationId), "")));

        assertEquals(List.of(204, 404), statuses);
        assertEquals(403, statusOfGet(invitee, "/groups/%d".formatted(groupId)));
    }

    @Test
    void shouldNotCancelAnInvitationThatWasAcceptedFirst() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser invitee = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        long invitationId = invite(groupId, owner, invitee.email());

        // the acceptance reaches the database first, the cancellation queues up behind it
        List<Integer> statuses = runBehindRowLock(
                "select id from group_invitations where id = " + invitationId + " for update",
                List.of(
                        () -> statusOfPost(invitee,
                                "/invitations/%d/accept".formatted(invitationId), ""),
                        () -> statusOfDelete(owner,
                                "/groups/%d/invitations/%d".formatted(groupId, invitationId))));

        assertEquals(List.of(200, 422), statuses);
        assertEquals(200, statusOfGet(invitee, "/groups/%d".formatted(groupId)));
    }

    @Test
    void shouldNotConfirmAPaymentThatWasDeletedFirst() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);
        long paymentId = createPayment(groupId, friend, owner, "5.00");
        String paymentUrl = "/groups/%d/payments/%d".formatted(groupId, paymentId);

        // the author's deletion reaches the database first, the confirmation queues up behind it
        List<Integer> statuses = runBehindRowLock(
                "select id from payments where id = " + paymentId + " for update",
                List.of(
                        () -> statusOfDelete(friend, paymentUrl),
                        () -> statusOfPost(owner, paymentUrl + "/confirm", "")));

        assertEquals(List.of(204, 404), statuses);
        fetch(owner, "/groups/%d/payments".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldPublishTheApiDescriptionWithoutLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.paths['/groups']").exists())
                .andExpect(jsonPath("$.paths['/users'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/groups'].get.parameters").doesNotExist());

        mockMvc.perform(get("/groups"))
                .andExpect(status().isUnauthorized());
    }

    private record TestUser(long id, String email) {
    }

    private static RequestPostProcessor as(TestUser user) {
        return httpBasic(user.email(), PASSWORD);
    }

    private ResultActions send(TestUser user, String url, String body) throws Exception {
        return mockMvc.perform(post(url)
                .with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions fetch(TestUser user, String url) throws Exception {
        return mockMvc.perform(get(url).with(as(user)));
    }

    private ResultActions remove(TestUser user, String url) throws Exception {
        return mockMvc.perform(delete(url).with(as(user)));
    }

    private int statusOfPost(TestUser user, String url, String body) throws Exception {
        return send(user, url, body).andReturn().getResponse().getStatus();
    }

    private int statusOfDelete(TestUser user, String url) throws Exception {
        return remove(user, url).andReturn().getResponse().getStatus();
    }

    private int statusOfGet(TestUser user, String url) throws Exception {
        return fetch(user, url).andReturn().getResponse().getStatus();
    }

    private int ownerCount(TestUser viewer, long groupId) throws Exception {
        String json = fetch(viewer, "/groups/%d".formatted(groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Object> owners = JsonPath.read(json, "$.members[?(@.role=='OWNER')]");
        return owners.size();
    }

    // starts all actions together, so that their transactions overlap
    private List<Integer> runAtTheSameTime(List<Callable<Integer>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        try {
            CountDownLatch ready = new CountDownLatch(actions.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> action : actions) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            ready.await();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Holds a row lock while the actions are started one by one, each only after the previous
     * one is waiting for a database lock (or has already finished). The transactions overlap
     * and reach the database in a known order, so the outcome does not depend on timing.
     */
    private List<Integer> runBehindRowLock(String lockRowSql, List<Callable<Integer>> actions)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (Statement statement = holder.createStatement()) {
                statement.execute(lockRowSql);
            }

            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < actions.size(); i++) {
                Future<Integer> future = pool.submit(actions.get(i));
                futures.add(future);
                waitUntilBlockedOrDone(future, i + 1);
            }
            holder.commit();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private void waitUntilBlockedOrDone(Future<Integer> future, int expectedWaiting)
            throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (future.isDone()) {
                return;
            }
            Integer waiting = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and wait_event_type = 'Lock'
                    """, Integer.class);
            if (waiting != null && waiting >= expectedWaiting) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Expected " + expectedWaiting
                + " request(s) to wait for a lock or finish");
    }

    private static String memberRowLock(long groupId, TestUser user) {
        return "select 1 from group_members where group_id = " + groupId
                + " and user_id = " + user.id() + " for update";
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private TestUser registerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        return new TestUser(idOf(result), email);
    }

    private long createGroup(TestUser owner) throws Exception {
        return idOf(send(owner, "/groups", """
                {"groupName":"Wyjazd"}
                """)
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void addMember(long groupId, TestUser owner, TestUser newMember) throws Exception {
        long invitationId = invite(groupId, owner, newMember.email());
        send(newMember, "/invitations/%d/accept".formatted(invitationId), "")
                .andExpect(status().isOk());
    }

    private long createExpense(long groupId, TestUser payer, String amount) throws Exception {
        return idOf(send(payer, "/groups/%d/expenses".formatted(groupId), """
                {"amount":%s,"category":"FOOD","paidById":%d,"participantIds":[%d]}
                """.formatted(amount, payer.id(), payer.id()))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long createSharedExpense(long groupId, TestUser payer, TestUser other, String amount)
            throws Exception {
        return idOf(send(payer, "/groups/%d/expenses".formatted(groupId), """
                {"amount":%s,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                """.formatted(amount, payer.id(), payer.id(), other.id()))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long createPayment(long groupId, TestUser sender, TestUser receiver, String amount)
            throws Exception {
        return idOf(send(sender, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":%s}
                """.formatted(sender.id(), receiver.id(), amount))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long invite(long groupId, TestUser owner, String email) throws Exception {
        return idOf(send(owner, "/groups/%d/invitations".formatted(groupId), """
                {"email":"%s"}
                """.formatted(email))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static String registerBody(String email) {
        return """
                {"firstName":"Jan","lastName":"Kowalski","email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
    }

    // the database is shared between tests in this class and is not rolled back
    private static String uniqueEmail() {
        return "e2e-" + UUID.randomUUID() + "@example.com";
    }
}
