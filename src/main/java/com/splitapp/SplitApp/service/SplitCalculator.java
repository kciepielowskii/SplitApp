package com.splitapp.SplitApp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SplitCalculator {
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
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        long totalCents;
        try {
            totalCents = total.setScale(2, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount must have at most 2 decimal places");
        }

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
}
