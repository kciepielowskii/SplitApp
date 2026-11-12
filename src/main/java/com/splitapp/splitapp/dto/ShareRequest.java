package com.splitapp.splitapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Amount (for AMOUNT splits) or percentage (for PERCENT splits) owed by one participant. */
public record ShareRequest(
        @NotNull Long userId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal value) {
}
