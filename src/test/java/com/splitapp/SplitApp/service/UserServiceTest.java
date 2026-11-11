package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.RegisterUserRequest;
import com.splitapp.SplitApp.dto.UserResponse;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.EmailAlreadyUsedException;
import com.splitapp.SplitApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldRegisterUserWithHashedPasswordAndNormalizedEmail() {
        when(userRepository.existsByEmail("jan@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse result = userService.register(
                new RegisterUserRequest("Jan", "Kowalski", "Jan@Example.com", "secret123"));

        assertEquals("jan@example.com", result.email());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed", ReflectionTestUtils.getField(captor.getValue(), "password"));
    }

    @Test
    void shouldRejectAlreadyUsedEmail() {
        when(userRepository.existsByEmail("jan@example.com")).thenReturn(true);

        EmailAlreadyUsedException ex = assertThrows(EmailAlreadyUsedException.class,
                () -> userService.register(
                        new RegisterUserRequest("Jan", "Kowalski", "jan@example.com", "secret123")));

        assertEquals("Email is already in use", ex.getMessage());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }
}
