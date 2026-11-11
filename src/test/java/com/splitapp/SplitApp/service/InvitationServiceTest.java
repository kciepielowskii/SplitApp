package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.dto.InvitationResponse;
import com.splitapp.SplitApp.dto.PendingInvitationResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupInvitation;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.InvitationStatus;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.InvitationNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupInvitationRepository;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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
class InvitationServiceTest {
    private static final Long GROUP_ID = 10L;

    @Mock
    private GroupInvitationRepository invitationRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccess groupAccess;

    @InjectMocks
    private InvitationService invitationService;

    @Test
    void shouldInviteByEmailAndNormalizeIt() {
        User owner = user(1L);
        when(groupRepository.findWithMembersById(GROUP_ID))
                .thenReturn(Optional.of(groupWithOwner(owner)));
        when(invitationRepository.existsByGroupIdAndEmailAndStatus(
                GROUP_ID, "anna@example.com", InvitationStatus.PENDING)).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(owner);
        when(invitationRepository.save(any(GroupInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InvitationResponse result = invitationService.invite(GROUP_ID, 1L, "  Anna@Example.com ");

        verify(groupAccess).requireMember(GROUP_ID, 1L);
        assertEquals(GROUP_ID, result.groupId());
        assertEquals("anna@example.com", result.email());
        assertEquals(1L, result.invitedById());
        assertEquals(InvitationStatus.PENDING, result.status());
    }

    @Test
    void shouldRejectInvitingWhenCallerIsNotOwner() {
        User owner = user(1L);
        User member = user(3L);
        when(groupRepository.findWithMembersById(GROUP_ID))
                .thenReturn(Optional.of(groupWithOwner(owner, member)));

        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> invitationService.invite(GROUP_ID, 3L, "anna@example.com"));

        assertEquals("Only the group owner can invite members", ex.getMessage());
        verifyNoInteractions(userRepository, invitationRepository);
    }

    @Test
    void shouldRejectInvitingExistingMember() {
        User owner = user(1L);
        User member = user(2L);
        when(groupRepository.findWithMembersById(GROUP_ID))
                .thenReturn(Optional.of(groupWithOwner(owner, member)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> invitationService.invite(GROUP_ID, 1L, "JAN2@example.com"));

        assertEquals("User is already a member of this group", ex.getMessage());
        verifyNoInteractions(invitationRepository);
    }

    @Test
    void shouldRejectSecondPendingInvitationForTheSameEmail() {
        when(groupRepository.findWithMembersById(GROUP_ID))
                .thenReturn(Optional.of(groupWithOwner(user(1L))));
        when(invitationRepository.existsByGroupIdAndEmailAndStatus(
                GROUP_ID, "anna@example.com", InvitationStatus.PENDING)).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> invitationService.invite(GROUP_ID, 1L, "anna@example.com"));

        assertEquals("This e-mail already has a pending invitation", ex.getMessage());
        verify(invitationRepository, never()).save(any(GroupInvitation.class));
    }

    @Test
    void shouldThrowWhenInvitingToUnknownGroup() {
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> invitationService.invite(GROUP_ID, 1L, "anna@example.com"));
    }

    @Test
    void shouldNotInviteWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMember(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class,
                () -> invitationService.invite(GROUP_ID, 9L, "anna@example.com"));

        verifyNoInteractions(groupRepository, invitationRepository, userRepository);
    }

    @Test
    void shouldListPendingInvitationsForOwner() {
        User owner = user(1L);
        ExpenseGroup group = groupWithOwner(owner);
        GroupInvitation invitation = new GroupInvitation(group, "anna@example.com", owner);
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));
        when(invitationRepository.findAllByGroupIdAndStatusOrderByIdDesc(
                GROUP_ID, InvitationStatus.PENDING)).thenReturn(List.of(invitation));

        List<InvitationResponse> result = invitationService.getGroupInvitations(GROUP_ID, 1L);

        assertEquals(1, result.size());
        assertEquals("anna@example.com", result.get(0).email());
    }

    @Test
    void shouldNotListInvitationsForNonOwner() {
        User owner = user(1L);
        User member = user(2L);
        when(groupRepository.findWithMembersById(GROUP_ID))
                .thenReturn(Optional.of(groupWithOwner(owner, member)));

        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> invitationService.getGroupInvitations(GROUP_ID, 2L));

        assertEquals("Only the group owner can view invitations", ex.getMessage());
        verifyNoInteractions(invitationRepository);
    }

    @Test
    void shouldCancelPendingInvitation() {
        User owner = user(1L);
        ExpenseGroup group = groupWithOwner(owner);
        GroupInvitation invitation = new GroupInvitation(group, "anna@example.com", owner);
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));
        when(invitationRepository.findByIdAndGroupId(5L, GROUP_ID))
                .thenReturn(Optional.of(invitation));

        invitationService.cancelInvitation(GROUP_ID, 1L, 5L);

        verify(invitationRepository).delete(invitation);
    }

    @Test
    void shouldThrowWhenCancellingUnknownInvitation() {
        when(groupRepository.findWithMembersById(GROUP_ID))
                .thenReturn(Optional.of(groupWithOwner(user(1L))));
        when(invitationRepository.findByIdAndGroupId(99L, GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(InvitationNotFoundException.class,
                () -> invitationService.cancelInvitation(GROUP_ID, 1L, 99L));

        verify(invitationRepository, never()).delete(any(GroupInvitation.class));
    }

    @Test
    void shouldRejectCancellingAnAnsweredInvitation() {
        User owner = user(1L);
        ExpenseGroup group = groupWithOwner(owner);
        GroupInvitation invitation = new GroupInvitation(group, "anna@example.com", owner);
        invitation.decline();
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));
        when(invitationRepository.findByIdAndGroupId(5L, GROUP_ID))
                .thenReturn(Optional.of(invitation));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> invitationService.cancelInvitation(GROUP_ID, 1L, 5L));

        assertEquals("Only a pending invitation can be cancelled", ex.getMessage());
        verify(invitationRepository, never()).delete(any(GroupInvitation.class));
    }

    @Test
    void shouldRejectCancellingWhenCallerIsNotOwner() {
        User owner = user(1L);
        User member = user(2L);
        when(groupRepository.findWithMembersById(GROUP_ID))
                .thenReturn(Optional.of(groupWithOwner(owner, member)));

        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> invitationService.cancelInvitation(GROUP_ID, 2L, 5L));

        assertEquals("Only the group owner can cancel invitations", ex.getMessage());
        verifyNoInteractions(invitationRepository);
    }

    @Test
    void shouldListMyPendingInvitationsWithoutGroupDetails() {
        User owner = user(1L);
        User invitee = user(2L);
        GroupInvitation invitation =
                new GroupInvitation(groupWithOwner(owner), invitee.getEmail(), owner);
        when(userRepository.findById(2L)).thenReturn(Optional.of(invitee));
        when(invitationRepository.findAllByEmailAndStatusOrderByIdDesc(
                "jan2@example.com", InvitationStatus.PENDING)).thenReturn(List.of(invitation));

        List<PendingInvitationResponse> result = invitationService.getMyInvitations(2L);

        assertEquals(1, result.size());
        assertEquals("Wyjazd", result.get(0).groupName());
        assertEquals("Jan", result.get(0).invitedByFirstName());
        assertEquals("Kowalski", result.get(0).invitedByLastName());
    }

    @Test
    void shouldAcceptInvitationAndJoinGroup() {
        User owner = user(1L);
        User invitee = user(2L);
        ExpenseGroup group = groupWithOwner(owner);
        GroupInvitation invitation = new GroupInvitation(group, invitee.getEmail(), owner);
        when(userRepository.findById(2L)).thenReturn(Optional.of(invitee));
        when(invitationRepository.findByIdAndEmailAndStatus(
                5L, "jan2@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(invitation));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupResponse result = invitationService.acceptInvitation(2L, 5L);

        assertEquals(2, result.members().size());
        assertEquals(2L, result.members().get(1).userId());
        assertEquals(GroupRole.MEMBER, result.members().get(1).role());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
    }

    @Test
    void shouldNotDuplicateMembershipWhenInviteeAlreadyJoinedSomehow() {
        User owner = user(1L);
        User invitee = user(2L);
        ExpenseGroup group = groupWithOwner(owner, invitee);
        GroupInvitation invitation = new GroupInvitation(group, invitee.getEmail(), owner);
        when(userRepository.findById(2L)).thenReturn(Optional.of(invitee));
        when(invitationRepository.findByIdAndEmailAndStatus(
                5L, "jan2@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(invitation));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupResponse result = invitationService.acceptInvitation(2L, 5L);

        assertEquals(2, result.members().size());
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
    }

    @Test
    void shouldThrowWhenAcceptingMissingOrForeignInvitation() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(invitationRepository.findByIdAndEmailAndStatus(
                99L, "jan2@example.com", InvitationStatus.PENDING)).thenReturn(Optional.empty());

        assertThrows(InvitationNotFoundException.class,
                () -> invitationService.acceptInvitation(2L, 99L));

        verifyNoInteractions(groupRepository);
    }

    @Test
    void shouldThrowWhenAcceptingAsUnknownUser() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> invitationService.acceptInvitation(9L, 5L));

        verifyNoInteractions(invitationRepository, groupRepository);
    }

    @Test
    void shouldDeclineInvitationWithoutJoiningGroup() {
        User owner = user(1L);
        User invitee = user(2L);
        GroupInvitation invitation =
                new GroupInvitation(groupWithOwner(owner), invitee.getEmail(), owner);
        when(userRepository.findById(2L)).thenReturn(Optional.of(invitee));
        when(invitationRepository.findByIdAndEmailAndStatus(
                5L, "jan2@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(invitation));

        invitationService.declineInvitation(2L, 5L);

        assertEquals(InvitationStatus.DECLINED, invitation.getStatus());
        verifyNoInteractions(groupRepository);
    }

    @Test
    void shouldThrowWhenDecliningMissingOrForeignInvitation() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(invitationRepository.findByIdAndEmailAndStatus(
                99L, "jan2@example.com", InvitationStatus.PENDING)).thenReturn(Optional.empty());

        assertThrows(InvitationNotFoundException.class,
                () -> invitationService.declineInvitation(2L, 99L));
    }

    private static User user(Long id) {
        User user = new User("Jan", "Kowalski", "jan" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static ExpenseGroup groupWithOwner(User owner, User... others) {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", owner);
        group.addMember(owner, GroupRole.OWNER);
        for (User other : others) {
            group.addMember(other, GroupRole.MEMBER);
        }
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        return group;
    }
}
