package com.splitapp.splitapp.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupInvitationTest {
    private final User owner = new User("Jan", "Kowalski", "jan@example.com", "hash");
    private final ExpenseGroup group = new ExpenseGroup("Wyjazd", owner);

    @Test
    void shouldStartPendingWithNormalizedEmail() {
        GroupInvitation invitation = new GroupInvitation(group, "  Anna@Example.COM ", owner);

        assertEquals("anna@example.com", invitation.getEmail());
        assertEquals(InvitationStatus.PENDING, invitation.getStatus());
        assertTrue(invitation.isPending());
    }

    @Test
    void shouldBeAccepted() {
        GroupInvitation invitation = new GroupInvitation(group, "anna@example.com", owner);

        invitation.accept();

        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
    }

    @Test
    void shouldBeDeclined() {
        GroupInvitation invitation = new GroupInvitation(group, "anna@example.com", owner);

        invitation.decline();

        assertEquals(InvitationStatus.DECLINED, invitation.getStatus());
    }

    @Test
    void shouldRejectAnsweringTwice() {
        GroupInvitation invitation = new GroupInvitation(group, "anna@example.com", owner);
        invitation.accept();

        assertThrows(IllegalStateException.class, invitation::decline);
        assertThrows(IllegalStateException.class, invitation::accept);
    }
}
