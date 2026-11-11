package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.CreateExpenseRequest;
import com.splitapp.SplitApp.dto.ExpenseResponse;
import com.splitapp.SplitApp.dto.ShareRequest;
import com.splitapp.SplitApp.dto.SplitType;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.ExpenseSplit;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.ExpenseNotFoundException;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
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
    private final GroupAccess groupAccess;

    @Transactional
    public ExpenseResponse createExpense(@NotNull Long groupId, @NotNull Long currentUserId,
                                         @NotNull @Valid CreateExpenseRequest request) {
        groupAccess.requireMember(groupId, currentUserId);
        ExpenseGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        List<Long> participantIds = participantIds(request);

        User payer = userRepository.findById(request.paidById())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        requireMember(group, payer, "Payer is not a member of this group");

        Map<Long, User> participants = userRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (!participants.keySet().containsAll(participantIds)) {
            throw new UserNotFoundException("User not found");
        }

        List<BigDecimal> amounts = calculateAmounts(request, participantIds.size());

        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        User createdBy = userRepository.getReferenceById(currentUserId);
        Expense expense = new Expense(amount, request.category(), request.description(),
                group, payer, createdBy);
        for (int i = 0; i < participantIds.size(); i++) {
            User participant = participants.get(participantIds.get(i));
            requireMember(group, participant, "Participant is not a member of this group");
            expense.addSplit(new ExpenseSplit(participant, amounts.get(i)));
        }

        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    private static List<Long> participantIds(CreateExpenseRequest request) {
        boolean hasParticipants = request.participantIds() != null && !request.participantIds().isEmpty();
        boolean hasShares = request.shares() != null && !request.shares().isEmpty();

        List<Long> ids;
        if (request.splitType() == SplitType.EQUAL) {
            if (hasShares) {
                throw new BusinessRuleException("Shares are not allowed for an equal split");
            }
            ids = hasParticipants ? request.participantIds() : List.of();
        } else {
            if (hasParticipants) {
                throw new BusinessRuleException("Participant ids are only allowed for an equal split");
            }
            ids = hasShares ? request.shares().stream().map(ShareRequest::userId).toList() : List.of();
        }

        if (ids.isEmpty()) {
            throw new BusinessRuleException("At least one participant is required");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessRuleException("Participants must be unique");
        }
        return ids;
    }

    private static List<BigDecimal> calculateAmounts(CreateExpenseRequest request, int participants) {
        try {
            return switch (request.splitType()) {
                case EQUAL -> SplitCalculator.splitEqually(request.amount(), participants);
                case AMOUNT -> SplitCalculator.validateAmounts(request.amount(), shareValues(request));
                case PERCENT -> SplitCalculator.splitByPercentages(request.amount(), shareValues(request));
            };
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(e.getMessage());
        }
    }

    private static List<BigDecimal> shareValues(CreateExpenseRequest request) {
        return request.shares().stream().map(ShareRequest::value).toList();
    }

    private static void requireMember(ExpenseGroup group, User user, String message) {
        if (!group.hasMember(user)) {
            throw new BusinessRuleException(message);
        }
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(@NotNull Long groupId, @NotNull Long currentUserId) {
        groupAccess.requireMember(groupId, currentUserId);
        return expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(groupId).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    @Transactional
    public void deleteExpense(@NotNull Long groupId, @NotNull Long currentUserId,
                              @NotNull Long expenseId) {
        groupAccess.requireMember(groupId, currentUserId);
        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found"));

        boolean isAuthor = expense.getCreatedBy().getId().equals(currentUserId);
        if (!isAuthor && !expense.getGroup().isOwner(currentUserId)) {
            throw new GroupAccessDeniedException(
                    "Only the author or the group owner can delete this expense");
        }
        if (involvesFormerMember(expense)) {
            throw new BusinessRuleException(
                    "Cannot delete an expense that involves a user who has left the group");
        }

        expenseRepository.delete(expense);
    }

    private static boolean involvesFormerMember(Expense expense) {
        ExpenseGroup group = expense.getGroup();
        return !group.hasMember(expense.getPaidBy())
                || expense.getSplits().stream()
                .anyMatch(split -> !group.hasMember(split.getUser()));
    }
}
