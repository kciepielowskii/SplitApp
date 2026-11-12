package com.splitapp.splitapp.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(@NotNull Long userId) {
}
