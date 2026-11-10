package com.splitapp.SplitApp.dto;

import com.splitapp.SplitApp.entity.GroupMember;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;

public record GroupMemberResponse(
        Long userId,
        String firstName,
        String lastName,
        GroupRole role
) {
    public static GroupMemberResponse from(GroupMember member) {
        User user = member.getUser();
        return new GroupMemberResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                member.getRole()
        );
    }
}
