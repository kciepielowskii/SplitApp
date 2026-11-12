package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.GroupInvitation;
import com.splitapp.splitapp.entity.InvitationStatus;

import java.time.Instant;

public record InvitationResponse(
        Long id,
        Long groupId,
        String email,
        Long invitedById,
        InvitationStatus status,
        Instant createdAt) {

    public static InvitationResponse from(GroupInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getGroup().getId(),
                invitation.getEmail(),
                invitation.getInvitedBy().getId(),
                invitation.getStatus(),
                invitation.getCreatedAt());
    }
}
