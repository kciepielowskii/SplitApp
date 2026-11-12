package com.splitapp.splitapp.service;

import com.splitapp.splitapp.dto.GroupResponse;
import com.splitapp.splitapp.entity.ExpenseGroup;
import com.splitapp.splitapp.entity.GroupRole;
import com.splitapp.splitapp.entity.User;
import com.splitapp.splitapp.exception.GroupAccessDeniedException;
import com.splitapp.splitapp.exception.GroupNotFoundException;
import com.splitapp.splitapp.repository.GroupRepository;
import com.splitapp.splitapp.repository.UserRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceGetGroupTest {
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
    void shouldReturnGroupWithMembers() {
        User owner = new User("Jan", "Kowalski", "jan@example.com", "hash");
        ReflectionTestUtils.setField(owner, "id", 1L);
        ExpenseGroup group = new ExpenseGroup("Wyjazd", owner);
        group.addMember(owner, GroupRole.OWNER);
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupResponse result = groupService.getGroup(GROUP_ID, CURRENT_USER_ID);

        verify(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);
        assertEquals(GROUP_ID, result.id());
        assertEquals("Wyjazd", result.groupName());
        assertEquals(1, result.members().size());
    }

    @Test
    void shouldThrowWhenGroupDoesNotExist() {
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> groupService.getGroup(GROUP_ID, CURRENT_USER_ID));
    }

    @Test
    void shouldNotReturnGroupWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMember(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class, () -> groupService.getGroup(GROUP_ID, 9L));

        verify(groupRepository, never()).findWithMembersById(any());
    }
}
