package com.splitapp.splitapp.service;

import com.splitapp.splitapp.exception.GroupAccessDeniedException;
import com.splitapp.splitapp.exception.GroupNotFoundException;
import com.splitapp.splitapp.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupAccess {
    private final GroupRepository groupRepository;

    /** 404 when the group does not exist, 403 when the user is not one of its members. */
    public void requireMember(Long groupId, Long userId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException("Group not found");
        }
        if (!groupRepository.existsByIdAndMembersUserId(groupId, userId)) {
            throw new GroupAccessDeniedException("You are not a member of this group");
        }
    }

    /**
     * Like {@link #requireMember}, but also locks the group row until the end of the
     * transaction. Every operation that changes who is in the group, who owns it, its
     * invitations or its money has to take this lock, so such operations on a single group
     * run one after another. Only the membership is checked before the lock, so that an
     * outsider cannot lock the group; everything else is read after it, and the membership
     * is checked again.
     * <p>
     * Accepting or declining an invitation is the one exception: the user is not a member
     * yet, so {@code InvitationService} reads the invitation, locks the group itself and
     * reads the invitation again.
     */
    public void requireMemberAndLock(Long groupId, Long userId) {
        requireMember(groupId, userId);
        groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        if (!groupRepository.existsByIdAndMembersUserId(groupId, userId)) {
            throw new GroupAccessDeniedException("You are not a member of this group");
        }
    }
}
