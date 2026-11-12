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
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        DelegatingPasswordEncoder encoder =
                new DelegatingPasswordEncoder(DEFAULT_ID, Map.of(DEFAULT_ID, bcrypt));
        // a hash without a known prefix is checked as BCrypt instead of throwing
        // IllegalArgumentException, which would surface as a 500 on a login attempt
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }
}
