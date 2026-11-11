package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
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
}
