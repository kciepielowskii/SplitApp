package com.splitapp.splitapp.dto;

import java.math.BigDecimal;

public record SettlementTransferResponse(Long fromUserId, Long toUserId, BigDecimal amount) {
}
