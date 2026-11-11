package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.TestcontainersConfiguration;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseCategory;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.ExpenseSplit;
import com.splitapp.SplitApp.entity.GroupMember;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    private EntityManager entityManager;

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

        assertThrows(DataIntegrityViolationException.class,
                () -> saveUser("same@example.com"));
    }

    @Test
    void shouldRejectSameUserTwiceInOneGroup() {
        User user = saveUser("member@example.com");
        ExpenseGroup group = saveGroup(user);
        groupMemberRepository.saveAndFlush(new GroupMember(group, user, GroupRole.OWNER));

        assertThrows(DataIntegrityViolationException.class,
                () -> groupMemberRepository.saveAndFlush(
                        new GroupMember(group, user, GroupRole.MEMBER)));
    }

    @Test
    void shouldRejectTwoSplitsForSameUserInOneExpense() {
        User user = saveUser("payer@example.com");
        ExpenseGroup group = saveGroup(user);
        Expense expense = new Expense(TEN, category, "Obiad", group, user);
        expense.addSplit(new ExpenseSplit(user, new BigDecimal("5.00")));
        expenseRepository.saveAndFlush(expense);

        ExpenseSplit duplicate = new ExpenseSplit(user, new BigDecimal("5.00"));
        ReflectionTestUtils.setField(duplicate, "expense", expense);

        assertThrows(DataIntegrityViolationException.class,
                () -> expenseSplitRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldRejectNonPositiveExpenseAmount() {
        User user = saveUser("payer@example.com");
        ExpenseGroup group = saveGroup(user);

        assertThrows(DataIntegrityViolationException.class,
                () -> expenseRepository.saveAndFlush(
                        new Expense(BigDecimal.ZERO, category, "Obiad", group, user)));
    }

    @Test
    void shouldRejectNonPositiveSplitAmount() {
        User user = saveUser("payer@example.com");
        ExpenseGroup group = saveGroup(user);
        Expense expense = new Expense(TEN, category, "Obiad", group, user);
        expense.addSplit(new ExpenseSplit(user, BigDecimal.ZERO));

        assertThrows(DataIntegrityViolationException.class,
                () -> expenseRepository.saveAndFlush(expense));
    }

    @Test
    void shouldRejectNonPositivePaymentAmount() {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        ExpenseGroup group = saveGroup(sender);

        assertThrows(DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(
                        new Payment(group, sender, receiver, BigDecimal.ZERO)));
    }

    @Test
    void shouldRejectPaymentWhereSenderEqualsReceiver() {
        User sender = saveUser("sender@example.com");
        User receiver = saveUser("receiver@example.com");
        ExpenseGroup group = saveGroup(sender);
        Payment payment = new Payment(group, sender, receiver, TEN);
        // the constructor refuses sender == receiver, so bypass it to test the DB check
        ReflectionTestUtils.setField(payment, "receiver", sender);

        assertThrows(DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(payment));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(new User("Jan", "Kowalski", email, "hash"));
    }

    private ExpenseGroup saveGroup(User owner) {
        return groupRepository.saveAndFlush(new ExpenseGroup("Wyjazd", owner));
    }
}
