package com.splitapp.splitapp.dto;

import java.util.List;

public record GroupBalancesResponse(
        Long groupId,
        List<MemberBalanceResponse> balances,
        List<SettlementTransferResponse> transfers) {
}
