package com.splitapp.SplitApp.exception;

import com.splitapp.SplitApp.dto.RegisterUserRequest;
import com.splitapp.SplitApp.service.GroupService;
import com.splitapp.SplitApp.exception.InvitationNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldReturnConflictOnUniqueViolation() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate key value (secret@example.com)",
                new org.hibernate.exception.ConstraintViolationException(
                        "duplicate key", new SQLException("duplicate", "23505"), "uk_users_email"));

        ProblemDetail result = handler.handleDataIntegrityViolation(exception);

        assertEquals(HttpStatus.CONFLICT.value(), result.getStatus());
        assertEquals("Conflict", result.getTitle());
        assertEquals("The request conflicts with existing data", result.getDetail());
        assertFalse(result.getDetail().contains("secret"));
    }

    @Test
    void shouldReturn500ForOtherIntegrityViolations() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "fk violated",
                new org.hibernate.exception.ConstraintViolationException(
                        "fk violated", new SQLException("fk", "23503"), "fk_group"));

        ProblemDetail result = handler.handleDataIntegrityViolation(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), result.getStatus());
        assertEquals("An unexpected error occurred.", result.getDetail());
    }

    @Test
    void shouldReturn500WhenIntegrityViolationHasNoKnownCause() {
        ProblemDetail result =
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), result.getStatus());
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
                .validateParameters(mock(GroupService.class), method,
                        new Object[]{" ", null, 1L});

        ProblemDetail result =
                handler.handleConstraintViolation(new ConstraintViolationException(violations));

        Map<?, ?> errors = (Map<?, ?>) result.getProperties().get("errors");
        assertEquals(Set.of("groupName"), errors.keySet());
    }

    @Test
    void shouldIncludeGlobalErrorsInValidationResponse() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "amount", "must be positive"));
        binding.addError(new ObjectError("request", "Shares do not match the total"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("endpoint", Object.class), 0);

        ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(parameter, binding), new HttpHeaders(),
                HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        ProblemDetail body = (ProblemDetail) response.getBody();
        Map<?, ?> errors = (Map<?, ?>) body.getProperties().get("errors");
        assertEquals("must be positive", errors.get("amount"));
        assertEquals("Shares do not match the total", errors.get("_global"));
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
    void shouldReturnNotFoundForMissingExpense() {
        ProblemDetail result =
                handler.handleExpenseNotFound(new ExpenseNotFoundException("Expense not found"));

        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus());
        assertEquals("Expense not found", result.getTitle());
    }

    @Test
    void shouldReturnNotFoundForMissingPayment() {
        ProblemDetail result =
                handler.handlePaymentNotFound(new PaymentNotFoundException("Payment not found"));

        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus());
        assertEquals("Payment not found", result.getTitle());
    }

    @Test
    void shouldReturnNotFoundForMissingInvitation() {
        ProblemDetail result = handler.handleInvitationNotFound(
                new InvitationNotFoundException("Invitation not found"));

        assertEquals(HttpStatus.NOT_FOUND.value(), result.getStatus());
        assertEquals("Invitation not found", result.getTitle());
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

    @SuppressWarnings("unused")
    private static void endpoint(Object request) {
    }
}
