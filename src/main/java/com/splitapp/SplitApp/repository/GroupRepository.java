package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<ExpenseGroup> findWithMembersById(Long id);

    boolean existsByIdAndMembersUserId(Long id, Long userId);
}
