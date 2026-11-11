package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceGetGroupTest {
    private static final Long GROUP_ID = 10L;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

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

        GroupResponse result = groupService.getGroup(GROUP_ID);

        assertEquals(GROUP_ID, result.id());
        assertEquals("Wyjazd", result.groupName());
        assertEquals(1, result.members().size());
    }

    @Test
    void shouldThrowWhenGroupDoesNotExist() {
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class, () -> groupService.getGroup(GROUP_ID));
    }
}
