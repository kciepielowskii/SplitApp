package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupBalancesResponse;
import com.splitapp.SplitApp.dto.MemberBalanceResponse;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.repository.ExpenseRepository;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.PaymentRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@Validated
@RequiredArgsConstructor
public class BalanceService {
    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public GroupBalancesResponse getBalances(@NotNull Long groupId) {
        ExpenseGroup group = groupRepository.findWithMembersById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        List<Expense> expenses = expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(groupId);
        List<Payment> payments = paymentRepository.findAllByGroupIdOrderByPaidAtDescIdDesc(groupId);

        Map<Long, Long> net = BalanceCalculator.netCents(expenses, payments);

        List<MemberBalanceResponse> balances = group.getMembers().stream()
                .map(member -> new MemberBalanceResponse(
                        member.getUser().getId(),
                        member.getUser().getFirstName(),
                        member.getUser().getLastName(),
                        BigDecimal.valueOf(net.getOrDefault(member.getUser().getId(), 0L), 2)))
                .toList();

        return new GroupBalancesResponse(groupId, balances, BalanceCalculator.settle(net));
    }
}
