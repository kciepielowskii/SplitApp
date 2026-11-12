package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.GroupMember;
import com.splitapp.splitapp.entity.GroupRole;
import com.splitapp.splitapp.entity.User;

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
