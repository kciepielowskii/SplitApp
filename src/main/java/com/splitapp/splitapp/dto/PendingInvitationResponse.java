package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.GroupInvitation;

import java.time.Instant;

public record PendingInvitationResponse(
        Long id,
        String groupName,
        String invitedByFirstName,
        String invitedByLastName,
        Instant createdAt) {

    public static PendingInvitationResponse from(GroupInvitation invitation) {
        return new PendingInvitationResponse(
                invitation.getId(),
                invitation.getGroup().getGroupName(),
                invitation.getInvitedBy().getFirstName(),
                invitation.getInvitedBy().getLastName(),
                invitation.getCreatedAt());
    }
}
