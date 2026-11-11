package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<ExpenseGroup> findWithMembersById(Long id);

    boolean existsByIdAndMembersUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ExpenseGroup g where g.id = :id")
    Optional<ExpenseGroup> findByIdForUpdate(@Param("id") Long id);
}
