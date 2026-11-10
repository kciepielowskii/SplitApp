package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByGroupId(Long groupId);

    List<Expense> findAllByPaidById(Long userId);
}
