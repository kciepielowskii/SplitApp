package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    void shouldLockTheGroupForAMember() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupRepository.existsByIdAndMembersUserId(1L, 2L)).thenReturn(true);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(aGroup()));

        assertDoesNotThrow(() -> groupAccess.requireMemberAndLock(1L, 2L));

        verify(groupRepository).findByIdForUpdate(1L);
    }

    @Test
    void shouldNotLockTheGroupForANonMember() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupRepository.existsByIdAndMembersUserId(1L, 2L)).thenReturn(false);

        assertThrows(GroupAccessDeniedException.class,
                () -> groupAccess.requireMemberAndLock(1L, 2L));

        verify(groupRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldNotLockAnUnknownGroup() {
        when(groupRepository.existsById(1L)).thenReturn(false);

        assertThrows(GroupNotFoundException.class,
                () -> groupAccess.requireMemberAndLock(1L, 2L));

        verify(groupRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldRejectACallerWhoLeftWhileWaitingForTheLock() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        // a member when the request arrived, no longer one once the lock was granted
        when(groupRepository.existsByIdAndMembersUserId(1L, 2L)).thenReturn(true, false);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(aGroup()));

        GroupAccessDeniedException e = assertThrows(GroupAccessDeniedException.class,
                () -> groupAccess.requireMemberAndLock(1L, 2L));

        assertEquals("You are not a member of this group", e.getMessage());
        verify(groupRepository, times(2)).existsByIdAndMembersUserId(1L, 2L);
    }

    @Test
    void shouldThrowNotFoundWhenTheGroupDisappearsBeforeItIsLocked() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupRepository.existsByIdAndMembersUserId(1L, 2L)).thenReturn(true);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> groupAccess.requireMemberAndLock(1L, 2L));
    }

    private static ExpenseGroup aGroup() {
        return new ExpenseGroup("Wyjazd", new User("Jan", "Kowalski", "jan@example.com", "hash"));
    }
}
