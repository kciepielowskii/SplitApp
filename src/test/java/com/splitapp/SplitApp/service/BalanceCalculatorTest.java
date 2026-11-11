package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.SettlementTransferResponse;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseSplit;
import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.User;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void shouldReturnEmptyBalancesWithoutExpensesAndPayments() {
        assertTrue(BalanceCalculator.netCents(List.of(), List.of()).isEmpty());
    }

    @Test
    void shouldCreditPayerAndDebitParticipantsOfExpense() {
        User payer = user(1L);
        User friend = user(2L);
        Expense expense = expense(payer, "10.00",
                split(payer, "5.00"), split(friend, "5.00"));

        Map<Long, Long> net = BalanceCalculator.netCents(List.of(expense), List.of());

        assertEquals(Map.of(1L, 500L, 2L, -500L), net);
    }

    @Test
    void shouldReduceDebtWhenPaymentIsRecorded() {
        User payer = user(1L);
        User friend = user(2L);
        Expense expense = expense(payer, "10.00",
                split(payer, "5.00"), split(friend, "5.00"));
        Payment payment = payment(friend, payer, "3.00");

        Map<Long, Long> net = BalanceCalculator.netCents(List.of(expense), List.of(payment));

        assertEquals(Map.of(1L, 200L, 2L, -200L), net);
    }

    private static User user(Long id) {
        User user = new User("Jan", "Kowalski", "jan" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static ExpenseSplit split(User user, String amount) {
        ExpenseSplit split = mock(ExpenseSplit.class);
        when(split.getUser()).thenReturn(user);
        when(split.getAmount()).thenReturn(new BigDecimal(amount));
        return split;
    }

    private static Expense expense(User payer, String amount, ExpenseSplit... splits) {
        Expense expense = mock(Expense.class);
        when(expense.getPaidBy()).thenReturn(payer);
        when(expense.getAmount()).thenReturn(new BigDecimal(amount));
        when(expense.getSplits()).thenReturn(List.of(splits));
        return expense;
    }

    private static Payment payment(User sender, User receiver, String amount) {
        Payment payment = mock(Payment.class);
        when(payment.getSender()).thenReturn(sender);
        when(payment.getReceiver()).thenReturn(receiver);
        when(payment.getAmount()).thenReturn(new BigDecimal(amount));
        return payment;
    }
}
