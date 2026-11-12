package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.CreateExpenseRequest;
import com.splitapp.SplitApp.dto.ExpenseResponse;
import com.splitapp.SplitApp.dto.ExpenseSplitResponse;
import com.splitapp.SplitApp.dto.SplitType;
import com.splitapp.SplitApp.entity.ExpenseCategory;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.ExpenseNotFoundException;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.security.SecurityConfig;
import com.splitapp.SplitApp.security.WithMockAppUser;
import com.splitapp.SplitApp.service.ExpenseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class)
@WithMockAppUser
class ExpenseControllerTest {
    private static final String VALID_BODY = """
            {"amount":10.00,"category":"FOOD","description":"Obiad","paidById":1,"participantIds":[1,2]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    @Test
    void shouldCreateExpense() throws Exception {
        ExpenseResponse response = new ExpenseResponse(5L, 1L, new BigDecimal("10.00"),
                ExpenseCategory.FOOD, "Obiad", 1L, 1L, Instant.parse("2026-01-01T10:00:00Z"),
                List.of(new ExpenseSplitResponse(1L, new BigDecimal("5.00")),
                        new ExpenseSplitResponse(2L, new BigDecimal("5.00"))));
        when(expenseService.createExpense(eq(1L), eq(1L), any(CreateExpenseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.groupId").value(1))
                .andExpect(jsonPath("$.paidById").value(1))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.splits[0].userId").value(1))
                .andExpect(jsonPath("$.splits[1].userId").value(2));
    }

    @Test
    void shouldRejectInvalidRequestWith400() throws Exception {
        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":0,"category":"FOOD","participantIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.paidById").exists());

        verifyNoInteractions(expenseService);
    }

    @Test
    void shouldRejectUnknownCategory() throws Exception {
        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"GAMBLING","paidById":1,"participantIds":[1]}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void shouldRejectNonNumericGroupId() throws Exception {
        mockMvc.perform(post("/groups/abc/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(expenseService);
    }

    @Test
    void shouldReturn404WhenGroupDoesNotExist() throws Exception {
        when(expenseService.createExpense(eq(99L), eq(1L), any(CreateExpenseRequest.class)))
                .thenThrow(new GroupNotFoundException("Group not found"));

        mockMvc.perform(post("/groups/99/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"))
                .andExpect(jsonPath("$.detail").value("Group not found"));
    }

    @Test
    void shouldReturn422OnBusinessRuleViolation() throws Exception {
        when(expenseService.createExpense(eq(1L), eq(1L), any(CreateExpenseRequest.class)))
                .thenThrow(new BusinessRuleException("Payer is not a member of this group"));

        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"))
                .andExpect(jsonPath("$.detail").value("Payer is not a member of this group"));
    }

    @Test
    void shouldRejectMoreThan100Participants() throws Exception {
        String ids = IntStream.rangeClosed(1, 101)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));

        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":1,"participantIds":[%s]}
                                """.formatted(ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.participantIds").exists());

        verifyNoInteractions(expenseService);
    }

    @Test
    void shouldReturnGroupExpenses() throws Exception {
        ExpenseResponse response = new ExpenseResponse(5L, 1L, new BigDecimal("10.00"),
                ExpenseCategory.FOOD, "Obiad", 1L, 1L, Instant.parse("2026-01-01T10:00:00Z"),
                List.of(new ExpenseSplitResponse(1L, new BigDecimal("10.00"))));
        when(expenseService.getExpenses(1L, 1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/groups/1/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].createdById").value(1))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].splits[0].userId").value(1));
    }

    @Test
    void shouldReturn404WhenListingExpensesOfUnknownGroup() throws Exception {
        when(expenseService.getExpenses(99L, 1L)).thenThrow(new GroupNotFoundException("Group not found"));

        mockMvc.perform(get("/groups/99/expenses"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldAcceptAmountSplitWithShares() throws Exception {
        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":1,"splitType":"AMOUNT",
                                 "shares":[{"userId":1,"value":3.00},{"userId":2,"value":7.00}]}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateExpenseRequest> captor = ArgumentCaptor.forClass(CreateExpenseRequest.class);
        verify(expenseService).createExpense(eq(1L), eq(1L), captor.capture());
        assertEquals(SplitType.AMOUNT, captor.getValue().splitType());
        assertEquals(2, captor.getValue().shares().size());
    }

    @Test
    void shouldRejectUnknownSplitTypeAndShareWithoutValue() throws Exception {
        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":1,"splitType":"WHATEVER",
                                 "shares":[{"userId":1,"value":10.00}]}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10.00,"category":"FOOD","paidById":1,"splitType":"AMOUNT",
                                 "shares":[{"userId":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void shouldDeleteExpense() throws Exception {
        mockMvc.perform(delete("/groups/1/expenses/5"))
                .andExpect(status().isNoContent());

        verify(expenseService).deleteExpense(1L, 1L, 5L);
    }

    @Test
    void shouldReturn404WhenDeletingUnknownExpense() throws Exception {
        doThrow(new ExpenseNotFoundException("Expense not found"))
                .when(expenseService).deleteExpense(1L, 1L, 99L);

        mockMvc.perform(delete("/groups/1/expenses/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Expense not found"));
    }

    @Test
    void shouldReturn403WhenDeletingSomeoneElsesExpense() throws Exception {
        doThrow(new GroupAccessDeniedException("Only the author or the group owner can delete this expense"))
                .when(expenseService).deleteExpense(1L, 1L, 5L);

        mockMvc.perform(delete("/groups/1/expenses/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }
}
