package com.splitapp.SplitApp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

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
    @Size(max = 255)
    @Getter
    private String email;

    @Column(nullable = false, length = 120)
    @NotBlank(message = "Password is required")
    private String password;

    @Column(nullable = false, updatable = false)
    @Getter
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public User(String firstName, String lastName, String email, String passwordHash) {
        this.firstName = Objects.requireNonNull(firstName, "First name is required");
        this.lastName = Objects.requireNonNull(lastName, "Last name is required");
        this.email = normalizeEmail(email);
        this.password = Objects.requireNonNull(passwordHash, "Password hash is required");
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "Email is required")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    static boolean sameUser(User first, User second) {
        return first == second
                || (first != null && second != null
                && first.getId() != null && first.getId().equals(second.getId()));
    }
}
