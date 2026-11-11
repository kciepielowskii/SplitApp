package com.splitapp.SplitApp.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ExpenseGroupTest {
    private final User creator = mock(User.class);

    @Test
    void shouldRejectNullGroupName() {
        assertThrows(NullPointerException.class, () -> new ExpenseGroup(null, creator));
    }

    @Test
    void shouldRejectNullCreator() {
        assertThrows(NullPointerException.class, () -> new ExpenseGroup("Wyjazd", null));
    }

    @Test
    void shouldAllowNullDescription() {
        assertDoesNotThrow(() -> new ExpenseGroup("Wyjazd", null, creator));
    }
}
