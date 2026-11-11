package com.splitapp.SplitApp.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ExpenseTest {
    private final ExpenseGroup group = mock(ExpenseGroup.class);
    private final User paidBy = mock(User.class);
    private final ExpenseCategory category = ExpenseCategory.values()[0];

    @Test
    void shouldRejectNullAmount() {
        assertThrows(NullPointerException.class,
                () -> new Expense(null, category, "Obiad", group, paidBy));
    }

    @Test
    void shouldRejectNullCategory() {
        assertThrows(NullPointerException.class,
                () -> new Expense(BigDecimal.TEN, null, "Obiad", group, paidBy));
    }

    @Test
    void shouldRejectNullGroup() {
        assertThrows(NullPointerException.class,
                () -> new Expense(BigDecimal.TEN, category, "Obiad", null, paidBy));
    }

    @Test
    void shouldRejectNullPayer() {
        assertThrows(NullPointerException.class,
                () -> new Expense(BigDecimal.TEN, category, "Obiad", group, null));
    }

    @Test
    void shouldAllowNullDescription() {
        assertDoesNotThrow(() -> new Expense(BigDecimal.TEN, category, null, group, paidBy));
    }

    private User user(Long id) {
        User user = new User("Jan", "Kowalski", "jan" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Expense expense() {
        return new Expense(BigDecimal.TEN, category, "Obiad", group, paidBy);
    }

    @Test
    void shouldAddSplitsForDifferentUsers() {
        Expense expense = expense();

        expense.addSplit(new ExpenseSplit(user(1L), BigDecimal.valueOf(5)));
        expense.addSplit(new ExpenseSplit(user(2L), BigDecimal.valueOf(5)));

        assertEquals(2, expense.getSplits().size());
    }

    @Test
    void shouldRejectDuplicateSplitForSameUser() {
        Expense expense = expense();
        expense.addSplit(new ExpenseSplit(user(1L), BigDecimal.valueOf(5)));

        assertThrows(IllegalArgumentException.class,
                () -> expense.addSplit(new ExpenseSplit(user(1L), BigDecimal.valueOf(5))));
        assertEquals(1, expense.getSplits().size());
    }

    @Test
    void shouldNotAllowModifyingSplitsList() {
        Expense expense = expense();
        ExpenseSplit split = new ExpenseSplit(user(1L), BigDecimal.valueOf(5));
        expense.addSplit(split);

        assertThrows(UnsupportedOperationException.class,
                () -> expense.getSplits().add(split));
    }
}
