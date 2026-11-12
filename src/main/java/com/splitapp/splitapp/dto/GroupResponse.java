package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.ExpenseGroup;

import java.util.List;

public record GroupResponse(
        Long id,
        String groupName,
        String groupDescription,
        Long createdById,
        List<GroupMemberResponse> members
) {
    public static GroupResponse from(ExpenseGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getGroupName(),
                group.getGroupDescription(),
                group.getCreatedBy().getId(),
                group.getMembers().stream()
                        .map(GroupMemberResponse::from)
                        .toList()
        );
    }
}
