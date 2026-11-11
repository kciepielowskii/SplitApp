package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class GroupService {
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupAccess groupAccess;

    @Transactional
    public GroupResponse createGroup(@NotBlank @Size(max = 100) String groupName,
                                     @Size(max = 255) String groupDescription,
                                     @NotNull Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        ExpenseGroup group = new ExpenseGroup(groupName, groupDescription, user);
        group.addMember(user, GroupRole.OWNER);
        return GroupResponse.from(groupRepository.save(group));
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(@NotNull Long groupId, @NotNull Long currentUserId) {
        groupAccess.requireMember(groupId, currentUserId);
        return groupRepository.findWithMembersById(groupId)
                .map(GroupResponse::from)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
    }
}
