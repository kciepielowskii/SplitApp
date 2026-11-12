package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.validation.MaxBytes;
import com.splitapp.splitapp.validation.NoNul;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(max = 50) @NoNul String firstName,
        @NotBlank @Size(max = 50) @NoNul String lastName,
        @NotBlank @Email @Size(max = 255) @NoNul
        @Pattern(regexp = "\\p{ASCII}*", message = "Email must contain only ASCII characters")
        String email,
        @NotBlank @Size(min = 8) @MaxBytes(72) String password) {

    public RegisterUserRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
        email = trim(email);
        // password is intentionally not trimmed
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
