package com.splitapp.splitapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration
public class PasswordConfig {

    private static final String DEFAULT_ID = "bcrypt";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(DEFAULT_ID,
                Map.of(DEFAULT_ID, new BCryptPasswordEncoder()));
    }
}
