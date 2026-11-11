package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.CreatePaymentRequest;
import com.splitapp.SplitApp.dto.PaymentResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.PaymentStatus;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.PaymentNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.PaymentRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupAccess groupAccess;

    @Transactional
    public PaymentResponse createPayment(@NotNull Long groupId, @NotNull Long currentUserId,
                                         @NotNull @Valid CreatePaymentRequest request) {
        groupAccess.requireMember(groupId, currentUserId);

        if (!currentUserId.equals(request.senderId())
                && !currentUserId.equals(request.receiverId())) {
            throw new GroupAccessDeniedException(
                    "You can only record payments you are part of");
        }

        ExpenseGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found"));

        if (request.senderId().equals(request.receiverId())) {
            throw new BusinessRuleException("Sender and receiver must differ");
        }

        User sender = userRepository.findById(request.senderId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        User receiver = userRepository.findById(request.receiverId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        requireMember(group, sender, "Sender is not a member of this group");
        requireMember(group, receiver, "Receiver is not a member of this group");

        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        User createdBy = currentUserId.equals(sender.getId()) ? sender : receiver;
        Payment payment = new Payment(group, sender, receiver, amount, createdBy);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    private static void requireMember(ExpenseGroup group, User user, String message) {
        if (!group.hasMember(user)) {
            throw new BusinessRuleException(message);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments(@NotNull Long groupId, @NotNull Long currentUserId) {
        groupAccess.requireMember(groupId, currentUserId);
        return paymentRepository.findAllByGroupIdOrderByPaidAtDescIdDesc(groupId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Transactional
    public void deletePayment(@NotNull Long groupId, @NotNull Long currentUserId,
                              @NotNull Long paymentId) {
        groupAccess.requireMember(groupId, currentUserId);
        Payment payment = paymentRepository.findByIdAndGroupId(paymentId, groupId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));

        boolean isAuthor = payment.getCreatedBy().getId().equals(currentUserId);
        if (!isAuthor && !payment.getGroup().isOwner(currentUserId)) {
            throw new GroupAccessDeniedException(
                    "Only the author or the group owner can delete this payment");
        }

        paymentRepository.delete(payment);
    }

    @Transactional
    public PaymentResponse confirmPayment(@NotNull Long groupId, @NotNull Long currentUserId,
                                          @NotNull Long paymentId) {
        groupAccess.requireMember(groupId, currentUserId);
        Payment payment = paymentRepository.findByIdAndGroupId(paymentId, groupId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found"));

        if (!payment.getReceiver().getId().equals(currentUserId)) {
            throw new GroupAccessDeniedException("Only the receiver can confirm this payment");
        }
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            throw new BusinessRuleException("Payment is already confirmed");
        }

        payment.confirm();
        return PaymentResponse.from(payment);
    }
}
