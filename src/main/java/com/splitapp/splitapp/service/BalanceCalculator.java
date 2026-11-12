package com.splitapp.splitapp.service;

import com.splitapp.splitapp.dto.SettlementTransferResponse;
import com.splitapp.splitapp.entity.Expense;
import com.splitapp.splitapp.entity.ExpenseSplit;
import com.splitapp.splitapp.entity.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class BalanceCalculator {
    private BalanceCalculator() {
    }

    /** Net balance of every user in cents: positive means others owe the user. */
    public static Map<Long, Long> netCents(List<Expense> expenses, List<Payment> payments) {
        Map<Long, Long> net = new HashMap<>();
        for (Expense expense : expenses) {
            net.merge(expense.getPaidBy().getId(), cents(expense.getAmount()), Long::sum);
            for (ExpenseSplit split : expense.getSplits()) {
                net.merge(split.getUser().getId(), -cents(split.getAmount()), Long::sum);
            }
        }
        for (Payment payment : payments) {
            net.merge(payment.getSender().getId(), cents(payment.getAmount()), Long::sum);
            net.merge(payment.getReceiver().getId(), -cents(payment.getAmount()), Long::sum);
        }
        return net;
    }

    /** Suggests transfers that bring every balance to zero, biggest debts first. */
    public static List<SettlementTransferResponse> settle(Map<Long, Long> net) {
        List<long[]> creditors = new ArrayList<>();
        List<long[]> debtors = new ArrayList<>();
        new TreeMap<>(net).forEach((userId, cents) -> {
            if (cents > 0) {
                creditors.add(new long[]{userId, cents});
            } else if (cents < 0) {
                debtors.add(new long[]{userId, -cents});
            }
        });
        Comparator<long[]> biggestFirst = (a, b) -> a[1] != b[1]
                ? Long.compare(b[1], a[1])
                : Long.compare(a[0], b[0]);
        creditors.sort(biggestFirst);
        debtors.sort(biggestFirst);

        List<SettlementTransferResponse> transfers = new ArrayList<>();
        int d = 0;
        int c = 0;
        while (d < debtors.size() && c < creditors.size()) {
            long[] debtor = debtors.get(d);
            long[] creditor = creditors.get(c);
            long cents = Math.min(debtor[1], creditor[1]);
            transfers.add(new SettlementTransferResponse(
                    debtor[0], creditor[0], BigDecimal.valueOf(cents, 2)));
            debtor[1] -= cents;
            creditor[1] -= cents;
            if (debtor[1] == 0) {
                d++;
            }
            if (creditor[1] == 0) {
                c++;
            }
        }
        return transfers;
    }

    private static long cents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
    }
}
