package com.splitapp.SplitApp.exception;

import com.splitapp.SplitApp.dto.RegisterUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldReturnConflictOnDataIntegrityViolation() {
        ProblemDetail result = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key"));

        assertEquals(HttpStatus.CONFLICT.value(), result.getStatus());
        assertEquals("The request conflicts with existing data", result.getDetail());
        assertFalse(result.getDetail().contains("duplicate key"));
    }

    @Test
    void shouldReturnBadRequestWithErrorsOnConstraintViolation() {
        Set<ConstraintViolation<RegisterUserRequest>> violations = validator.validate(
                new RegisterUserRequest("Jan", "Kowalski", "not-an-email", "secret123"));

        ProblemDetail result =
                handler.handleConstraintViolation(new ConstraintViolationException(violations));

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getStatus());
        assertEquals("Validation failed", result.getTitle());
        Map<?, ?> errors = (Map<?, ?>) result.getProperties().get("errors");
        assertTrue(errors.containsKey("email"));
    }

    @Test
    void shouldHideDetailsOfUnexpectedErrors() {
        ProblemDetail result = handler.handleUnexpected(new RuntimeException("secret detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), result.getStatus());
        assertEquals("An unexpected error occurred.", result.getDetail());
    }

    @Test
    void shouldReturnNotFoundForMissingUser() {
        ProblemDetail result = handler.handleUserNotFound(new UserNotFoundException("User not found"));

        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus());
        assertEquals("User not found", result.getDetail());
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyUsed() {
        ProblemDetail result =
                handler.handleEmailAlreadyUsed(new EmailAlreadyUsedException("Email is already in use"));

        assertEquals(HttpStatus.CONFLICT.value(), result.getStatus());
        assertEquals("Email is already in use", result.getDetail());
    }
}
