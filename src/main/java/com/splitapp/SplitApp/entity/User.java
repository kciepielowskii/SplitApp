package com.splitapp.SplitApp.entity;

import jakarta.persistence.*;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Column(nullable = false, length = 50)
    @Size(max = 50)
    @NotBlank(message = "First name is required")
    @Getter
    @Setter
    private String firstName;

    @Column(nullable = false, length = 50)
    @Size(max = 50)
    @NotBlank(message = "Last name is required")
    @Getter
    @Setter
    private String lastName;

    @Column(nullable = false, unique = true)
    @Email(message = "Invalid email address format")
    @NotBlank(message = "Email is required")
    @Getter
    @Setter
    private String email;

    @Column(nullable = false, length = 120)
    @NotBlank(message = "Password is required")
    private String password;

    @Column(nullable = false, updatable = false)
    @Getter
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
