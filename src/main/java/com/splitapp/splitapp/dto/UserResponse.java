package com.splitapp.splitapp.dto;

import com.splitapp.splitapp.entity.User;

public record UserResponse(Long id, String firstName, String lastName, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFirstName(),
                user.getLastName(), user.getEmail());
    }
}
