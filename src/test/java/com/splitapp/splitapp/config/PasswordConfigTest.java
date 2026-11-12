package com.splitapp.splitapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordConfigTest {
    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void shouldPrefixNewHashesWithTheAlgorithmName() {
        String hash = encoder.encode("secret123");

        assertTrue(hash.startsWith("{bcrypt}$2"));
        assertTrue(encoder.matches("secret123", hash));
        assertFalse(encoder.matches("other", hash));
    }

    @Test
    void shouldAcceptAPlainBcryptHashOnceItHasTheMigrationPrefix() {
        String plain = new BCryptPasswordEncoder().encode("secret123");

        assertTrue(encoder.matches("secret123", "{bcrypt}" + plain));
    }

    @Test
    void shouldTreatAHashWithoutAPrefixAsBcrypt() {
        String plain = new BCryptPasswordEncoder().encode("secret123");

        assertTrue(encoder.matches("secret123", plain));
        assertFalse(encoder.matches("other", plain));
    }

    @Test
    void shouldNotMatchAHashWithAnUnknownPrefix() {
        assertFalse(encoder.matches("secret123", "{noop}secret123"));
    }
}
