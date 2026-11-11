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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "group_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private ExpenseGroup group;

    @Column(nullable = false, updatable = false, length = 255)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_id", nullable = false, updatable = false)
    private User invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public GroupInvitation(ExpenseGroup group, String email, User invitedBy) {
        this.group = Objects.requireNonNull(group, "Group is required");
        this.email = User.normalizeEmail(email);
        this.invitedBy = Objects.requireNonNull(invitedBy, "Inviter is required");
        this.status = InvitationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public void accept() {
        requirePending();
        this.status = InvitationStatus.ACCEPTED;
    }

    public void decline() {
        requirePending();
        this.status = InvitationStatus.DECLINED;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("Invitation is no longer pending");
        }
    }
}
