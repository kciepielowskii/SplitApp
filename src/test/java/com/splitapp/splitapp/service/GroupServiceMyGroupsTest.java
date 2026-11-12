package com.splitapp.splitapp.service;

import com.splitapp.splitapp.dto.GroupSummaryResponse;
import com.splitapp.splitapp.entity.GroupRole;
import com.splitapp.splitapp.repository.GroupRepository;
import com.splitapp.splitapp.repository.GroupSummaryView;
import com.splitapp.splitapp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceMyGroupsTest {
    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccess groupAccess;

    @InjectMocks
    private GroupService groupService;

    @Test
    void shouldMapTheGroupsOfTheCallerInTheOrderTheRepositoryReturnsThem() {
        GroupSummaryView shared = view(2L, "Wspólna", null, GroupRole.MEMBER);
        GroupSummaryView own = view(1L, "Wyjazd", "Weekend w górach", GroupRole.OWNER);
        when(groupRepository.findSummariesByMemberUserId(1L)).thenReturn(List.of(shared, own));

        List<GroupSummaryResponse> result = groupService.getMyGroups(1L);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).id());
        assertEquals(GroupRole.MEMBER, result.get(0).myRole());
        assertEquals("Weekend w górach", result.get(1).groupDescription());
        assertEquals(GroupRole.OWNER, result.get(1).myRole());
    }

    @Test
    void shouldReturnAnEmptyListWhenTheCallerHasNoGroups() {
        when(groupRepository.findSummariesByMemberUserId(1L)).thenReturn(List.of());

        assertTrue(groupService.getMyGroups(1L).isEmpty());
    }

    private static GroupSummaryView view(Long id, String name, String description, GroupRole role) {
        GroupSummaryView view = mock(GroupSummaryView.class);
        when(view.getId()).thenReturn(id);
        when(view.getGroupName()).thenReturn(name);
        when(view.getGroupDescription()).thenReturn(description);
        when(view.getRole()).thenReturn(role);
        return view;
    }
}
