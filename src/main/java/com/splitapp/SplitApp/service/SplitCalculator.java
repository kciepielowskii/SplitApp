package com.splitapp.SplitApp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public final class SplitCalculator {
    private static final long FULL_PERCENT_IN_HUNDREDTHS = 10_000;

    private SplitCalculator() {
    }

    /**
     * Splits the amount equally between the given number of participants. Remaining cents go
     * to the first participants, so the parts always add up to exactly the total.
     */
    public static List<BigDecimal> splitEqually(BigDecimal total, int participants) {
        Objects.requireNonNull(total, "Amount is required");
        if (participants < 1) {
            throw new IllegalArgumentException("At least one participant is required");
        }
        long totalCents = toCents(total);

        long baseCents = totalCents / participants;
        long remainder = totalCents % participants;
        if (baseCents == 0) {
            throw new IllegalArgumentException("Amount is too small to split between "
                    + participants + " participants");
        }

        List<BigDecimal> parts = new ArrayList<>(participants);
        for (int i = 0; i < participants; i++) {
            long cents = baseCents + (i < remainder ? 1 : 0);
            parts.add(BigDecimal.valueOf(cents, 2));
        }
        return parts;
    }

    /**
     * Validates explicit amounts: each must be positive with at most 2 decimal places and
     * together they must add up exactly to the total. Returns them with scale 2.
     */
    public static List<BigDecimal> validateAmounts(BigDecimal total, List<BigDecimal> amounts) {
        Objects.requireNonNull(total, "Amount is required");
        Objects.requireNonNull(amounts, "Amounts are required");
        long totalCents = toCents(total);
        if (amounts.isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }

        List<BigDecimal> normalized = new ArrayList<>(amounts.size());
        long sum = 0;
        for (BigDecimal amount : amounts) {
            long cents = exactCents(Objects.requireNonNull(amount, "Amount is required"),
                    "Amounts must have at most 2 decimal places");
            if (cents <= 0) {
                throw new IllegalArgumentException("Every amount must be greater than zero");
            }
            sum += cents;
            normalized.add(BigDecimal.valueOf(cents, 2));
        }
        if (sum != totalCents) {
            throw new IllegalArgumentException("Amounts must add up to the total amount");
        }
        return normalized;
    }

    /**
     * Splits the amount by percentages (at most 2 decimal places, adding up to exactly 100).
     * The cents left after rounding down go to the largest fractional parts, so the parts
     * always add up to exactly the total.
     */
    public static List<BigDecimal> splitByPercentages(BigDecimal total, List<BigDecimal> percentages) {
        Objects.requireNonNull(total, "Amount is required");
        Objects.requireNonNull(percentages, "Percentages are required");
        long totalCents = toCents(total);
        if (percentages.isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }

        int count = percentages.size();
        long[] hundredths = new long[count];
        long sum = 0;
        for (int i = 0; i < count; i++) {
            hundredths[i] = exactCents(Objects.requireNonNull(percentages.get(i), "Percentage is required"),
                    "Percentages must have at most 2 decimal places");
            if (hundredths[i] <= 0) {
                throw new IllegalArgumentException("Every percentage must be greater than zero");
            }
            sum += hundredths[i];
        }
        if (sum != FULL_PERCENT_IN_HUNDREDTHS) {
            throw new IllegalArgumentException("Percentages must add up to 100");
        }

        long[] cents = new long[count];
        long[] remainders = new long[count];
        long allocated = 0;
        for (int i = 0; i < count; i++) {
            long scaled = totalCents * hundredths[i];
            cents[i] = scaled / FULL_PERCENT_IN_HUNDREDTHS;
            remainders[i] = scaled % FULL_PERCENT_IN_HUNDREDTHS;
            allocated += cents[i];
        }

        long leftover = totalCents - allocated;
        List<Integer> byRemainder = IntStream.range(0, count).boxed()
                .sorted(Comparator.<Integer>comparingLong(i -> -remainders[i]).thenComparingInt(i -> i))
                .toList();
        for (int k = 0; k < leftover; k++) {
            cents[byRemainder.get(k)]++;
        }

        List<BigDecimal> parts = new ArrayList<>(count);
        for (long part : cents) {
            if (part == 0) {
                throw new IllegalArgumentException("Amount is too small to split by these percentages");
            }
            parts.add(BigDecimal.valueOf(part, 2));
        }
        return parts;
    }

    private static long toCents(BigDecimal total) {
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return exactCents(total, "Amount must have at most 2 decimal places");
    }

    private static long exactCents(BigDecimal value, String message) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(message);
        }
    }
}
