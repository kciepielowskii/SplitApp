package com.splitapp.SplitApp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull(message = "The amount is required")
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "The category is required")
    private ExpenseCategory category;

    @Size(max = 255)
    private String description;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne
    @JoinColumn(name = "paid_by_id", nullable = false)
    private User paidBy;

    @OneToMany(mappedBy = "expense")
    private List<ExpenseSplit> splits = new ArrayList<>();

    public void addSplit(ExpenseSplit split) {
        if (split == null) {
            throw new IllegalArgumentException("Split cannot be null");
        }

        splits.add(split);
        split.setExpense(this);
    }

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdOn;

    @PrePersist
    public void createOn() {
        createdOn = LocalDateTime.now();
    }
}
