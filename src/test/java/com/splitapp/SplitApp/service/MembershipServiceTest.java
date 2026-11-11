package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.Expense;
import com.splitapp.SplitApp.entity.ExpenseCategory;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.ExpenseSplit;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.PaymentStatus;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.repository.ExpenseRepository;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.PaymentRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {
    private static final Long GROUP_ID = 10L;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private GroupAccess groupAccess;

    @InjectMocks
    private MembershipService membershipService;

    @Test
    void shouldTransferOwnership() {
        ExpenseGroup group = groupWithOwner(user(1L), user(2L));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupResponse result = membershipService.transferOwnership(GROUP_ID, 1L, 2L);

        verify(groupAccess).requireMemberAndLock(GROUP_ID, 1L);
        verify(groupRepository).flush();
        assertEquals(GroupRole.MEMBER, result.members().get(0).role());
        assertEquals(GroupRole.OWNER, result.members().get(1).role());
    }

    @Test
    void shouldRejectTransferWhenCallerIsNotOwner() {
        ExpenseGroup group = groupWithOwner(user(1L), user(2L), user(3L));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> membershipService.transferOwnership(GROUP_ID, 2L, 3L));

        assertEquals("Only the group owner can transfer ownership", ex.getMessage());
        assertTrue(group.isOwner(1L));
    }

    @Test
    void shouldRejectTransferToYourself() {
        ExpenseGroup group = groupWithOwner(user(1L), user(2L));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipService.transferOwnership(GROUP_ID, 1L, 1L));

        assertEquals("You are already the owner of this group", ex.getMessage());
    }

    @Test
    void shouldRejectTransferToNonMember() {
        ExpenseGroup group = groupWithOwner(user(1L), user(2L));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipService.transferOwnership(GROUP_ID, 1L, 9L));

        assertEquals("The new owner must be a member of this group", ex.getMessage());
        assertTrue(group.isOwner(1L));
    }

    @Test
    void shouldThrowWhenTransferringInUnknownGroup() {
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> membershipService.transferOwnership(GROUP_ID, 1L, 2L));
    }

    @Test
    void shouldNotTransferWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMemberAndLock(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class,
                () -> membershipService.transferOwnership(GROUP_ID, 9L, 2L));

        verifyNoInteractions(groupRepository);
    }

    @Test
    void shouldLetMemberWithZeroBalanceLeave() {
        ExpenseGroup group = groupWithOwner(user(1L), user(2L));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));
        when(paymentRepository.existsInvolvingUser(GROUP_ID, 2L, PaymentStatus.PENDING))
                .thenReturn(false);
        when(expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(GROUP_ID))
                .thenReturn(List.of());
        when(paymentRepository.findAllByGroupIdAndStatus(GROUP_ID, PaymentStatus.CONFIRMED))
                .thenReturn(List.of());

        membershipService.leaveGroup(GROUP_ID, 2L);

        verify(groupAccess).requireMemberAndLock(GROUP_ID, 2L);
        assertFalse(group.isMember(2L));
        assertTrue(group.isMember(1L));
    }

    @Test
    void shouldLetMemberLeaveAfterSettlingUp() {
        User owner = user(1L);
        User member = user(2L);
        ExpenseGroup group = groupWithOwner(owner, member);
        Expense expense = new Expense(new BigDecimal("10.00"), ExpenseCategory.FOOD, "Obiad",
                group, owner, owner);
        expense.addSplit(new ExpenseSplit(owner, new BigDecimal("5.00")));
        expense.addSplit(new ExpenseSplit(member, new BigDecimal("5.00")));
        Payment payment = new Payment(group, member, owner, new BigDecimal("5.00"), owner);
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));
        when(paymentRepository.existsInvolvingUser(GROUP_ID, 2L, PaymentStatus.PENDING))
                .thenReturn(false);
        when(expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(GROUP_ID))
                .thenReturn(List.of(expense));
        when(paymentRepository.findAllByGroupIdAndStatus(GROUP_ID, PaymentStatus.CONFIRMED))
                .thenReturn(List.of(payment));

        membershipService.leaveGroup(GROUP_ID, 2L);

        assertFalse(group.isMember(2L));
    }

    @Test
    void shouldRejectLeavingWithOutstandingBalance() {
        User owner = user(1L);
        User member = user(2L);
        ExpenseGroup group = groupWithOwner(owner, member);
        Expense expense = new Expense(new BigDecimal("10.00"), ExpenseCategory.FOOD, "Obiad",
                group, owner, owner);
        expense.addSplit(new ExpenseSplit(owner, new BigDecimal("5.00")));
        expense.addSplit(new ExpenseSplit(member, new BigDecimal("5.00")));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));
        when(paymentRepository.existsInvolvingUser(GROUP_ID, 2L, PaymentStatus.PENDING))
                .thenReturn(false);
        when(expenseRepository.findAllByGroupIdOrderByCreatedAtDescIdDesc(GROUP_ID))
                .thenReturn(List.of(expense));
        when(paymentRepository.findAllByGroupIdAndStatus(GROUP_ID, PaymentStatus.CONFIRMED))
                .thenReturn(List.of());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipService.leaveGroup(GROUP_ID, 2L));

        assertEquals("Settle your balance before leaving the group", ex.getMessage());
        assertTrue(group.isMember(2L));
    }

    @Test
    void shouldRejectLeavingWithPendingPayments() {
        ExpenseGroup group = groupWithOwner(user(1L), user(2L));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));
        when(paymentRepository.existsInvolvingUser(GROUP_ID, 2L, PaymentStatus.PENDING))
                .thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipService.leaveGroup(GROUP_ID, 2L));

        assertEquals("You have pending payments in this group; "
                + "wait until they are confirmed or delete them", ex.getMessage());
        assertTrue(group.isMember(2L));
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void shouldNotLetTheOwnerLeave() {
        ExpenseGroup group = groupWithOwner(user(1L), user(2L));
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.of(group));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> membershipService.leaveGroup(GROUP_ID, 1L));

        assertEquals("The group owner cannot leave; "
                + "transfer ownership to another member first", ex.getMessage());
        assertTrue(group.isMember(1L));
        verifyNoInteractions(paymentRepository, expenseRepository);
    }

    @Test
    void shouldThrowWhenLeavingUnknownGroup() {
        when(groupRepository.findWithMembersById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> membershipService.leaveGroup(GROUP_ID, 2L));
    }

    @Test
    void shouldNotLeaveWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMemberAndLock(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class,
                () -> membershipService.leaveGroup(GROUP_ID, 9L));

        verifyNoInteractions(groupRepository, expenseRepository, paymentRepository);
    }

    private static User user(Long id) {
        User user = new User("Jan", "Kowalski", "jan" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static ExpenseGroup groupWithOwner(User owner, User... others) {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", owner);
        group.addMember(owner, GroupRole.OWNER);
        for (User other : others) {
            group.addMember(other, GroupRole.MEMBER);
        }
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        return group;
    }
}
