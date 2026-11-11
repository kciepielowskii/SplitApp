package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.CreateExpenseRequest;
import com.splitapp.SplitApp.dto.ExpenseResponse;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.ExpenseSplit;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.ExpenseRepository;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExpenseResponse createExpense(@NotNull Long groupId,
                                         @NotNull @Valid CreateExpenseRequest request) {
        ExpenseGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        List<Long> participantIds = request.participantIds();
        if (new HashSet<>(participantIds).size() != participantIds.size()) {
            throw new BusinessRuleException("Participants must be unique");
        }

        User payer = userRepository.findById(request.paidById())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        requireMember(group, payer, "Payer is not a member of this group");

        Map<Long, User> participants = userRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<BigDecimal> shares;
        try {
            shares = SplitCalculator.splitEqually(request.amount(), participantIds.size());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(e.getMessage());
        }

        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        Expense expense = new Expense(amount, request.category(), request.description(), group, payer);
        for (int i = 0; i < participantIds.size(); i++) {
            User participant = participants.get(participantIds.get(i));
            if (participant == null) {
                throw new UserNotFoundException("User not found");
            }
            requireMember(group, participant, "Participant is not a member of this group");
            expense.addSplit(new ExpenseSplit(participant, shares.get(i)));
        }

        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    private static void requireMember(ExpenseGroup group, User user, String message) {
        if (!group.hasMember(user)) {
            throw new BusinessRuleException(message);
        }
    }
}
