package com.splitapp.SplitApp.service;

import com.splitapp.SplitApp.dto.UserResponse;
import com.splitapp.SplitApp.entity.User;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceGetUserTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldReturnTheUser() {
        User user = new User("Jan", "Kowalski", "jan@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse result = userService.getUser(1L);

        assertEquals(1L, result.id());
        assertEquals("Jan", result.firstName());
        assertEquals("jan@example.com", result.email());
    }

    @Test
    void shouldThrowWhenTheUserDoesNotExist() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUser(9L));
    }
}
