package com.splitapp.SplitApp.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "group_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"group_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    @Getter
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @Getter
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Getter
    private GroupRole role;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    public void onCreate() {
        joinedAt = Instant.now();
    }

    public GroupMember(ExpenseGroup group, User user, GroupRole role) {
        this.group = group;
        this.user = user;
        this.role = role;
    }
}
