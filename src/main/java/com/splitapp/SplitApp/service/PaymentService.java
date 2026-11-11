package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.CreatePaymentRequest;
import com.splitapp.SplitApp.dto.PaymentResponse;
import com.splitapp.SplitApp.entity.ExpenseGroup;
import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.GroupRepository;
import com.splitapp.SplitApp.repository.PaymentRepository;
import com.splitapp.SplitApp.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Validated
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    @Transactional
    public PaymentResponse createPayment(@NotNull Long groupId,
                                         @NotNull @Valid CreatePaymentRequest request) {
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
        Payment payment = new Payment(group, sender, receiver, amount);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    private static void requireMember(ExpenseGroup group, User user, String message) {
        if (!group.hasMember(user)) {
            throw new BusinessRuleException(message);
        }
    }
}
