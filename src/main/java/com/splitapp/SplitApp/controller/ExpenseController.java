package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.CreateExpenseRequest;
import com.splitapp.SplitApp.dto.ExpenseResponse;
import com.splitapp.SplitApp.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(@PathVariable Long groupId,
                                         @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.createExpense(groupId, request);
    }
}
