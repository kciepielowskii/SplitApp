package com.splitapp.SplitApp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    @JoinColumn(name = "user_id", nullable = false)
    @Getter
    private User createdBy;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @Getter
    private List<GroupMember> members = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void createOn() {
        createdAt = Instant.now();
    }

    public ExpenseGroup(String groupName, User createdBy) {
        this.groupName = groupName;
        this.createdBy = createdBy;
    }

    public ExpenseGroup(String groupName, String groupDescription, User createdBy) {
        this(groupName, createdBy);
        this.groupDescription = groupDescription;
    }

    public GroupMember addMember(User user, GroupRole role) {
        GroupMember member = new GroupMember(this, user, role);
        members.add(member);
        return member;
    }
}