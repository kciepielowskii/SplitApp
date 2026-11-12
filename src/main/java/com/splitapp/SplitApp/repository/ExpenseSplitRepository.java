package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {
}
