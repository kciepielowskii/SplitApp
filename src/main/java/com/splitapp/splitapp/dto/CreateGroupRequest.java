package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.validation.NoNul;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(max = 100) @NoNul String groupName,
        @Size(max = 255) @NoNul String groupDescription) {

    public CreateGroupRequest {
        groupName = groupName == null ? null : groupName.trim();
        groupDescription = groupDescription == null || groupDescription.isBlank()
                ? null : groupDescription.trim();
    }
}
