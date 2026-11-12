package com.splitapp.splitapp.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrencyFailureHandlingTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldAnswer409WhenARowWasChangedByAnotherRequest() {
        ProblemDetail problem = handler.handleConcurrencyFailure(
                new OptimisticLockingFailureException("stale row"));

        assertEquals(409, problem.getStatus());
        assertEquals("Conflict", problem.getTitle());
    }

    @Test
    void shouldAnswer409WhenTheDatabaseReportsALockFailure() {
        ProblemDetail problem = handler.handleConcurrencyFailure(
                new CannotAcquireLockException("deadlock detected"));

        assertEquals(409, problem.getStatus());
    }
}
