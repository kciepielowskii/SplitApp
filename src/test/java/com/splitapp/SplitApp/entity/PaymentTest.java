package com.splitapp.SplitApp.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentTest {
    private final ExpenseGroup group = mock(ExpenseGroup.class);

    @Test
    void shouldRejectSameUserInstanceAsSenderAndReceiver() {
        User user = mock(User.class);

        assertThrows(IllegalArgumentException.class,
                () -> new Payment(group, user, user, BigDecimal.TEN));
    }

    @Test
    void shouldRejectDifferentInstancesWithSameId() {
        User sender = mock(User.class);
        User receiver = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(receiver.getId()).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> new Payment(group, sender, receiver, BigDecimal.TEN));
    }

    @Test
    void shouldAllowDifferentUsers() {
        User sender = mock(User.class);
        User receiver = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(receiver.getId()).thenReturn(2L);

        assertDoesNotThrow(() -> new Payment(group, sender, receiver, BigDecimal.TEN));
    }

    @Test
    void shouldRejectNullSender() {
        User receiver = mock(User.class);

        assertThrows(NullPointerException.class,
                () -> new Payment(group, null, receiver, BigDecimal.TEN));
    }
}
