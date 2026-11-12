package com.splitapp.splitapp.service;

import com.splitapp.splitapp.dto.GroupResponse;
import com.splitapp.splitapp.entity.ExpenseGroup;
import com.splitapp.splitapp.entity.GroupRole;
import com.splitapp.splitapp.entity.User;
import com.splitapp.splitapp.exception.GroupNotFoundException;
import com.splitapp.splitapp.exception.UserNotFoundException;
import com.splitapp.splitapp.repository.GroupRepository;
import com.splitapp.splitapp.repository.UserRepository;
import com.splitapp.splitapp.dto.GroupSummaryResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

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

    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> getMyGroups(@NotNull Long currentUserId) {
        return groupRepository.findSummariesByMemberUserId(currentUserId).stream()
                .map(view -> new GroupSummaryResponse(view.getId(), view.getGroupName(),
                        view.getGroupDescription(), view.getRole()))
                .toList();
    }
}
