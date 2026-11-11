package com.splitapp.SplitApp.dto;

import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long groupId,
        Long senderId,
        Long receiverId,
        Long createdById,
        BigDecimal amount,
        PaymentStatus status,
        Instant paidAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getGroup().getId(),
                payment.getSender().getId(),
                payment.getReceiver().getId(),
                payment.getCreatedBy().getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt());
    }
}
