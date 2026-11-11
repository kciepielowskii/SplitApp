package com.splitapp.SplitApp.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentTest {
    private final ExpenseGroup group = mock(ExpenseGroup.class);

    @BeforeEach
    void setUp() {
        when(group.hasMember(any(User.class))).thenReturn(true);
    }

    @Test
    void shouldRejectSameUserInstanceAsSenderAndReceiver() {
        User user = mock(User.class);

        assertThrows(IllegalArgumentException.class,
                () -> new Payment(group, user, user, BigDecimal.TEN, user));
    }

    @Test
    void shouldRejectDifferentInstancesWithSameId() {
        User sender = mock(User.class);
        User receiver = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(receiver.getId()).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> new Payment(group, sender, receiver, BigDecimal.TEN, sender));
    }

    @Test
    void shouldAllowDifferentUsers() {
        User sender = mock(User.class);
        User receiver = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(receiver.getId()).thenReturn(2L);

        assertDoesNotThrow(() -> new Payment(group, sender, receiver, BigDecimal.TEN, sender));
    }

    @Test
    void shouldRejectNullSender() {
        User receiver = mock(User.class);

        assertThrows(NullPointerException.class,
                () -> new Payment(group, null, receiver, BigDecimal.TEN, receiver));
    }

    @Test
    void shouldRejectNullAuthor() {
        User sender = mock(User.class);
        User receiver = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(receiver.getId()).thenReturn(2L);

        assertThrows(NullPointerException.class,
                () -> new Payment(group, sender, receiver, BigDecimal.TEN, null));
    }

    @Test
    void shouldRejectSenderWhoIsNotGroupMember() {
        User sender = mock(User.class);
        User receiver = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(receiver.getId()).thenReturn(2L);
        when(group.hasMember(sender)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Payment(group, sender, receiver, BigDecimal.TEN, sender));

        assertEquals("Sender is not a member of this group", ex.getMessage());
    }

    @Test
    void shouldRejectReceiverWhoIsNotGroupMember() {
        User sender = mock(User.class);
        User receiver = mock(User.class);
        when(sender.getId()).thenReturn(1L);
        when(receiver.getId()).thenReturn(2L);
        when(group.hasMember(receiver)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Payment(group, sender, receiver, BigDecimal.TEN, sender));

        assertEquals("Receiver is not a member of this group", ex.getMessage());
    }
}
