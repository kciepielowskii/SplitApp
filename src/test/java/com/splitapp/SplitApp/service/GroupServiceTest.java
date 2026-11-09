package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.entity.Group;
import com.splitapp.SplitApp.entity.GroupMember;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupMemberRepository;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;

import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GroupServiceTest {
    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    void shouldCreateGroupSuccessfully() throws Exception {
        User user = createUser();
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(groupRepository.save(any(Group.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Group result = groupService.createGroup("Wyjazd", 1L);

        ArgumentCaptor<GroupMember> captor =
                ArgumentCaptor.forClass(GroupMember.class);

        verify(groupMemberRepository).save(captor.capture());

        GroupMember savedMember = captor.getValue();

        assertNotNull(result);
        assertEquals("Wyjazd", result.getGroupName());

        verify(groupRepository).save(any(Group.class));

        assertEquals(GroupRole.OWNER, savedMember.getRole());
        assertEquals(user, savedMember.getUser());
    }

    private User createUser() throws Exception {
        Constructor<User> constructor = User.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> {
            groupService.createGroup("Wyjazd", 1L);
        });
        verify(groupRepository, never()).save(any(Group.class));
        verify(groupMemberRepository, never()).save(any(GroupMember.class));
    }

    @Test
    void shouldAssignSavedGroupToGroupMember() throws Exception {
        User user = createUser();
        Group savedGroup = new Group("Wyjazd", user);
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(groupRepository.save(any(Group.class)))
                .thenReturn(savedGroup);

        groupService.createGroup("Wyjazd", 1L);
        ArgumentCaptor<GroupMember> captor =
                ArgumentCaptor.forClass(GroupMember.class);

        verify(groupMemberRepository).save(captor.capture());
        assertEquals(savedGroup, captor.getValue().getGroup());
    }
}
