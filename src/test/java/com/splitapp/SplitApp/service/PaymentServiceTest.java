package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.CreatePaymentRequest;
import com.splitapp.SplitApp.dto.PaymentResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.PaymentNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.PaymentRepository;
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

        verify(groupAccess).requireMember(GROUP_ID, CURRENT_USER_ID);
        assertEquals(GROUP_ID, result.groupId());
        assertEquals(1L, result.senderId());
        assertEquals(2L, result.receiverId());
        assertEquals(1L, result.createdById());
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
                .when(groupAccess).requireMember(GROUP_ID, 9L);

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

        verify(groupAccess).requireMember(GROUP_ID, 2L);
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
    void shouldRejectDeletingPaymentWhenCallerIsOnlyTheReceiver() {
        User owner = user(1L);
        User sender = user(2L);
        User receiver = user(3L);
        Payment payment = paymentBy(sender, sender, receiver, groupWithOwner(owner, sender, receiver));
        when(paymentRepository.findByIdAndGroupId(7L, GROUP_ID)).thenReturn(Optional.of(payment));

        GroupAccessDeniedException ex = assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.deletePayment(GROUP_ID, 3L, 7L));

        assertEquals("Only the author or the group owner can delete this payment", ex.getMessage());
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
                .when(groupAccess).requireMember(GROUP_ID, 9L);

        assertThrows(GroupAccessDeniedException.class,
                () -> paymentService.deletePayment(GROUP_ID, 9L, 7L));

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
