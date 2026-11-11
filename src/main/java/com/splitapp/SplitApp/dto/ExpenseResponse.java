package com.splitapp.SplitApp.dto;

import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseResponse(
        Long id,
        Long groupId,
        BigDecimal amount,
        ExpenseCategory category,
        String description,
        Long paidById,
        List<ExpenseSplitResponse> splits) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getAmount(),
                expense.getCategory(),
                expense.getDescription(),
                expense.getPaidBy().getId(),
                expense.getSplits().stream()
                        .map(ExpenseSplitResponse::from)
                        .toList());
    }
}
