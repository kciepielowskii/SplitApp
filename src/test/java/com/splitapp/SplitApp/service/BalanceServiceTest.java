package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupBalancesResponse;
import com.splitapp.SplitApp.dto.MemberBalanceResponse;
import com.splitapp.SplitApp.dto.SettlementTransferResponse;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseCategory;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.ExpenseSplit;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.repository.ExpenseRepository;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.PaymentRepository;
import com.splitapp.SplitApp.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long CURRENT_USER_ID = 1L;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private GroupAccess groupAccess;

    @InjectMocks
    private BalanceService balanceService;

    @Test
    void shouldCombineExpensesAndPaymentsIntoBalances() {
        User u1 = user(1L);
        User u2 = user(2L);
        User u3 = user(3L);
        ExpenseGroup group = group(u1, u2, u3);
        Expense expense = new Expense(new BigDecimal("30.00"), ExpenseCategory.FOOD, "Obiad", group, u1, u1);
        expense.addSplit(new ExpenseSplit(u1, new BigDecimal("10.00")));
        expense.addSplit(new ExpenseSplit(u2, new BigDecimal("10.00")));
        expense.addSplit(new ExpenseSplit(u3, new BigDecimal("10.00")));
        Payment payment = new Payment(group, u2, u1, new BigDecimal("10.00"), u1);
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));
        when(expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(GROUP_ID))
                .thenReturn(List.of(expense));
        when(paymentRepository.findAllByGroupIdAndStatus(GROUP_ID, PaymentStatus.CONFIRMED))
                .thenReturn(List.of(payment));

        GroupBalancesResponse result = balanceService.getBalances(GROUP_ID, CURRENT_USER_ID);

        verify(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);
        Map<Long, BigDecimal> byUser = result.balances().stream()
                .collect(Collectors.toMap(MemberBalanceResponse::userId, MemberBalanceResponse::balance));
        assertEquals(new BigDecimal("10.00"), byUser.get(1L));
        assertEquals(new BigDecimal("0.00"), byUser.get(2L));
        assertEquals(new BigDecimal("-10.00"), byUser.get(3L));
        assertEquals(List.of(new SettlementTransferResponse(3L, 1L, new BigDecimal("10.00"))),
                result.transfers());
    }

    @Test
    void shouldThrowWhenGroupDoesNotExist() {
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> balanceService.getBalances(GROUP_ID, CURRENT_USER_ID));
    }

    @Test
    void shouldNotReturnBalancesWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMember(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class, () -> balanceService.getBalances(GROUP_ID, 9L));

        verifyNoInteractions(groupRepository, expenseRepository, paymentRepository);
    }

    private static User user(Long id) {
        User user = new User("Jan", "Kowalski", "jan" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static ExpenseGroup group(User... members) {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", members[0]);
        for (User member : members) {
            group.addMember(member, GroupRole.MEMBER);
        }
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        return group;
    }
}
