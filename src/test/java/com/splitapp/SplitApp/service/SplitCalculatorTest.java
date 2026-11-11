package com.splitapp.SplitApp.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SplitCalculatorTest {

    @Test
    void shouldGiveRemainingCentsToFirstParticipants() {
        List<BigDecimal> parts = SplitCalculator.splitEqually(new BigDecimal("10.00"), 3);

        assertEquals(List.of(new BigDecimal("3.34"), new BigDecimal("3.33"), new BigDecimal("3.33")), parts);
    }

    @Test
    void shouldSplitEvenlyWhenDivisible() {
        List<BigDecimal> parts = SplitCalculator.splitEqually(new BigDecimal("10.00"), 2);

        assertEquals(List.of(new BigDecimal("5.00"), new BigDecimal("5.00")), parts);
    }

    @Test
    void shouldAllowSingleCentForOneParticipant() {
        List<BigDecimal> parts = SplitCalculator.splitEqually(new BigDecimal("0.01"), 1);

        assertEquals(List.of(new BigDecimal("0.01")), parts);
    }

    @Test
    void shouldRejectAmountTooSmallToSplit() {
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.splitEqually(new BigDecimal("0.02"), 3));
    }

    @Test
    void shouldRejectMoreThanTwoDecimalPlaces() {
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.splitEqually(new BigDecimal("10.005"), 2));
    }

    @Test
    void shouldRejectNoParticipants() {
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.splitEqually(new BigDecimal("10.00"), 0));
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.splitEqually(BigDecimal.ZERO, 2));
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.splitEqually(new BigDecimal("-5.00"), 2));
    }

    @Test
    void shouldAlwaysSumToTheTotal() {
        for (String amount : List.of("0.07", "1.00", "9.99", "100.01", "12345.67")) {
            for (int participants = 1; participants <= 7; participants++) {
                BigDecimal total = new BigDecimal(amount);
                if (total.movePointRight(2).longValue() < participants) {
                    continue; // too small to give everyone at least one cent
                }

                BigDecimal sum = SplitCalculator.splitEqually(total, participants).stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                assertEquals(total, sum, amount + " / " + participants);
            }
        }
    }

    @Test
    void shouldSplitByPercentages() {
        List<BigDecimal> parts = SplitCalculator.splitByPercentages(
                new BigDecimal("100.00"), List.of(new BigDecimal("70"), new BigDecimal("30")));

        assertEquals(List.of(new BigDecimal("70.00"), new BigDecimal("30.00")), parts);
    }

    @Test
    void shouldGiveLeftoverCentToLargestFractionalPart() {
        List<BigDecimal> parts = SplitCalculator.splitByPercentages(new BigDecimal("10.00"),
                List.of(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34")));

        assertEquals(List.of(new BigDecimal("3.33"), new BigDecimal("3.33"), new BigDecimal("3.34")), parts);
    }

    @Test
    void shouldGiveLeftoverCentToFirstOnTie() {
        List<BigDecimal> parts = SplitCalculator.splitByPercentages(
                new BigDecimal("0.05"), List.of(new BigDecimal("50"), new BigDecimal("50")));

        assertEquals(List.of(new BigDecimal("0.03"), new BigDecimal("0.02")), parts);
    }

    @Test
    void shouldRejectPercentagesNotAddingUpTo100() {
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.splitByPercentages(
                new BigDecimal("10.00"), List.of(new BigDecimal("50"), new BigDecimal("40"))));
    }

    @Test
    void shouldRejectZeroPercentage() {
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.splitByPercentages(
                new BigDecimal("10.00"), List.of(new BigDecimal("100"), BigDecimal.ZERO)));
    }

    @Test
    void shouldRejectPercentageSplitThatLeavesSomeoneWithNothing() {
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.splitByPercentages(
                new BigDecimal("0.01"), List.of(new BigDecimal("50"), new BigDecimal("50"))));
    }

    @Test
    void shouldAlwaysSumToTheTotalWhenSplittingByPercentages() {
        List<BigDecimal> percentages =
                List.of(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
        for (String amount : List.of("0.07", "1.00", "9.99", "100.01", "12345.67")) {
            BigDecimal total = new BigDecimal(amount);

            BigDecimal sum = SplitCalculator.splitByPercentages(total, percentages).stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertEquals(total, sum, amount);
        }
    }

    @Test
    void shouldAcceptAmountsAddingUpToTheTotalAndNormalizeScale() {
        List<BigDecimal> amounts = SplitCalculator.validateAmounts(
                new BigDecimal("10.00"), List.of(new BigDecimal("3"), new BigDecimal("7.00")));

        assertEquals(List.of(new BigDecimal("3.00"), new BigDecimal("7.00")), amounts);
    }

    @Test
    void shouldRejectAmountsNotAddingUpToTheTotal() {
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.validateAmounts(
                new BigDecimal("10.00"), List.of(new BigDecimal("3.00"), new BigDecimal("6.00"))));
    }

    @Test
    void shouldRejectNonPositiveAmountInExplicitSplit() {
        assertThrows(IllegalArgumentException.class, () -> SplitCalculator.validateAmounts(
                new BigDecimal("10.00"), List.of(new BigDecimal("10.00"), BigDecimal.ZERO)));
    }
}
