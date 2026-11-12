package com.splitapp.splitapp.service;

import com.splitapp.splitapp.dto.CreatePaymentRequest;
import com.splitapp.splitapp.dto.PaymentResponse;
import com.splitapp.splitapp.entity.ExpenseGroup;
import com.splitapp.splitapp.entity.GroupRole;
import com.splitapp.splitapp.entity.Payment;
import com.splitapp.splitapp.entity.PaymentStatus;
import com.splitapp.splitapp.entity.User;
import com.splitapp.splitapp.exception.BusinessRuleException;
import com.splitapp.splitapp.exception.GroupAccessDeniedException;
import com.splitapp.splitapp.exception.GroupNotFoundException;
import com.splitapp.splitapp.exception.PaymentNotFoundException;
import com.splitapp.splitapp.exception.UserNotFoundException;
import com.splitapp.splitapp.repository.GroupRepository;
import com.splitapp.splitapp.repository.PaymentRepository;
import com.splitapp.splitapp.repository.UserRepository;
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
class PaymentServiceTest {
    private static final Long GROUP_ID = 10L;
    private static final Long CURRENT_USER_ID = 1L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupAccess groupAccess;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void shouldCreatePayment() {
        User u1 = user(1L);
        User u2 = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1, u2)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(u2));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse result = paymentService.createPayment(GROUP_ID, CURRENT_USER_ID,
                request(1L, 2L, "10"));

        verify(groupAccess).requireMemberAndLock(GROUP_ID, CURRENT_USER_ID);
        assertEquals(GROUP_ID, result.groupId());
        assertEquals(1L, result.senderId());
        assertEquals(2L, result.receiverId());
        assertEquals(1L, result.createdById());
        assertEquals(PaymentStatus.PENDING, result.status());
        assertEquals(new BigDecimal("10.00"), result.amount());
    }

    @Test
    void shouldAllowReceiverToRecordPayment() {
        User u1 = user(1L);
        User u2 = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(u1, u2)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(u2));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse result = paymentService.createPayment(GROUP_ID, 2L,
                request(1L, 2L, "10"));

        assertEquals(1L, result.senderId());
        assertEquals(2L, result.receiverId());
        assertEquals(2L, result.createdById());
        assertEquals(PaymentStatus.CONFIRMED, result.status());
    }

    @Test
    void shouldRejectPaymentWhenCallerIsNeitherSenderNorReceiver() {
        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.createPayment(GROUP_ID, 3L, request(1L, 2L, "10.00")));

        assertEquals("You can only record payments you are part of", ex.getMessage());
        verifyNoInteractions(groupRepository, userRepository, paymentRepository);
    }

    @Test
    void shouldThrowWhenGroupDoesNotExist() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(GroupNotFoundException.class,
                () -> paymentService.createPayment(GROUP_ID, CURRENT_USER_ID,
                        request(1L, 2L, "10.00")));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldRejectSameSenderAndReceiver() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(user(1L))));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> paymentService.createPayment(GROUP_ID, CURRENT_USER_ID,
                        request(1L, 1L, "10.00")));

        assertEquals("Sender and receiver must differ", ex.getMessage());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldThrowWhenSenderDoesNotExist() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(user(2L))));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> paymentService.createPayment(GROUP_ID, CURRENT_USER_ID,
                        request(1L, 2L, "10.00")));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldRejectSenderWhoIsNotGroupMember() {
        User sender = user(1L);
        User receiver = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(receiver)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> paymentService.createPayment(GROUP_ID, CURRENT_USER_ID,
                        request(1L, 2L, "10.00")));

        assertEquals("Sender is not a member of this group", ex.getMessage());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldRejectReceiverWhoIsNotGroupMember() {
        User sender = user(1L);
        User receiver = user(2L);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(sender)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> paymentService.createPayment(GROUP_ID, CURRENT_USER_ID,
                        request(1L, 2L, "10.00")));

        assertEquals("Receiver is not a member of this group", ex.getMessage());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldReturnGroupPayments() {
        User u1 = user(1L);
        User u2 = user(2L);
        Payment payment = new Payment(group(u1, u2), u1, u2, new BigDecimal("10.00"), u1);
        when(paymentRepository.findAllByGroupIdOrderByPaidAtDescIdDesc(GROUP_ID))
                .thenReturn(List.of(payment));

        List<PaymentResponse> result = paymentService.getPayments(GROUP_ID, CURRENT_USER_ID);

        verify(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).senderId());
        assertEquals(2L, result.get(0).receiverId());
        assertEquals(1L, result.get(0).createdById());
    }

    @Test
    void shouldThrowWhenListingPaymentsOfUnknownGroup() {
        doThrow(new GroupNotFoundException("Group not found"))
                .when(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);

        assertThrows(GroupNotFoundException.class,
                () -> paymentService.getPayments(GROUP_ID, CURRENT_USER_ID));

        verify(paymentRepository, never()).findAllByGroupIdOrderByPaidAtDescIdDesc(any());
    }

    @Test
    void shouldNotCreatePaymentWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMemberAndLock(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class, () -> paymentService.createPayment(
                GROUP_ID, 9L, request(1L, 2L, "10.00")));

        verifyNoInteractions(groupRepository, userRepository, paymentRepository);
    }

    @Test
    void shouldNotListPaymentsWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMember(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class, () -> paymentService.getPayments(GROUP_ID, 9L));

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldDeletePaymentWhenCallerIsAuthor() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(sender, sender, receiver, groupWithOwner(owner, sender, receiver));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        paymentService.deletePayment(GROUP_ID, 2L, 7L);

        verify(groupAccess).requireMemberAndLock(GROUP_ID, 2L);
        verify(paymentRepository).delete(payment);
    }

    @Test
    void shouldDeletePaymentWhenCallerIsGroupOwner() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(sender, sender, receiver, groupWithOwner(owner, sender, receiver));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        paymentService.deletePayment(GROUP_ID, 1L, 7L);

        verify(paymentRepository).delete(payment);
    }

    @Test
    void shouldLetTheReceiverRejectAPendingPayment() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(sender, sender, receiver, groupWithOwner(owner, sender, receiver));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        paymentService.deletePayment(GROUP_ID, 3L, 7L);

        verify(paymentRepository).delete(payment);
    }

    @Test
    void shouldRejectDeletingAConfirmedPaymentWhenCallerIsOnlyTheReceiver() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(sender, sender, receiver, groupWithOwner(owner, sender, receiver));
        payment.confirm();
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.deletePayment(GROUP_ID, 3L, 7L));

        assertEquals("Only the author, the receiver of a pending payment "
                + "or the group owner can delete this payment", ex.getMessage());
        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldRejectDeletingPaymentWhenCallerIsOnlyTheSenderOfAPaymentTheReceiverRecorded() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(receiver, sender, receiver, groupWithOwner(owner, sender, receiver));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.deletePayment(GROUP_ID, 2L, 7L));

        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldRejectDeletingAPendingPaymentWhenCallerIsNotInvolved() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        User bystander = user(4L);
        Payment payment = paymentBy(sender, sender, receiver,
                groupWithOwner(owner, sender, receiver, bystander));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.deletePayment(GROUP_ID, 4L, 7L));

        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldRejectDeletingPaymentWhenSenderHasLeft() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        ExpenseGroup group = groupWithOwner(owner, sender, receiver);
        Payment payment = paymentBy(receiver, sender, receiver, group);
        group.removeMember(2L);
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> paymentService.deletePayment(GROUP_ID, 3L, 7L));

        assertEquals("Cannot delete a payment that involves a user who has left the group",
                ex.getMessage());
        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldRejectDeletingPaymentWhenReceiverHasLeft() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        ExpenseGroup group = groupWithOwner(owner, sender, receiver);
        Payment payment = paymentBy(sender, sender, receiver, group);
        group.removeMember(3L);
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        assertThrows(BusinessRuleException.class,
                () -> paymentService.deletePayment(GROUP_ID, 1L, 7L));

        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldReportMissingPermissionBeforeFormerMemberProblemOnPayment() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        User other = user(4L);
        ExpenseGroup group = groupWithOwner(owner, sender, receiver, other);
        Payment payment = paymentBy(sender, sender, receiver, group);
        group.removeMember(2L);
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.deletePayment(GROUP_ID, 4L, 7L));

        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldThrowWhenDeletingUnknownPayment() {
        when(paymentRepository.findByIdAndGroupId(99L, GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.deletePayment(GROUP_ID, CURRENT_USER_ID, 99L));

        verify(paymentRepository, never()).delete(any(Payment.class));
    }

    @Test
    void shouldNotDeletePaymentWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMemberAndLock(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.deletePayment(GROUP_ID, 9L, 7L));

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldConfirmPaymentWhenCallerIsReceiver() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(sender, sender, receiver, groupWithOwner(owner, sender, receiver));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.confirmPayment(GROUP_ID, 3L, 7L);

        verify(groupAccess).requireMemberAndLock(GROUP_ID, 3L);
        assertEquals(PaymentStatus.CONFIRMED, result.status());
        assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
    }

    @Test
    void shouldRejectConfirmingWhenCallerIsNotTheReceiver() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(sender, sender, receiver, groupWithOwner(owner, sender, receiver));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.confirmPayment(GROUP_ID, 2L, 7L));

        assertEquals("Only the receiver can confirm this payment", ex.getMessage());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void shouldRejectConfirmingAnAlreadyConfirmedPayment() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(receiver, sender, receiver, groupWithOwner(owner, sender, receiver));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> paymentService.confirmPayment(GROUP_ID, 3L, 7L));

        assertEquals("Payment is already confirmed", ex.getMessage());
    }

    @Test
    void shouldThrowWhenConfirmingUnknownPayment() {
        when(paymentRepository.findByIdAndGroupId(99L, GROUP_ID)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.confirmPayment(GROUP_ID, CURRENT_USER_ID, 99L));
    }

    @Test
    void shouldNotConfirmPaymentWhenCallerIsNotMember() {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(groupAccess).requireMemberAndLock(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.confirmPayment(GROUP_ID, 9L, 7L));

        verifyNoInteractions(paymentRepository);
    }

    private static CreatePaymentRequest request(Long senderId, Long receiverId, String amount) {
        return new CreatePaymentRequest(senderId, receiverId, new BigDecimal(amount));
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

    private static ExpenseGroup groupWithOwner(User owner, User... others) {
        ExpenseGroup group = new ExpenseGroup("Wyjazd", owner);
        group.addMember(owner, GroupRole.OWNER);
        for (User other : others) {
            group.addMember(other, GroupRole.MEMBER);
        }
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        return group;
    }

    private static Payment paymentBy(User author, User sender, User receiver, ExpenseGroup group) {
        return new Payment(group, sender, receiver, new BigDecimal("10.00"), author);
    }
}
