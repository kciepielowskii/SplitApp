package com.splitapp.splitapp.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ExpenseSplitTest {
    private final User user = mock(User.class);

    @Test
    void shouldRejectNullUser() {
        assertThrows(NullPointerException.class,
                () -> new ExpenseSplit(null, BigDecimal.TEN));
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(NullPointerException.class,
                () -> new ExpenseSplit(user, null));
    }
}
