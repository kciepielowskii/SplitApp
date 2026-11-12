package com.splitapp.splitapp.repository;

import com.splitapp.splitapp.entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = "splits")
    List<Expense> findAllByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);

    Optional<Expense> findByIdAndGroupId(Long id, Long groupId);
}
