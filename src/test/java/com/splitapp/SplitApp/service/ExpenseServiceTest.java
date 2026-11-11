package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.CreateExpenseRequest;
import com.splitapp.SplitApp.dto.ExpenseResponse;
import com.splitapp.SplitApp.dto.ExpenseSplitResponse;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseCategory;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.ExpenseRepository;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {
    private static final Long GROUP_ID = 10L;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    void shouldCreateExpenseWithEqualSplits() {
        User u1 = user(1L);
        User u2 = user(2L);
        User u3 = user(3L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1, u2, u3)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(u1, u2, u3));
        when(expenseRepository.save(any(Expense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponse result = expenseService.createExpense(GROUP_ID,
                request("10.00", 1L, List.of(1L, 2L, 3L)));

        assertEquals(GROUP_ID, result.groupId());
        assertEquals(new BigDecimal("10.00"), result.amount());
        assertEquals(1L, result.paidById());
        assertEquals(List.of(
                new ExpenseSplitResponse(1L, new BigDecimal("3.34")),
                new ExpenseSplitResponse(2L, new BigDecimal("3.33")),
                new ExpenseSplitResponse(3L, new BigDecimal("3.33"))), result.splits());
    }

    @Test
    void shouldThrowWhenGroupDoesNotExist() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> expenseService.createExpense(GROUP_ID, request("10.00", 1L, List.of(1L))));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void shouldRejectDuplicateParticipants() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(user(1L))));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, request("10.00", 1L, List.of(1L, 1L))));

        assertEquals("Participants must be unique", ex.getMessage());
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void shouldRejectPayerWhoIsNotGroupMember() {
        User payer = user(1L);
        User member = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(member)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(payer));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, request("10.00", 1L, List.of(2L))));

        assertEquals("Payer is not a member of this group", ex.getMessage());
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void shouldRejectParticipantWhoIsNotGroupMember() {
        User u1 = user(1L);
        User outsider = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(u1, outsider));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, request("10.00", 1L, List.of(1L, 2L))));

        assertEquals("Participant is not a member of this group", ex.getMessage());
        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void shouldThrowWhenParticipantDoesNotExist() {
        User u1 = user(1L);
        User u2 = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1, u2)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(u1));

        assertThrows(UserNotFoundException.class,
                () -> expenseService.createExpense(GROUP_ID, request("10.00", 1L, List.of(1L, 2L))));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void shouldRejectAmountTooSmallToSplit() {
        User u1 = user(1L);
        User u2 = user(2L);
        User u3 = user(3L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1, u2, u3)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(u1, u2, u3));

        assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, request("0.02", 1L, List.of(1L, 2L, 3L))));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    private static CreateExpenseRequest request(String amount, Long paidById, List<Long> participantIds) {
        return new CreateExpenseRequest(new BigDecimal(amount), ExpenseCategory.FOOD,
                "Obiad", paidById, participantIds);
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
