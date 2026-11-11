package com.splitapp.SplitApp.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(@NotNull Long userId) {
}
