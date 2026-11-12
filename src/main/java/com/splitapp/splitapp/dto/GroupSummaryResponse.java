package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.GroupRole;

/** One row of the caller's group list; the members are available from GET /groups/{id}. */
public record GroupSummaryResponse(
        Long id,
        String groupName,
        String groupDescription,
        GroupRole myRole
) {
}
