package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.Expense;
import com.splitapp.splitapp.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExpenseResponse(
        Long id,
        Long groupId,
        BigDecimal amount,
        ExpenseCategory category,
        String description,
        Long paidById,
        Long createdById,
        Instant createdAt,
        List<ExpenseSplitResponse> splits) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getAmount(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getPaidBy().getId(),
                expense.getCreatedBy().getId(),
                expense.getCreatedAt(),
                expense.getSplits().stream()
                        .map(ExpenseSplitResponse::from)
                        .toList());
    }
}
