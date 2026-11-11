package com.splitapp.SplitApp.security;

import com.splitapp.SplitApp.repository.LoginView;
import com.splitapp.SplitApp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService service;

    @Test
    void shouldLoadUserByNormalizedEmail() {
        LoginView login = mock(LoginView.class);
        when(login.getId()).thenReturn(7L);
        when(login.getEmail()).thenReturn("jan@example.com");
        when(login.getPassword()).thenReturn("hash");
        when(userRepository.findLoginByEmail("jan@example.com")).thenReturn(Optional.of(login));

        AppUserDetails details = (AppUserDetails) service.loadUserByUsername("  Jan@Example.com ");

        assertEquals(7L, details.getId());
        assertEquals("jan@example.com", details.getUsername());
        assertEquals("hash", details.getPassword());
    }

    @Test
    void shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findLoginByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("nobody@example.com"));
    }
}
