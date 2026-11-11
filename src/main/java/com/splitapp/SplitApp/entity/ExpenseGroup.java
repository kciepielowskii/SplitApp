package com.splitapp.SplitApp.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "expense_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Column(nullable = false, length = 100)
    @Size(max = 100)
    @NotBlank(message = "The group name is required")
    @Getter
    private String groupName;

    @Size(max = 255)
    @Getter
    private String groupDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    @Getter
    private User createdBy;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<GroupMember> members = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public ExpenseGroup(String groupName, User createdBy) {
        this.groupName = Objects.requireNonNull(groupName, "Group name is required");
        this.createdBy = Objects.requireNonNull(createdBy, "Creator is required");
    }

    public ExpenseGroup(String groupName, String groupDescription, User createdBy) {
        this(groupName, createdBy);
        this.groupDescription = groupDescription;
    }

    public boolean hasMember(User user) {
        return members.stream()
                .anyMatch(member -> User.sameUser(member.getUser(), user));
    }

    public boolean isOwner(Long userId) {
        return members.stream()
                .anyMatch(member -> member.getRole() == GroupRole.OWNER
                        && Objects.equals(member.getUser().getId(), userId));
    }

    public GroupMember addMember(User user, GroupRole role) {
        Objects.requireNonNull(user, "User is required");
        Objects.requireNonNull(role, "Role is required");

        if (hasMember(user)) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        GroupMember member = new GroupMember(this, user, role);
        members.add(member);
        return member;
    }

    public List<GroupMember> getMembers() {
        return Collections.unmodifiableList(members);
    }
}
