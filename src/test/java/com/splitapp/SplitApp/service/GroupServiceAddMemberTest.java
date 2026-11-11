package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupMemberResponse;
import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceAddMemberTest {
    private static final Long GROUP_ID = 10L;
    private static final Long CURRENT_USER_ID = 1L;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccess groupAccess;

    @InjectMocks
    private GroupService groupService;

    @Test
    void shouldAddUserAsMember() {
        User owner = user(1L);
        User newcomer = user(2L);
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group(owner)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newcomer));

        GroupResponse result = groupService.addMember(GROUP_ID, CURRENT_USER_ID, 2L);

        verify(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);
        assertEquals(2, result.members().size());
        GroupMemberResponse added = result.members().get(1);
        assertEquals(2L, added.userId());
        assertEquals(GroupRole.MEMBER, added.role());
    }

    @Test
    void shouldThrowWhenGroupDoesNotExist() {
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> groupService.addMember(GROUP_ID, CURRENT_USER_ID, 2L));
    }

    @Test
    void shouldThrowWhenUserDoesNotExist() {
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group(user(1L))));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> groupService.addMember(GROUP_ID, CURRENT_USER_ID, 2L));
    }

    @Test
    void shouldRejectUserWhoIsAlreadyMember() {
        User owner = user(1L);
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group(owner)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> groupService.addMember(GROUP_ID, CURRENT_USER_ID, 1L));

        assertEquals("User is already a member of this group", ex.getMessage());
    }

    @Test
    void shouldNotAddMemberWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMember(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class,
                () -> groupService.addMember(GROUP_ID, 9L, 2L));

        verifyNoInteractions(userRepository);
        verify(groupRepository, never()).findWithMembersById(GROUP_ID);
    }

    private static User user(Long id) {
        User user = new User("Jan", "Kowalski", "jan" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void shouldRejectAddingMemberWhenCallerIsNotOwner() {
        User owner = user(1L);
        ExpenseGroup group = group(owner);
        group.addMember(user(3L), GroupRole.MEMBER);
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> groupService.addMember(GROUP_ID, 3L, 2L));

        assertEquals("Only the group owner can add members", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    private static ExpenseGroup group(User owner) {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", owner);
        group.addMember(owner, GroupRole.OWNER);
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        return group;
    }
}
