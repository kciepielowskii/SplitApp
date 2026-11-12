package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.RegisterUserRequest;
import com.splitapp.SplitApp.dto.UserResponse;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.EmailAlreadyUsedException;
import com.splitapp.SplitApp.repository.UserRepository;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;


@Service
@Validated
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(@Valid @NotNull RegisterUserRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException("Email is already in use");
        }

        User user = new User(request.firstName(), request.lastName(),
                email, passwordEncoder.encode(request.password()));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(@NotNull Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
