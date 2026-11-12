package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.ExpenseSplit;

import java.math.BigDecimal;

public record ExpenseSplitResponse(Long userId, BigDecimal amount) {
    public static ExpenseSplitResponse from(ExpenseSplit split) {
        return new ExpenseSplitResponse(split.getUser().getId(), split.getAmount());
    }
}
