package com.splitapp.splitapp.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareRequestValidationTest {
    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private static boolean isValid(String value) {
        return VALIDATOR.validate(new ShareRequest(1L, new BigDecimal(value))).isEmpty();
    }

    @Test
    void shouldAcceptTypicalValues() {
        assertTrue(isValid("0.01"));
        assertTrue(isValid("60"));
        assertTrue(isValid("99999999.99"));
    }

    @Test
    void shouldRejectZeroAndNegativeValues() {
        assertFalse(isValid("0"));
        assertFalse(isValid("-5.00"));
    }

    @Test
    void shouldRejectTooManyDigits() {
        assertFalse(isValid("5.005"));
        assertFalse(isValid("100000000.00"));
    }
}
