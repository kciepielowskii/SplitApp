package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByGroupId(Long groupId);

    List<Payment> findAllBySenderId(Long senderId);

    List<Payment> findAllByReceiverId(Long receiverId);

    List<Payment> findAllByGroupIdOrderByPaidAtDescIdDesc(Long groupId);

    Optional<Payment> findByIdAndGroupId(Long id, Long groupId);

    List<Payment> findAllByGroupIdAndStatus(Long groupId, PaymentStatus status);
}
