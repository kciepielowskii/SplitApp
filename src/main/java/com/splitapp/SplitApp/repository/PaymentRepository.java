package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.Payment;
import com.splitapp.SplitApp.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByGroupIdOrderByPaidAtDescIdDesc(Long groupId);

    Optional<Payment> findByIdAndGroupId(Long id, Long groupId);

    List<Payment> findAllByGroupIdAndStatus(Long groupId, PaymentStatus status);

    @Query("""
            select count(p) > 0 from Payment p
            where p.group.id = :groupId and p.status = :status
              and (p.sender.id = :userId or p.receiver.id = :userId)
            """)
    boolean existsInvolvingUser(@Param("groupId") Long groupId, @Param("userId") Long userId,
                                @Param("status") PaymentStatus status);
}
