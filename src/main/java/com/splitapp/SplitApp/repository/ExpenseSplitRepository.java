package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {
    List<ExpenseSplit> findAllByExpenseId(Long expenseId);

    List<ExpenseSplit> findAllByUserId(Long userId);
}
