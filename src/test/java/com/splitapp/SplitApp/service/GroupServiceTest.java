package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.entity.Group;
import com.splitapp.SplitApp.entity.GroupMember;
import com.splitapp.SplitApp.entity.User;
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

        assertNotNull(result);
        assertEquals("Wyjazd", result.getGroupName());

        verify(groupRepository).save(any(Group.class));
        verify(groupMemberRepository).save(any(GroupMember.class));
    }

    private User createUser() throws Exception {
        Constructor<User> constructor = User.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
