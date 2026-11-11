package com.splitapp.SplitApp.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private User user(Long id) {
        User user = new User("Jan", "Kowalski", "jan" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
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
}
