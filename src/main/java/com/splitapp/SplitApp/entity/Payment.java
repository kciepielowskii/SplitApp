package com.splitapp.SplitApp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @NotNull(message = "The amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 8, fraction = 2)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant paidAt;

    public Payment(ExpenseGroup group, User sender, User receiver, BigDecimal amount,
                   User createdBy) {
        this.group = Objects.requireNonNull(group, "Group is required");
        this.sender = Objects.requireNonNull(sender, "Sender is required");
        this.receiver = Objects.requireNonNull(receiver, "Receiver is required");
        this.amount = Objects.requireNonNull(amount, "Amount is required");
        this.createdBy = Objects.requireNonNull(createdBy, "Author is required");

        if (User.sameUser(sender, receiver)) {
            throw new IllegalArgumentException("Sender and receiver must differ");
        }
        if (!group.hasMember(sender)) {
            throw new IllegalArgumentException("Sender is not a member of this group");
        }
        if (!group.hasMember(receiver)) {
            throw new IllegalArgumentException("Receiver is not a member of this group");
        }
    }

    @PrePersist
    protected void onCreate() {
        this.paidAt = Instant.now();
    }
}
