package com.splitapp.SplitApp.repository;

import com.splitapp.SplitApp.entity.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {
}
