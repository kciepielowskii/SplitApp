package com.splitapp.SplitApp.dto;

import java.math.BigDecimal;

public record SettlementTransferResponse(Long fromUserId, Long toUserId, BigDecimal amount) {
}
