package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.RegisterUserRequest;
import com.splitapp.SplitApp.dto.UserResponse;
import com.splitapp.SplitApp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.created(URI.create("/users/" + response.id())).body(response);
    }
}
