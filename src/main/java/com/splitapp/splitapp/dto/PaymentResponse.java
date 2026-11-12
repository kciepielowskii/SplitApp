package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.Payment;
import com.splitapp.splitapp.entity.PaymentStatus;

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
