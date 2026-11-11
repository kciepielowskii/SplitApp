package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupAccessTest {
    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupAccess groupAccess;

    @Test
    void shouldAllowGroupMember() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupRepository.existsByIdAndMembersUserId(1L, 2L)).thenReturn(true);

        assertDoesNotThrow(() -> groupAccess.requireMember(1L, 2L));
    }

    @Test
    void shouldThrowNotFoundForUnknownGroup() {
        when(groupRepository.existsById(1L)).thenReturn(false);

        assertThrows(GroupNotFoundException.class, () -> groupAccess.requireMember(1L, 2L));
        verify(groupRepository, never()).existsByIdAndMembersUserId(any(), any());
    }

    @Test
    void shouldThrowForbiddenForNonMember() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupRepository.existsByIdAndMembersUserId(1L, 2L)).thenReturn(false);

        GroupAccessDeniedException e = assertThrows(GroupAccessDeniedException.class,
                () -> groupAccess.requireMember(1L, 2L));

        assertEquals("You are not a member of this group", e.getMessage());
    }
}
