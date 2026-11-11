package com.splitapp.SplitApp.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
}
