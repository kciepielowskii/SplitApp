package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.CreateExpenseRequest;
import com.splitapp.SplitApp.dto.ExpenseResponse;
import com.splitapp.SplitApp.dto.ExpenseSplitResponse;
import com.splitapp.SplitApp.entity.ExpenseCategory;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.service.ExpenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
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
                ExpenseCategory.FOOD, "Obiad", 1L,
                List.of(new ExpenseSplitResponse(1L, new BigDecimal("5.00")),
                        new ExpenseSplitResponse(2L, new BigDecimal("5.00"))));
        when(expenseService.createExpense(eq(1L), any(CreateExpenseRequest.class))).thenReturn(response);

        mockMvc.perform(post("/groups/1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.groupId").value(1))
                .andExpect(jsonPath("$.paidById").value(1))
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
                .andExpect(jsonPath("$.errors.paidById").exists())
                .andExpect(jsonPath("$.errors.participantIds").exists());

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
        when(expenseService.createExpense(eq(99L), any(CreateExpenseRequest.class)))
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
        when(expenseService.createExpense(eq(1L), any(CreateExpenseRequest.class)))
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
}
