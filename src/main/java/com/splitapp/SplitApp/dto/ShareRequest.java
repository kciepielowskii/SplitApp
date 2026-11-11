package com.splitapp.SplitApp.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Amount (for AMOUNT splits) or percentage (for PERCENT splits) owed by one participant. */
public record ShareRequest(@NotNull Long userId, @NotNull BigDecimal value) {
}
