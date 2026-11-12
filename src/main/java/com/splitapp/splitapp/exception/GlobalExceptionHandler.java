package com.splitapp.splitapp.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String UNIQUE_VIOLATION = "23505";

    // SQLState class 22: "data exception", the database refuses the value itself
    private static final String DATA_EXCEPTION_CLASS = "22";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, SortedSet<String>> messages = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                addMessage(messages, error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                addMessage(messages, "_global", error.getDefaultMessage()));

        return handleExceptionInternal(exception, validationProblem(joined(messages)), headers,
                status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request body is missing or malformed");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String detail = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? "Parameter '" + mismatch.getName() + "' has an invalid value"
                : "A request parameter has an invalid value";
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid parameter", detail);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "User not found", exception.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, SortedSet<String>> messages = new TreeMap<>();
        exception.getConstraintViolations().forEach(violation ->
                addMessage(messages, fieldName(violation), violation.getMessage()));

        return validationProblem(joined(messages));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        // Do not log the exception message: the database includes the conflicting values in it.
        String constraint = exception.getCause()
                instanceof org.hibernate.exception.ConstraintViolationException cause
                && cause.getConstraintName() != null
                ? cause.getConstraintName()
                : "unknown";
        String sqlState = sqlStateOf(exception);

        if (UNIQUE_VIOLATION.equals(sqlState)) {
            log.warn("Unique constraint violated: {}", constraint);
            return problem(HttpStatus.CONFLICT, "Conflict",
                    "The request conflicts with existing data");
        }
        if (sqlState != null && sqlState.startsWith(DATA_EXCEPTION_CLASS)) {
            log.warn("The database refused a value, SQLState: {}", sqlState);
            return problem(HttpStatus.BAD_REQUEST, "Invalid data",
                    "The request contains data that cannot be stored");
        }

        log.error("Data integrity violation, constraint: {}, SQLState: {}", constraint, sqlState);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.");
    }

    // Optimistic and pessimistic locking failures and deadlocks all extend this exception.
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail handleConcurrencyFailure(ConcurrencyFailureException exception) {
        log.warn("Concurrent modification: {}", exception.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The data was changed by another request; please try again");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        if (exception instanceof DataAccessException) {
            // the message of a database exception contains the SQL and the values in it
            log.error("Unexpected database error: {}, SQLState: {}",
                    exception.getClass().getName(), sqlStateOf(exception));
        } else {
            log.error("Unexpected error", exception);
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.");
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ProblemDetail handleGroupNotFound(GroupNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Group not found", exception.getMessage());
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    public ProblemDetail handleExpenseNotFound(ExpenseNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Expense not found", exception.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail handlePaymentNotFound(PaymentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Payment not found", exception.getMessage());
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    public ProblemDetail handleInvitationNotFound(InvitationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Invitation not found", exception.getMessage());
    }

    @ExceptionHandler(GroupAccessDeniedException.class)
    public ProblemDetail handleGroupAccessDenied(GroupAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violation", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    private ProblemDetail validationProblem(Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    // Keep only the last node of the path, so "createGroup.groupName" becomes "groupName",
    // the same key the controller-level validation uses.
    private static String fieldName(ConstraintViolation<?> violation) {
        String name = "";
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }

    private static void addMessage(Map<String, SortedSet<String>> messages, String field,
                                   String message) {
        messages.computeIfAbsent(field, key -> new TreeSet<>())
                .add(message == null ? "Invalid value" : message);
    }

    // Every violation of a field is reported, in alphabetical order, so the same request
    // always gets the same answer.
    private static Map<String, String> joined(Map<String, SortedSet<String>> messages) {
        Map<String, String> errors = new LinkedHashMap<>();
        messages.forEach((field, texts) -> errors.put(field, String.join("; ", texts)));
        return errors;
    }

    // The SQLState lives on the driver's SQLException at the bottom of the cause chain, whatever
    // exception type Hibernate and Spring wrapped it in.
    private static String sqlStateOf(Throwable exception) {
        String sqlState = null;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                sqlState = sql.getSQLState();
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return sqlState;
    }
}
