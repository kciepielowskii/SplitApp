package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.CreateExpenseRequest;
import com.splitapp.SplitApp.dto.ExpenseResponse;
import com.splitapp.SplitApp.dto.ExpenseSplitResponse;
import com.splitapp.SplitApp.dto.ShareRequest;
import com.splitapp.SplitApp.dto.SplitType;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseCategory;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.ExpenseSplit;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long CURRENT_USER_ID = 1L;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccess groupAccess;

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

        ExpenseResponse result = expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                request("10.00", 1L, List.of(1L, 2L, 3L)));

        verify(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);
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
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        request("10.00", 1L, List.of(1L))));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void shouldRejectDuplicateParticipants() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(user(1L))));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        request("10.00", 1L, List.of(1L, 1L))));

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
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        request("10.00", 1L, List.of(2L))));

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
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        request("10.00", 1L, List.of(1L, 2L))));

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
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        request("10.00", 1L, List.of(1L, 2L))));

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
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        request("0.02", 1L, List.of(1L, 2L, 3L))));

        verify(expenseRepository, never()).save(any(Expense.class));
    }

    @Test
    void shouldReportMissingParticipantBeforeAmountProblems() {
        User u1 = user(1L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(u1));

        assertThrows(UserNotFoundException.class,
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        request("0.01", 1L, List.of(1L, 2L))));
    }

    @Test
    void shouldReturnGroupExpenses() {
        User u1 = user(1L);
        ExpenseGroup group = group(u1);
        Expense expense = new Expense(new BigDecimal("10.00"), ExpenseCategory.FOOD, "Obiad", group, u1);
        expense.addSplit(new ExpenseSplit(u1, new BigDecimal("10.00")));
        when(expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(GROUP_ID))
                .thenReturn(List.of(expense));

        List<ExpenseResponse> result = expenseService.getExpenses(GROUP_ID, CURRENT_USER_ID);

        verify(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).paidById());
        assertEquals(1, result.get(0).splits().size());
    }

    @Test
    void shouldThrowWhenListingExpensesOfUnknownGroup() {
        doThrow(new GroupNotFoundException("Group not found"))
                .when(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);

        assertThrows(GroupNotFoundException.class,
                () -> expenseService.getExpenses(GROUP_ID, CURRENT_USER_ID));

        verify(expenseRepository, never()).findAllByGroupIdOrderByCreatedAtDescIdDesc(any());
    }

    @Test
    void shouldStoreExplicitAmounts() {
        User u1 = user(1L);
        User u2 = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1, u2)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(u1, u2));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse result = expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                sharesRequest("10.00", 1L, SplitType.AMOUNT,
                        new ShareRequest(1L, new BigDecimal("3.00")),
                        new ShareRequest(2L, new BigDecimal("7.00"))));

        assertEquals(new BigDecimal("3.00"), result.splits().get(0).amount());
        assertEquals(new BigDecimal("7.00"), result.splits().get(1).amount());
    }

    @Test
    void shouldSplitByPercentages() {
        User u1 = user(1L);
        User u2 = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1, u2)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(u1, u2));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse result = expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                sharesRequest("10.00", 1L, SplitType.PERCENT,
                        new ShareRequest(1L, new BigDecimal("60")),
                        new ShareRequest(2L, new BigDecimal("40"))));

        assertEquals(new BigDecimal("6.00"), result.splits().get(0).amount());
        assertEquals(new BigDecimal("4.00"), result.splits().get(1).amount());
    }

    @Test
    void shouldRejectAmountsThatDoNotAddUpToTotal() {
        User u1 = user(1L);
        User u2 = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1, u2)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(u1, u2));

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        sharesRequest("10.00", 1L, SplitType.AMOUNT,
                                new ShareRequest(1L, new BigDecimal("3.00")),
                                new ShareRequest(2L, new BigDecimal("6.00")))));

        assertEquals("Amounts must add up to the total amount", e.getMessage());
    }

    @Test
    void shouldRejectSharesForEqualSplit() {
        User u1 = user(1L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1)));
        CreateExpenseRequest request = new CreateExpenseRequest(new BigDecimal("10.00"),
                ExpenseCategory.FOOD, null, 1L, SplitType.EQUAL, List.of(1L),
                List.of(new ShareRequest(1L, new BigDecimal("10.00"))));

        assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID, request));
    }

    @Test
    void shouldRejectParticipantIdsForAmountSplit() {
        User u1 = user(1L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1)));
        CreateExpenseRequest request = new CreateExpenseRequest(new BigDecimal("10.00"),
                ExpenseCategory.FOOD, null, 1L, SplitType.AMOUNT, List.of(1L),
                List.of(new ShareRequest(1L, new BigDecimal("10.00"))));

        assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID, request));
    }

    @Test
    void shouldRejectExpenseWithoutParticipants() {
        User u1 = user(1L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1)));

        assertThrows(BusinessRuleException.class,
                () -> expenseService.createExpense(GROUP_ID, CURRENT_USER_ID,
                        request("10.00", 1L, List.of())));
    }

    @Test
    void shouldNotCreateExpenseWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMember(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class, () -> expenseService.createExpense(
                GROUP_ID, 9L, request("10.00", 1L, List.of(1L))));

        verifyNoInteractions(groupRepository, userRepository, expenseRepository);
    }

    @Test
    void shouldNotListExpensesWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMember(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class, () -> expenseService.getExpenses(GROUP_ID, 9L));

        verifyNoInteractions(expenseRepository);
    }

    private static CreateExpenseRequest request(String amount, Long paidById, List<Long> participantIds) {
        return new CreateExpenseRequest(new BigDecimal(amount), ExpenseCategory.FOOD, "Obiad",
                paidById, null, participantIds, null);
    }

    private static CreateExpenseRequest sharesRequest(String amount, Long paidById, SplitType type,
                                                      ShareRequest... shares) {
        return new CreateExpenseRequest(new BigDecimal(amount), ExpenseCategory.FOOD, "Obiad",
                paidById, type, null, List.of(shares));
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
