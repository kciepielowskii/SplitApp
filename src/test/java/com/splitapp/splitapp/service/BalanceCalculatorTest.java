package com.splitapp.splitapp.service;

import com.splitapp.splitapp.dto.SettlementTransferResponse;
import com.splitapp.splitapp.entity.Expense;
import com.splitapp.splitapp.entity.ExpenseSplit;
import com.splitapp.splitapp.entity.Payment;
import com.splitapp.splitapp.entity.User;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


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

    @Test
    void shouldBringEveryBalanceToZeroForRandomGroups() {
        Random random = new Random(42);
        for (int round = 0; round < 1000; round++) {
            int people = 2 + random.nextInt(9);
            Map<Long, Long> balances = new HashMap<>();
            long sum = 0;
            for (long id = 1; id < people; id++) {
                long cents = random.nextInt(200_001) - 100_000;
                balances.put(id, cents);
                sum += cents;
            }
            balances.put((long) people, -sum);

            List<SettlementTransferResponse> transfers = BalanceCalculator.settle(balances);

            Map<Long, Long> remaining = new HashMap<>(balances);
            for (SettlementTransferResponse transfer : transfers) {
                long cents = transfer.amount().unscaledValue().longValueExact();
                String where = "round " + round + ", transfer " + transfer;
                assertTrue(cents > 0, where);
                assertNotEquals(debtorOf(transfer), creditorOf(transfer), where);
                assertTrue(balances.get(debtorOf(transfer)) < 0, where);
                assertTrue(balances.get(creditorOf(transfer)) > 0, where);
                remaining.merge(debtorOf(transfer), cents, Long::sum);
                remaining.merge(creditorOf(transfer), -cents, Long::sum);
            }

            assertTrue(remaining.values().stream().allMatch(cents -> cents == 0),
                    "round " + round + " left " + remaining);
            long peopleWithABalance = balances.values().stream().filter(cents -> cents != 0).count();
            assertTrue(transfers.size() <= Math.max(0L, peopleWithABalance - 1),
                    "round " + round + " used " + transfers.size() + " transfers");
        }
    }

    @Test
    void shouldSettleSeveralDebtorsAgainstSeveralCreditors() {
        Map<Long, Long> balances = Map.of(
                1L, 500L, 2L, 300L, 3L, 200L,
                4L, -450L, 5L, -350L, 6L, -200L);

        assertEquals(List.of(
                        new SettlementTransferResponse(4L, 1L, new BigDecimal("4.50")),
                        new SettlementTransferResponse(5L, 1L, new BigDecimal("0.50")),
                        new SettlementTransferResponse(5L, 2L, new BigDecimal("3.00")),
                        new SettlementTransferResponse(6L, 3L, new BigDecimal("2.00"))),
                BalanceCalculator.settle(balances));
    }

    @Test
    void shouldBreakTiesByUserIdWhateverTheOrderOfTheMap() {
        Map<Long, Long> forward = new LinkedHashMap<>();
        forward.put(3L, -100L);
        forward.put(5L, -100L);
        forward.put(9L, 200L);
        Map<Long, Long> backward = new LinkedHashMap<>();
        backward.put(9L, 200L);
        backward.put(5L, -100L);
        backward.put(3L, -100L);

        List<SettlementTransferResponse> expected = List.of(
                new SettlementTransferResponse(3L, 9L, new BigDecimal("1.00")),
                new SettlementTransferResponse(5L, 9L, new BigDecimal("1.00")));
        assertEquals(expected, BalanceCalculator.settle(forward));
        assertEquals(expected, BalanceCalculator.settle(backward));
    }

    // the only two places that depend on the field names of the record
    private static long debtorOf(SettlementTransferResponse transfer) {
        return transfer.fromUserId();
    }

    private static long creditorOf(SettlementTransferResponse transfer) {
        return transfer.toUserId();
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
