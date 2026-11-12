package com.splitapp.splitapp.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class GroupMemberTest {
    private final ExpenseGroup group = mock(ExpenseGroup.class);
    private final User user = mock(User.class);

    @Test
    void shouldRejectNullGroup() {
        assertThrows(NullPointerException.class,
                () -> new GroupMember(null, user, GroupRole.MEMBER));
    }

    @Test
    void shouldRejectNullUser() {
        assertThrows(NullPointerException.class,
                () -> new GroupMember(group, null, GroupRole.MEMBER));
    }

    @Test
    void shouldRejectNullRole() {
        assertThrows(NullPointerException.class,
                () -> new GroupMember(group, user, null));
    }

    @Test
    void shouldChangeRole() {
        GroupMember member = new GroupMember(group, user, GroupRole.MEMBER);

        member.changeRole(GroupRole.OWNER);

        assertEquals(GroupRole.OWNER, member.getRole());
    }

    @Test
    void shouldRejectNullRoleWhenChangingRole() {
        GroupMember member = new GroupMember(group, user, GroupRole.MEMBER);

        assertThrows(NullPointerException.class, () -> member.changeRole(null));
        assertEquals(GroupRole.MEMBER, member.getRole());
    }
}
