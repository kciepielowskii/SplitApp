package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByGroupId(Long groupId);

    List<Expense> findAllByPaidById(Long userId);

    @EntityGraph(attributePaths = "splits")
    List<Expense> findAllByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);

    Optional<Expense> findByIdAndGroupId(Long id, Long groupId);
}
