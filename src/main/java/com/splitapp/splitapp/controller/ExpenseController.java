package com.splitapp.splitapp.controller;

import com.splitapp.splitapp.dto.CreateExpenseRequest;
import com.splitapp.splitapp.dto.ExpenseResponse;
import com.splitapp.splitapp.security.AppUserDetails;
import com.splitapp.splitapp.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(@PathVariable Long groupId,
                                         @Valid @RequestBody CreateExpenseRequest request,
                                         @AuthenticationPrincipal AppUserDetails currentUser) {
        return expenseService.createExpense(groupId, currentUser.getId(), request);
    }

    @GetMapping
    public List<ExpenseResponse> getExpenses(@PathVariable Long groupId,
                                             @AuthenticationPrincipal AppUserDetails currentUser) {
        return expenseService.getExpenses(groupId, currentUser.getId());
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@PathVariable Long groupId, @PathVariable Long expenseId,
                              @AuthenticationPrincipal AppUserDetails currentUser) {
        expenseService.deleteExpense(groupId, currentUser.getId(), expenseId);
    }
}
