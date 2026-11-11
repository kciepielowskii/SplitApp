package com.splitapp.SplitApp.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by_id", nullable = false)
    private User paidBy;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseSplit> splits = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Expense(BigDecimal amount, ExpenseCategory category, String description,
                   ExpenseGroup group, User paidBy) {
        this.amount = Objects.requireNonNull(amount, "Amount is required");
        this.category = Objects.requireNonNull(category, "Category is required");
        this.description = description;
        this.group = Objects.requireNonNull(group, "Group is required");
        this.paidBy = Objects.requireNonNull(paidBy, "Payer is required");

        if (!group.hasMember(paidBy)) {
            throw new IllegalArgumentException("Payer is not a member of this group");
        }
    }

    public List<ExpenseSplit> getSplits() {
        return Collections.unmodifiableList(splits);
    }

    public void addSplit(ExpenseSplit split) {
        Objects.requireNonNull(split, "Split is required");

        if (!group.hasMember(split.getUser())) {
            throw new IllegalArgumentException("User is not a member of this group");
        }

        boolean duplicate = splits.stream()
                .anyMatch(existing -> User.sameUser(existing.getUser(), split.getUser()));
        if (duplicate) {
            throw new IllegalArgumentException("User already has a split for this expense");
        }

        splits.add(split);
        split.setExpense(this);
    }
}
