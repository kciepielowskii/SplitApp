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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationFlowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRegisterUserAndCreateGroupForThem() throws Exception {
        String email = uniqueEmail();

        long userId = registerUser(email);

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":%d}
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupName").value("Wyjazd"))
                .andExpect(jsonPath("$.createdById").value(userId))
                .andExpect(jsonPath("$.members[0].userId").value(userId))
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
    void shouldReturn404WhenCreatingGroupForUnknownUser() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":999999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    @Test
    void shouldCreateExpenseInGroup() throws Exception {
        long userId = registerUser(uniqueEmail());
        long groupId = createGroup(userId);

        mockMvc.perform(post("/groups/%d/expenses".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","description":"Obiad","paidById":%d,"participantIds":[%d]}
                                """.formatted(userId, userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.paidById").value(userId))
                .andExpect(jsonPath("$.splits[0].userId").value(userId))
                .andExpect(jsonPath("$.splits[0].amount").value(10.0));
    }

    @Test
    void shouldReturn422WhenParticipantIsNotInGroup() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long outsiderId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);

        mockMvc.perform(post("/groups/%d/expenses".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                                """.formatted(ownerId, ownerId, outsiderId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"));
    }

    @Test
    void shouldReturn404WhenCreatingExpenseInUnknownGroup() throws Exception {
        mockMvc.perform(post("/groups/999999/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":1,"participantIds":[1]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldReturn422WhenPaymentReceiverIsNotInGroup() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long outsiderId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);

        mockMvc.perform(post("/groups/%d/payments".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senderId":%d,"receiverId":%d,"amount":10.00}
                                """.formatted(ownerId, outsiderId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"))
                .andExpect(jsonPath("$.detail").value("Receiver is not a member of this group"));
    }

    @Test
    void shouldReturn404WhenCreatingPaymentInUnknownGroup() throws Exception {
        mockMvc.perform(post("/groups/999999/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senderId":1,"receiverId":2,"amount":10.00}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldSplitExpenseBetweenGroupMembers() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long friendId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);
        addMember(groupId, friendId);

        mockMvc.perform(post("/groups/%d/expenses".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                                """.formatted(ownerId, ownerId, friendId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splits[0].userId").value(ownerId))
                .andExpect(jsonPath("$.splits[0].amount").value(5.0))
                .andExpect(jsonPath("$.splits[1].userId").value(friendId))
                .andExpect(jsonPath("$.splits[1].amount").value(5.0));
    }

    @Test
    void shouldRecordPaymentBetweenGroupMembers() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long friendId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);
        addMember(groupId, friendId);

        mockMvc.perform(post("/groups/%d/payments".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senderId":%d,"receiverId":%d,"amount":10.00}
                                """.formatted(friendId, ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderId").value(friendId))
                .andExpect(jsonPath("$.receiverId").value(ownerId))
                .andExpect(jsonPath("$.amount").value(10.0))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
    }

    @Test
    void shouldReturn422WhenAddingSameMemberTwice() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);

        mockMvc.perform(post("/groups/%d/members".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d}
                                """.formatted(ownerId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("User is already a member of this group"));
    }

    @Test
    void shouldReturn404WhenAddingUnknownUserToGroup() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);

        mockMvc.perform(post("/groups/%d/members".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":999999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }

    @Test
    void shouldReturnGroupWithAllMembers() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long friendId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);
        addMember(groupId, friendId);

        mockMvc.perform(get("/groups/%d".formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].userId").value(ownerId))
                .andExpect(jsonPath("$.members[1].userId").value(friendId));
    }

    @Test
    void shouldReturn404WhenGettingUnknownGroup() throws Exception {
        mockMvc.perform(get("/groups/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldListExpensesNewestFirst() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);
        createExpense(groupId, ownerId, "10.00");
        createExpense(groupId, ownerId, "20.00");

        mockMvc.perform(get("/groups/%d/expenses".formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(20.0))
                .andExpect(jsonPath("$[1].amount").value(10.0))
                .andExpect(jsonPath("$[0].splits[0].userId").value(ownerId));
    }

    @Test
    void shouldListPaymentsNewestFirst() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long friendId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);
        addMember(groupId, friendId);
        createPayment(groupId, friendId, ownerId, "5.00");
        createPayment(groupId, ownerId, friendId, "7.00");

        mockMvc.perform(get("/groups/%d/payments".formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(7.0))
                .andExpect(jsonPath("$[1].amount").value(5.0));
    }

    @Test
    void shouldReturn404WhenListingExpensesAndPaymentsOfUnknownGroup() throws Exception {
        mockMvc.perform(get("/groups/999999/expenses"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
        mockMvc.perform(get("/groups/999999/payments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldComputeBalancesAndClearThemAfterPayment() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long friendId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);
        addMember(groupId, friendId);
        createSharedExpense(groupId, ownerId, friendId, "10.00");

        mockMvc.perform(get("/groups/%d/balances".formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", ownerId).value(5.0))
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", friendId).value(-5.0))
                .andExpect(jsonPath("$.transfers.length()").value(1))
                .andExpect(jsonPath("$.transfers[0].fromUserId").value(friendId))
                .andExpect(jsonPath("$.transfers[0].toUserId").value(ownerId))
                .andExpect(jsonPath("$.transfers[0].amount").value(5.0));

        createPayment(groupId, friendId, ownerId, "5.00");

        mockMvc.perform(get("/groups/%d/balances".formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", ownerId).value(0.0))
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", friendId).value(0.0))
                .andExpect(jsonPath("$.transfers.length()").value(0));
    }

    @Test
    void shouldReturn404WhenGettingBalancesOfUnknownGroup() throws Exception {
        mockMvc.perform(get("/groups/999999/balances"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldSplitExpenseByExplicitAmountsAndReflectItInBalances() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long friendId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);
        addMember(groupId, friendId);

        mockMvc.perform(post("/groups/%d/expenses".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":%d,"splitType":"AMOUNT",
                                 "shares":[{"userId":%d,"value":3.00},{"userId":%d,"value":7.00}]}
                                """.formatted(ownerId, ownerId, friendId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splits[0].amount").value(3.0))
                .andExpect(jsonPath("$.splits[1].amount").value(7.0));

        mockMvc.perform(get("/groups/%d/balances".formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", ownerId).value(7.0))
                .andExpect(jsonPath("$.balances[?(@.userId==%d)].balance", friendId).value(-7.0));
    }

    @Test
    void shouldSplitExpenseByPercentagesAndRejectWrongTotal() throws Exception {
        long ownerId = registerUser(uniqueEmail());
        long friendId = registerUser(uniqueEmail());
        long groupId = createGroup(ownerId);
        addMember(groupId, friendId);

        mockMvc.perform(post("/groups/%d/expenses".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":%d,"splitType":"PERCENT",
                                 "shares":[{"userId":%d,"value":60},{"userId":%d,"value":40}]}
                                """.formatted(ownerId, ownerId, friendId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splits[0].amount").value(6.0))
                .andExpect(jsonPath("$.splits[1].amount").value(4.0));

        mockMvc.perform(post("/groups/%d/expenses".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":%d,"splitType":"PERCENT",
                                 "shares":[{"userId":%d,"value":60},{"userId":%d,"value":30}]}
                                """.formatted(ownerId, ownerId, friendId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Percentages must add up to 100"));
    }

    private void createSharedExpense(long groupId, long payerId, long otherId, String amount) throws Exception {
        mockMvc.perform(post("/groups/%d/expenses".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%s,"category":"FOOD","paidById":%d,"participantIds":[%d,%d]}
                                """.formatted(amount, payerId, payerId, otherId)))
                .andExpect(status().isCreated());
    }

    private long registerUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long createGroup(long userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":%d}
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private void addMember(long groupId, long userId) throws Exception {
        mockMvc.perform(post("/groups/%d/members".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d}
                                """.formatted(userId)))
                .andExpect(status().isCreated());
    }

    private void createExpense(long groupId, long userId, String amount) throws Exception {
        mockMvc.perform(post("/groups/%d/expenses".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%s,"category":"FOOD","paidById":%d,"participantIds":[%d]}
                                """.formatted(amount, userId, userId)))
                .andExpect(status().isCreated());
    }

    private void createPayment(long groupId, long senderId, long receiverId, String amount) throws Exception {
        mockMvc.perform(post("/groups/%d/payments".formatted(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senderId":%d,"receiverId":%d,"amount":%s}
                                """.formatted(senderId, receiverId, amount)))
                .andExpect(status().isCreated());
    }

    private static String registerBody(String email) {
        return """
                {"firstName":"Jan","lastName":"Kowalski","email":"%s","password":"secret123"}
                """.formatted(email);
    }

    // the database is shared between tests in this class and is not rolled back
    private static String uniqueEmail() {
        return "e2e-" + UUID.randomUUID() + "@example.com";
    }
}
