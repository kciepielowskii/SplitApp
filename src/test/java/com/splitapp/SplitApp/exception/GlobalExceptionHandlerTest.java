package com.splitapp.SplitApp.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnConflictOnDataIntegrityViolation() {
        ProblemDetail result = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key"));

        assertEquals(HttpStatus.CONFLICT.value(), result.getStatus());
    }
}
