package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {
    List<ExpenseSplit> findAllByExpense_Id(Long expenseId);

    List<ExpenseSplit> findAllByUserId(Long userId);
}
