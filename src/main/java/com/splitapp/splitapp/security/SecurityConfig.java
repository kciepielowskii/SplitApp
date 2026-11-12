package com.splitapp.splitapp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ProblemDetailAuthenticationEntryPoint entryPoint = new ProblemDetailAuthenticationEntryPoint();
        http
                // CSRF protection is off. Browsers cache Basic credentials and resend them
                // automatically, so a cross-site form POST would be authenticated. That is
                // stopped by RequireJsonContentTypeFilter: every POST must be application/json,
                // which an HTML form cannot send. Do not add permissive CORS or endpoints
                // that accept form data.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // the API is stateless, so there is nothing to log out of; without this Spring
                // Security answers GET/POST /logout with a redirect to a login page
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/users").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .addFilterAfter(new RequireJsonContentTypeFilter(), AuthorizationFilter.class);
        return http.build();
    }
}
