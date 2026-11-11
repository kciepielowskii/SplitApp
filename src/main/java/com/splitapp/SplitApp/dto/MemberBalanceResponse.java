package com.splitapp.SplitApp.dto;

import java.math.BigDecimal;

public record MemberBalanceResponse(Long userId, String firstName, String lastName, BigDecimal balance) {
}
