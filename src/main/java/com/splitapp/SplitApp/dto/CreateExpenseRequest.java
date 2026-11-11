package com.splitapp.SplitApp.dto;

import com.splitapp.SplitApp.entity.ExpenseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateExpenseRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotNull ExpenseCategory category,
        @Size(max = 255) String description,
        @NotNull Long paidById,
        @NotEmpty @Size(max = 100) List<@NotNull Long> participantIds) {

    public CreateExpenseRequest {
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
