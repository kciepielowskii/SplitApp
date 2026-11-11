package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.SettlementTransferResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceCalculatorTest {

    @Test
    void shouldSettleSingleDebt() {
        List<SettlementTransferResponse> transfers =
                BalanceCalculator.settle(Map.of(1L, 1000L, 2L, -1000L));

        assertEquals(List.of(new SettlementTransferResponse(2L, 1L, new BigDecimal("10.00"))), transfers);
    }

    @Test
    void shouldPayBiggestDebtorFirstWhenThereIsOneCreditor() {
        List<SettlementTransferResponse> transfers =
                BalanceCalculator.settle(Map.of(1L, 1000L, 2L, -400L, 3L, -600L));

        assertEquals(List.of(
                new SettlementTransferResponse(3L, 1L, new BigDecimal("6.00")),
                new SettlementTransferResponse(2L, 1L, new BigDecimal("4.00"))), transfers);
    }

    @Test
    void shouldSplitOneDebtorBetweenCreditors() {
        List<SettlementTransferResponse> transfers =
                BalanceCalculator.settle(Map.of(1L, 700L, 2L, 300L, 3L, -1000L));

        assertEquals(List.of(
                new SettlementTransferResponse(3L, 1L, new BigDecimal("7.00")),
                new SettlementTransferResponse(3L, 2L, new BigDecimal("3.00"))), transfers);
    }

    @Test
    void shouldReturnNothingWhenEveryoneIsSettled() {
        assertTrue(BalanceCalculator.settle(Map.of(1L, 0L, 2L, 0L)).isEmpty());
    }
}
