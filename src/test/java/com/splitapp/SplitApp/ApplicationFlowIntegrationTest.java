package com.splitapp.SplitApp;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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

        send(user, "/groups/999999/expenses", """
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

        send(user, "/groups/999999/payments", """
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
                .andExpect(jsonPath("$.amount").value(10.0))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
    }

    @Test
    void shouldReturn422WhenAddingSameMemberTwice() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        send(owner, "/groups/%d/members".formatted(groupId), """
                {"userId":%d}
                """.formatted(owner.id()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("User is already a member of this group"));
    }

    @Test
    void shouldReturn404WhenAddingUnknownUserToGroup() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        send(owner, "/groups/%d/members".formatted(groupId), """
                {"userId":999999}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
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
    void shouldReturn404WhenGettingUnknownGroup() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        fetch(user, "/groups/999999")
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

        fetch(user, "/groups/999999/expenses")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
        fetch(user, "/groups/999999/payments")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldComputeBalancesAndClearThemAfterPayment() throws Exception {
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

        createPayment(groupId, friend, owner, "5.00");

        fetch(owner, "/groups/%d/balances".formatted(groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", owner.id()).value(0.0))
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", friend.id()).value(0.0))
                .andExpect(jsonPath("$.transfers.length()").value(0));
    }

    @Test
    void shouldReturn404WhenGettingBalancesOfUnknownGroup() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        fetch(user, "/groups/999999/balances")
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
        mockMvc.perform(get("/groups/999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void shouldAuthenticateRegisteredUserWithBasicAuth() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        // 404 means the request got past authentication and reached the controller
        fetch(user, "/groups/999999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldReturn401ForWrongPasswordAndUnknownUser() throws Exception {
        TestUser user = registerUser(uniqueEmail());

        mockMvc.perform(get("/groups/999999").with(httpBasic(user.email(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mockMvc.perform(get("/groups/999999").with(httpBasic("nobody@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenNonMemberUsesGroupEndpoints() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser outsider = registerUser(uniqueEmail());
        long groupId = createGroup(owner);

        for (String path : List.of("", "/expenses", "/payments", "/balances")) {
            fetch(outsider, "/groups/%d%s".formatted(groupId, path))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Forbidden"));
        }
        send(outsider, "/groups/%d/members".formatted(groupId), """
                {"userId":%d}
                """.formatted(outsider.id()))
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
    void shouldReturn403WhenRegularMemberAddsMemberButOwnerCan() throws Exception {
        TestUser owner = registerUser(uniqueEmail());
        TestUser friend = registerUser(uniqueEmail());
        TestUser newcomer = registerUser(uniqueEmail());
        long groupId = createGroup(owner);
        addMember(groupId, owner, friend);

        send(friend, "/groups/%d/members".formatted(groupId), """
                {"userId":%d}
                """.formatted(newcomer.id()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Only the group owner can add members"));

        fetch(newcomer, "/groups/%d".formatted(groupId)).andExpect(status().isForbidden());

        addMember(groupId, owner, newcomer);

        fetch(newcomer, "/groups/%d".formatted(groupId)).andExpect(status().isOk());
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

    private TestUser registerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return new TestUser(id.longValue(), email);
    }

    private long createGroup(TestUser owner) throws Exception {
        MvcResult result = send(owner, "/groups", """
                {"groupName":"Wyjazd"}
                """)
                .andExpect(status().isCreated())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private void addMember(long groupId, TestUser actor, TestUser newMember) throws Exception {
        send(actor, "/groups/%d/members".formatted(groupId), """
                {"userId":%d}
                """.formatted(newMember.id()))
                .andExpect(status().isCreated());
    }

    private void createExpense(long groupId, TestUser payer, String amount) throws Exception {
        send(payer, "/groups/%d/expenses".formatted(groupId), """
                {"amount":%s,"category":"FOOD","paidById":%d,"participantIds":[%d]}
                """.formatted(amount, payer.id(), payer.id()))
                .andExpect(status().isCreated());
    }

    private void createSharedExpense(long groupId, TestUser payer, TestUser other, String amount)
            throws Exception {
        send(payer, "/groups/%d/expenses".formatted(groupId), """
                {"amount":%s,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                """.formatted(amount, payer.id(), payer.id(), other.id()))
                .andExpect(status().isCreated());
    }

    private void createPayment(long groupId, TestUser sender, TestUser receiver, String amount)
            throws Exception {
        send(sender, "/groups/%d/payments".formatted(groupId), """
                {"senderId":%d,"receiverId":%d,"amount":%s}
                """.formatted(sender.id(), receiver.id(), amount))
                .andExpect(status().isCreated());
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
