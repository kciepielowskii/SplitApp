package com.splitapp.splitapp.service;

import com.splitapp.splitapp.dto.GroupBalancesResponse;
import com.splitapp.splitapp.dto.MemberBalanceResponse;
import com.splitapp.splitapp.entity.Expense;
import com.splitapp.splitapp.entity.ExpenseGroup;
import com.splitapp.splitapp.entity.Payment;
import com.splitapp.splitapp.entity.PaymentStatus;
import com.splitapp.splitapp.exception.GroupNotFoundException;
import com.splitapp.splitapp.repository.ExpenseRepository;
import com.splitapp.splitapp.repository.GroupRepository;
import com.splitapp.splitapp.repository.PaymentRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Isolation;

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
    private final GroupAccess groupAccess;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GroupBalancesResponse getBalances(@NotNull Long groupId, @NotNull Long currentUserId) {
        groupAccess.requireMember(groupId, currentUserId);
        ExpenseGroup group = groupRepository.findWithMembersById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));
        List<Expense> expenses = expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(groupId);
        List<Payment> payments =
                paymentRepository.findAllByGroupIdAndStatus(groupId, PaymentStatus.CONFIRMED);

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
