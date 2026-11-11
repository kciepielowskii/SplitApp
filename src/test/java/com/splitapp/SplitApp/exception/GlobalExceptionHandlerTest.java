package com.splitapp.SplitApp.exception;

import com.splitapp.SplitApp.dto.RegisterUserRequest;
import com.splitapp.SplitApp.service.GroupService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.lang.reflect.Method;
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
        assertEquals("Conflict", result.getTitle());
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
        assertEquals("Request validation failed", result.getDetail());
    }

    @Test
    void shouldUseFieldNameAsKeyForMethodParameterViolations() throws Exception {
        Method method = GroupService.class.getMethod(
                "createGroup", String.class, String.class, Long.class);

        Set<ConstraintViolation<GroupService>> violations = validator.forExecutables()
                .validateParameters(new GroupService(null, null, null), method,
                        new Object[]{" ", null, 1L});

        ProblemDetail result =
                handler.handleConstraintViolation(new ConstraintViolationException(violations));

        Map<?, ?> errors = (Map<?, ?>) result.getProperties().get("errors");
        assertEquals(Set.of("groupName"), errors.keySet());
    }

    @Test
    void shouldHideDetailsOfUnexpectedErrors() {
        ProblemDetail result = handler.handleUnexpected(new RuntimeException("secret detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), result.getStatus());
        assertEquals("Internal server error", result.getTitle());
        assertEquals("An unexpected error occurred.", result.getDetail());
    }

    @Test
    void shouldReturnNotFoundForMissingUser() {
        ProblemDetail result = handler.handleUserNotFound(new UserNotFoundException("User not found"));

        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus());
        assertEquals("User not found", result.getTitle());
        assertEquals("User not found", result.getDetail());
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyUsed() {
        ProblemDetail result =
                handler.handleEmailAlreadyUsed(new EmailAlreadyUsedException("Email is already in use"));

        assertEquals(HttpStatus.CONFLICT.value(), result.getStatus());
        assertEquals("Conflict", result.getTitle());
        assertEquals("Email is already in use", result.getDetail());
    }

    @Test
    void shouldReturnNotFoundForMissingGroup() {
        ProblemDetail result = handler.handleGroupNotFound(new GroupNotFoundException("Group not found"));

        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus());
        assertEquals("Group not found", result.getTitle());
        assertEquals("Group not found", result.getDetail());
    }

    @Test
    void shouldReturn422ForBusinessRuleViolation() {
        ProblemDetail result =
                handler.handleBusinessRule(new BusinessRuleException("Participants must be unique"));

        assertEquals(422, result.getStatus());
        assertEquals("Business rule violation", result.getTitle());
        assertEquals("Participants must be unique", result.getDetail());
    }

    @Test
    void shouldReturn403WhenUserIsNotGroupMember() {
        ProblemDetail result = handler.handleGroupAccessDenied(
                new GroupAccessDeniedException("You are not a member of this group"));

        assertEquals(403, result.getStatus());
        assertEquals("Forbidden", result.getTitle());
        assertEquals("You are not a member of this group", result.getDetail());
    }
}
