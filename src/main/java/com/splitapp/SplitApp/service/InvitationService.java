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
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class InvitationService {
    private final GroupInvitationRepository invitationRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupAccess groupAccess;

    /**
     * Succeeds whether or not an account exists for the e-mail, so the endpoint cannot be used
     * to find out who is registered.
     */
    @Transactional
    public InvitationResponse invite(@NotNull Long groupId, @NotNull Long currentUserId,
                                     @NotNull String email) {
        ExpenseGroup group = loadGroupAsOwner(groupId, currentUserId,
                "Only the group owner can invite members");
        String normalized = User.normalizeEmail(email);

        boolean alreadyMember = group.getMembers().stream()
                .anyMatch(member -> member.getUser().getEmail().equals(normalized));
        if (alreadyMember) {
            throw new BusinessRuleException("User is already a member of this group");
        }
        if (invitationRepository.existsByGroupIdAndEmailAndStatus(
                groupId, normalized, InvitationStatus.PENDING)) {
            throw new BusinessRuleException("This e-mail already has a pending invitation");
        }

        User inviter = userRepository.getReferenceById(currentUserId);
        GroupInvitation invitation = new GroupInvitation(group, normalized, inviter);
        return InvitationResponse.from(invitationRepository.save(invitation));
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> getGroupInvitations(@NotNull Long groupId,
                                                        @NotNull Long currentUserId) {
        loadGroupAsOwner(groupId, currentUserId, "Only the group owner can view invitations");
        return invitationRepository
                .findAllByGroupIdAndStatusOrderByIdDesc(groupId, InvitationStatus.PENDING).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    @Transactional
    public void cancelInvitation(@NotNull Long groupId, @NotNull Long currentUserId,
                                 @NotNull Long invitationId) {
        loadGroupAsOwner(groupId, currentUserId, "Only the group owner can cancel invitations");
        GroupInvitation invitation = invitationRepository.findByIdAndGroupId(invitationId, groupId)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));
        if (!invitation.isPending()) {
            throw new BusinessRuleException("Only a pending invitation can be cancelled");
        }
        invitationRepository.delete(invitation);
    }

    @Transactional(readOnly = true)
    public List<PendingInvitationResponse> getMyInvitations(@NotNull Long currentUserId) {
        User user = loadUser(currentUserId);
        return invitationRepository
                .findAllByEmailAndStatusOrderByIdDesc(user.getEmail(), InvitationStatus.PENDING)
                .stream()
                .map(PendingInvitationResponse::from)
                .toList();
    }

    @Transactional
    public GroupResponse acceptInvitation(@NotNull Long currentUserId, @NotNull Long invitationId) {
        User user = loadUser(currentUserId);
        GroupInvitation invitation = findMyPendingInvitation(user, invitationId);
        ExpenseGroup group = groupRepository.findWithMembersById(invitation.getGroup().getId())
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        if (!group.hasMember(user)) {
            group.addMember(user, GroupRole.MEMBER);
        }
        invitation.accept();
        return GroupResponse.from(group);
    }

    @Transactional
    public void declineInvitation(@NotNull Long currentUserId, @NotNull Long invitationId) {
        User user = loadUser(currentUserId);
        findMyPendingInvitation(user, invitationId).decline();
    }

    private ExpenseGroup loadGroupAsOwner(Long groupId, Long currentUserId, String denyMessage) {
        groupAccess.requireMember(groupId, currentUserId);
        ExpenseGroup group = groupRepository.findWithMembersById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        if (!group.isOwner(currentUserId)) {
            throw new GroupAccessDeniedException(denyMessage);
        }
        return group;
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    // Someone else's, already answered and non-existent invitations all look the same: 404.
    private GroupInvitation findMyPendingInvitation(User user, Long invitationId) {
        return invitationRepository
                .findByIdAndEmailAndStatus(invitationId, user.getEmail(), InvitationStatus.PENDING)
                .orElseThrow(() -> new InvitationNotFoundException("Invitation not found"));
    }
}
