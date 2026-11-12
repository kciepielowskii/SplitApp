package com.splitapp.splitapp.repository;

import com.splitapp.splitapp.TestcontainersConfiguration;
import com.splitapp.splitapp.entity.Expense;
import com.splitapp.splitapp.entity.ExpenseCategory;
import com.splitapp.splitapp.entity.ExpenseGroup;
import com.splitapp.splitapp.entity.ExpenseSplit;
import com.splitapp.splitapp.entity.GroupInvitation;
import com.splitapp.splitapp.entity.GroupMember;
import com.splitapp.splitapp.entity.GroupRole;
import com.splitapp.splitapp.entity.Payment;
import com.splitapp.splitapp.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.properties.jakarta.persistence.validation.mode=none")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class DatabaseConstraintsTest {
    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private GroupInvitationRepository invitationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExpenseCategory category = ExpenseCategory.values()[0];

    @Test
    void shouldPersistGroupMembersThroughCascade() {
        User owner = saveUser("owner@example.com");
        ExpenseGroup group = new ExpenseGroup("Wyjazd", owner);
        group.addMember(owner, GroupRole.OWNER);
        groupRepository.saveAndFlush(group);
        entityManager.clear();

        ExpenseGroup loaded = groupRepository.findById(group.getId()).orElseThrow();

        assertEquals(1, loaded.getMembers().size());
    }

    @Test
    void shouldRejectDuplicateEmail() {
        saveUser("same@example.com");

        assertViolates("users_email_key", () -> saveUser("same@example.com"));
    }

    @Test
    void shouldRejectSameUserTwiceInOneGroup() {
        User user = saveUser("member@example.com");
        ExpenseGroup group = saveGroup(user);

        assertViolates("uk_group_member",
                () -> groupMemberRepository.saveAndFlush(
                        new GroupMember(group, user, GroupRole.MEMBER)));
    }

    @Test
    void shouldRejectASecondOwnerInOneGroup() {
        User owner = saveUser("owner@example.com");
        User other = saveUser("other@example.com");
        ExpenseGroup group = saveGroup(owner);

        assertViolates("uq_group_members_single_owner",
                () -> groupMemberRepository.saveAndFlush(
                        new GroupMember(group, other, GroupRole.OWNER)));
    }

    @Test
    void shouldAllowAnOwnerInEveryGroupAndManyRegularMembers() {
        User firstOwner = saveUser("owner1@example.com");
        User secondOwner = saveUser("owner2@example.com");
        User third = saveUser("third@example.com");
        User fourth = saveUser("fourth@example.com");
        ExpenseGroup first = saveGroup(firstOwner);
        ExpenseGroup second = saveGroup(secondOwner);
        first.addMember(third, GroupRole.MEMBER);
        first.addMember(fourth, GroupRole.MEMBER);
        groupRepository.saveAndFlush(first);

        Integer owners = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM group_members WHERE role = 'OWNER' AND group_id IN (?, ?)",
                Integer.class, first.getId(), second.getId());
        Integer firstGroupRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM group_members WHERE group_id = ?",
                Integer.class, first.getId());
        assertEquals(2, owners);
        assertEquals(3, firstGroupRows);
    }

    @Test
    void shouldTransferOwnershipWithoutEverHavingTwoOwners() {
        User owner = saveUser("owner@example.com");
        User successor = saveUser("successor@example.com");
        ExpenseGroup group = saveGroup(owner);
        group.addMember(successor, GroupRole.MEMBER);
        groupRepository.saveAndFlush(group);
        entityManager.clear();

        ExpenseGroup loaded = groupRepository.findWithMembersById(group.getId()).orElseThrow();
        loaded.transferOwnership(owner.getId(), successor.getId(), groupRepository::flush);
        groupRepository.flush();
        entityManager.clear();

        assertEquals("MEMBER", roleOf(group, owner));
        assertEquals("OWNER", roleOf(group, successor));
    }

    @Test
    void shouldRejectTwoSplitsForSameUserInOneExpense() {
        User user = saveUser("payer@example.com");
        ExpenseGroup group = saveGroup(user);
        Expense expense = new Expense(TEN, category, "Obiad", group, user, user);
        expense.addSplit(new ExpenseSplit(user, new BigDecimal("5.00")));
        expenseRepository.saveAndFlush(expense);

        ExpenseSplit duplicate = new ExpenseSplit(user, new BigDecimal("5.00"));
        ReflectionTestUtils.setField(duplicate, "expense", expense);

        assertViolates("uk_expense_split_user",
                () -> expenseSplitRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldRejectNonPositiveExpenseAmount() {
        User user = saveUser("payer@example.com");
        ExpenseGroup group = saveGroup(user);

        assertViolates("chk_expense_amount",
                () -> expenseRepository.saveAndFlush(
                        new Expense(BigDecimal.ZERO, category, "Obiad", group, user, user)));
    }

    @Test
    void shouldRejectNonPositiveSplitAmount() {
        User user = saveUser("payer@example.com");
        ExpenseGroup group = saveGroup(user);
        Expense expense = new Expense(TEN, category, "Obiad", group, user, user);
        expense.addSplit(new ExpenseSplit(user, BigDecimal.ZERO));

        assertViolates("chk_split_amount",
                () -> expenseRepository.saveAndFlush(expense));
    }

    @Test
    void shouldRejectNonPositivePaymentAmount() {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        ExpenseGroup group = saveGroup(sender);
        group.addMember(receiver, GroupRole.MEMBER);

        assertViolates("chk_payment_amount",
                () -> paymentRepository.saveAndFlush(
                        new Payment(group, sender, receiver, BigDecimal.ZERO, sender)));
    }

    @Test
    void shouldRejectPaymentWhereSenderEqualsReceiver() {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        ExpenseGroup group = saveGroup(sender);
        group.addMember(receiver, GroupRole.MEMBER);
        Payment payment = new Payment(group, sender, receiver, TEN, sender);
        // the constructor refuses sender == receiver, so bypass it to test the DB check
        ReflectionTestUtils.setField(payment, "receiver", sender);

        assertViolates("chk_payment_sender_receiver",
                () -> paymentRepository.saveAndFlush(payment));
    }

    @Test
    void shouldRejectUnknownGroupRole() {
        User user = saveUser("member@example.com");
        ExpenseGroup group = saveGroup(user);

        assertViolates("ck_group_members_role",
                () -> jdbcTemplate.update(
                        "UPDATE group_members SET role = 'ADMIN' WHERE group_id = ? AND user_id = ?",
                        group.getId(), user.getId()));
    }

    @Test
    void shouldRejectUnknownExpenseCategory() {
        User user = saveUser("payer@example.com");
        ExpenseGroup group = saveGroup(user);
        Expense expense = expenseRepository.saveAndFlush(
                new Expense(TEN, category, "Obiad", group, user, user));

        assertViolates("ck_expenses_category",
                () -> jdbcTemplate.update(
                        "UPDATE expenses SET category = 'GAMBLING' WHERE id = ?",
                        expense.getId()));
    }

    @Test
    void shouldRejectUnknownPaymentStatus() {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        ExpenseGroup group = saveGroup(sender);
        group.addMember(receiver, GroupRole.MEMBER);
        groupRepository.saveAndFlush(group);
        Payment payment = paymentRepository.saveAndFlush(
                new Payment(group, sender, receiver, TEN, sender));

        assertViolates("chk_payments_status",
                () -> jdbcTemplate.update(
                        "UPDATE payments SET status = 'REFUNDED' WHERE id = ?",
                        payment.getId()));
    }

    @Test
    void shouldRejectUnknownInvitationStatus() {
        User owner = saveUser("owner@example.com");
        ExpenseGroup group = saveGroup(owner);
        GroupInvitation invitation = invitationRepository.saveAndFlush(
                new GroupInvitation(group, "friend@example.com", owner));

        assertViolates("chk_group_invitations_status",
                () -> jdbcTemplate.update(
                        "UPDATE group_invitations SET status = 'CANCELLED' WHERE id = ?",
                        invitation.getId()));
    }

    @Test
    void shouldRejectSecondPendingInvitationForSameGroupAndEmail() {
        User owner = saveUser("owner@example.com");
        ExpenseGroup group = saveGroup(owner);
        invitationRepository.saveAndFlush(
                new GroupInvitation(group, "friend@example.com", owner));

        // the constructor normalizes the address, so the upper-case variant is the same e-mail
        assertViolates("uq_group_invitations_pending",
                () -> invitationRepository.saveAndFlush(
                        new GroupInvitation(group, "FRIEND@example.com", owner)));
    }

    @Test
    void shouldAllowNewPendingInvitationAfterTheOldOneWasAnsweredOrDeclined() {
        User owner = saveUser("owner@example.com");
        ExpenseGroup group = saveGroup(owner);

        GroupInvitation declined = invitationRepository.saveAndFlush(
                new GroupInvitation(group, "friend@example.com", owner));
        declined.decline();
        invitationRepository.saveAndFlush(declined);

        GroupInvitation accepted = invitationRepository.saveAndFlush(
                new GroupInvitation(group, "friend@example.com", owner));
        accepted.accept();
        invitationRepository.saveAndFlush(accepted);

        invitationRepository.saveAndFlush(
                new GroupInvitation(group, "friend@example.com", owner));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM group_invitations WHERE group_id = ? AND email = ?",
                Integer.class, group.getId(), "friend@example.com");
        assertEquals(3, rows);
    }

    @Test
    void shouldAllowPendingInvitationsForTheSameEmailInDifferentGroups() {
        User firstOwner = saveUser("owner1@example.com");
        User secondOwner = saveUser("owner2@example.com");
        ExpenseGroup first = saveGroup(firstOwner);
        ExpenseGroup second = saveGroup(secondOwner);

        invitationRepository.saveAndFlush(
                new GroupInvitation(first, "friend@example.com", firstOwner));
        invitationRepository.saveAndFlush(
                new GroupInvitation(second, "friend@example.com", secondOwner));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM group_invitations WHERE email = ? "
                        + "AND group_id IN (?, ?)",
                Integer.class, "friend@example.com", first.getId(), second.getId());
        assertEquals(2, rows);
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(new User("Jan", "Kowalski", email, "hash"));
    }

    private ExpenseGroup saveGroup(User owner) {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", owner);
        group.addMember(owner, GroupRole.OWNER);
        return groupRepository.saveAndFlush(group);
    }

    private String roleOf(ExpenseGroup group, User user) {
        return jdbcTemplate.queryForObject(
                "SELECT role FROM group_members WHERE group_id = ? AND user_id = ?",
                String.class, group.getId(), user.getId());
    }

    private static void assertViolates(String constraint, Executable action) {
        DataIntegrityViolationException ex =
                assertThrows(DataIntegrityViolationException.class, action);
        String cause = ex.getMostSpecificCause().getMessage();
        assertTrue(cause.contains(constraint),
                () -> "Expected a violation of " + constraint + " but got: " + cause);
    }
}
