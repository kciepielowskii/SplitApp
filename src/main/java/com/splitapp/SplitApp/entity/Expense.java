package com.splitapp.SplitApp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by_id", nullable = false)
    private User paidBy;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseSplit> splits = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void createOn() {
        createdAt = LocalDateTime.now();
    }

    public Expense(BigDecimal amount, ExpenseCategory category, String description,
                   Group group, User paidBy) {
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.group = group;
        this.paidBy = paidBy;
    }

    public void addSplit(ExpenseSplit split) {
        if (split == null) {
            throw new IllegalArgumentException("Split cannot be null");
        }

        splits.add(split);
        split.setExpense(this);
    }
}
