package com.splitapp.splitapp.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterUserRequestValidationTest {
    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<RegisterUserRequest>> validate(String password) {
        return validator.validate(
                new RegisterUserRequest("Jan", "Kowalski", "jan@example.com", password));
    }

    @Test
    void shouldAcceptPasswordOf72Bytes() {
        assertTrue(validate("a".repeat(72)).isEmpty());
    }

    @Test
    void shouldRejectPasswordLongerThan72Bytes() {
        Set<ConstraintViolation<RegisterUserRequest>> violations = validate("a".repeat(73));

        assertEquals(1, violations.size());
        assertEquals("password", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void shouldRejectMultiByteCharactersExceeding72Bytes() {
        // 40 characters, 80 bytes in UTF-8
        Set<ConstraintViolation<RegisterUserRequest>> violations = validate("ą".repeat(40));

        assertEquals(1, violations.size());
        assertEquals("password", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void shouldAcceptShortPasswordWithPolishCharacters() {
        assertTrue(validate("zażółć123").isEmpty());
    }

    @Test
    void shouldTrimNamesAndEmailButNotPassword() {
        RegisterUserRequest request =
                new RegisterUserRequest("  Jan ", " Kowalski  ", "  jan@example.com ", " secret123 ");

        assertEquals("Jan", request.firstName());
        assertEquals("Kowalski", request.lastName());
        assertEquals("jan@example.com", request.email());
        assertEquals(" secret123 ", request.password());
        assertTrue(validator.validate(request).isEmpty());
    }
}
