package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findAllByGroupId(Long groupId);

    List<Payment> findAllBySenderId(Long senderId);

    List<Payment> findAllByReceiverId(Long receiverId);
}
