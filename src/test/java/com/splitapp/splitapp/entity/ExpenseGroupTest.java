package com.splitapp.splitapp.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private User user(Long id) {
        User user = new User("Jan", "Kowalski", "jan" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ExpenseGroup groupWithOwnerAndMember() {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", creator);
        group.addMember(user(1L), GroupRole.OWNER);
        group.addMember(user(2L), GroupRole.MEMBER);
        return group;
    }

    @Test
    void shouldAddMember() {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", creator);

        GroupMember member = group.addMember(user(1L), GroupRole.OWNER);

        assertNotNull(member);
        assertEquals(1, group.getMembers().size());
    }

    @Test
    void shouldAllowDifferentMembers() {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", creator);

        group.addMember(user(1L), GroupRole.OWNER);
        group.addMember(user(2L), GroupRole.MEMBER);

        assertEquals(2, group.getMembers().size());
    }

    @Test
    void shouldRejectDuplicateMemberWithSameId() {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", creator);
        group.addMember(user(1L), GroupRole.OWNER);

        assertThrows(IllegalArgumentException.class,
                () -> group.addMember(user(1L), GroupRole.MEMBER));
        assertEquals(1, group.getMembers().size());
    }

    @Test
    void shouldNotAllowModifyingMembersList() {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", creator);
        GroupMember member = group.addMember(user(1L), GroupRole.OWNER);

        assertThrows(UnsupportedOperationException.class,
                () -> group.getMembers().add(member));
    }

    @Test
    void shouldRecognizeOwner() {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", creator);
        group.addMember(user(1L), GroupRole.OWNER);
        group.addMember(user(2L), GroupRole.MEMBER);

        assertTrue(group.isOwner(1L));
    }

    @Test
    void shouldNotTreatRegularMemberOrOutsiderAsOwner() {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", creator);
        group.addMember(user(1L), GroupRole.OWNER);
        group.addMember(user(2L), GroupRole.MEMBER);

        assertFalse(group.isOwner(2L));
        assertFalse(group.isOwner(3L));
    }

    @Test
    void shouldRecognizeMembersById() {
        ExpenseGroup group = groupWithOwnerAndMember();

        assertTrue(group.isMember(1L));
        assertTrue(group.isMember(2L));
        assertFalse(group.isMember(3L));
    }

    @Test
    void shouldRemoveMember() {
        ExpenseGroup group = groupWithOwnerAndMember();

        group.removeMember(2L);

        assertEquals(1, group.getMembers().size());
        assertFalse(group.isMember(2L));
        assertTrue(group.isMember(1L));
    }

    @Test
    void shouldRejectRemovingSomeoneWhoIsNotMember() {
        ExpenseGroup group = groupWithOwnerAndMember();

        assertThrows(IllegalArgumentException.class, () -> group.removeMember(3L));
        assertEquals(2, group.getMembers().size());
    }

    @Test
    void shouldTransferOwnership() {
        ExpenseGroup group = groupWithOwnerAndMember();

        group.transferOwnership(1L, 2L, () -> { });

        assertFalse(group.isOwner(1L));
        assertTrue(group.isOwner(2L));
        assertEquals(GroupRole.MEMBER, group.getMembers().get(0).getRole());
        assertEquals(GroupRole.OWNER, group.getMembers().get(1).getRole());
    }

    @Test
    void shouldDemoteTheOldOwnerBeforeThePromotionStarts() {
        ExpenseGroup group = groupWithOwnerAndMember();

        group.transferOwnership(1L, 2L, () -> {
            assertFalse(group.isOwner(1L));
            assertFalse(group.isOwner(2L));
        });

        assertTrue(group.isOwner(2L));
    }

    @Test
    void shouldRejectTransferringOwnershipToNonMember() {
        ExpenseGroup group = groupWithOwnerAndMember();

        assertThrows(IllegalArgumentException.class, () ->
                group.transferOwnership(1L, 3L, () -> { }));
        assertTrue(group.isOwner(1L));
    }

    @Test
    void shouldRejectTransferringOwnershipToTheSameUser() {
        ExpenseGroup group = groupWithOwnerAndMember();

        assertThrows(IllegalArgumentException.class, () ->
                        group.transferOwnership(1L, 1L, () -> { }));
        assertTrue(group.isOwner(1L));
    }

    @Test
    void shouldRejectTransferringOwnershipByNonOwner() {
        ExpenseGroup group = groupWithOwnerAndMember();

        assertThrows(IllegalArgumentException.class, () ->
                        group.transferOwnership(2L, 1L, () -> { }));
        assertTrue(group.isOwner(1L));
        assertFalse(group.isOwner(2L));
    }
}
