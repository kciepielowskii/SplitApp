package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.validation.NoNul;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(@NotBlank @Email @Size(max = 255) @NoNul String email) {

    public CreateInvitationRequest {
        email = email == null ? null : email.trim();
    }
}
