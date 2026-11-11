package com.splitapp.SplitApp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(@NotBlank @Email @Size(max = 255) String email) {

    public CreateInvitationRequest {
        email = email == null ? null : email.trim();
    }
}
