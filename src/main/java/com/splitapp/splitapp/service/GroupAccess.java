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
     * transaction. Every operation that changes who is in the group, who owns it, or its
     * money has to use this one, so such operations on a single group run one after another.
     * The lock is taken before anything else about the group is read.
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
