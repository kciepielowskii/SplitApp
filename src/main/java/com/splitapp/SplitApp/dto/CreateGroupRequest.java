package com.splitapp.SplitApp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String groupName,
        @Size(max = 255) String groupDescription,
        @NotNull Long userId) {

        public CreateGroupRequest {
            groupName = groupName == null ? null : groupName.trim();
            groupDescription = groupDescription == null || groupDescription.isBlank()
                    ? null : groupDescription.trim();
        }
}
