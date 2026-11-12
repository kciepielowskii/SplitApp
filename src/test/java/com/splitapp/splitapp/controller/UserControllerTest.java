package com.splitapp.splitapp.controller;

import com.splitapp.splitapp.dto.RegisterUserRequest;
import com.splitapp.splitapp.dto.UserResponse;
import com.splitapp.splitapp.exception.EmailAlreadyUsedException;
import com.splitapp.splitapp.security.SecurityConfig;
import com.splitapp.splitapp.service.UserService;
import com.splitapp.splitapp.exception.UserNotFoundException;
import com.splitapp.splitapp.security.WithMockAppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@WithMockUser
class UserControllerTest {
    private static final String VALID_BODY = """
            {"firstName":"Jan","lastName":"Kowalski","email":"jan@example.com","password":"secret123"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldRegisterUserAndNotExposePassword() throws Exception {
        when(userService.register(any(RegisterUserRequest.class)))
                .thenReturn(new UserResponse(1L, "Jan", "Kowalski", "jan@example.com"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jan@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldRejectInvalidRequestWith400() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"firstName":"","lastName":"Kowalski","email":"not-an-email","password":"123"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturn409WhenEmailAlreadyUsed() throws Exception {
        when(userService.register(any(RegisterUserRequest.class)))
                .thenThrow(new EmailAlreadyUsedException("Email is already in use"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Email is already in use"));
    }

    @Test
    void shouldReturn409WhenDatabaseRejectsDuplicate() throws Exception {
        when(userService.register(any(RegisterUserRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key",
                                new org.hibernate.exception.ConstraintViolationException(
                                        "duplicate key", new java.sql.SQLException("duplicate", "23505"), "uk_users_email")));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"));
    }

    @Test
    @WithMockAppUser
    void shouldReturnTheLoggedInUserAndNotExposeThePassword() throws Exception {
        when(userService.getUser(1L))
                .thenReturn(new UserResponse(1L, "Jan", "Kowalski", "jan@example.com"));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("jan@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockAppUser
    void shouldReturn404WhenTheLoggedInUserNoLongerExists() throws Exception {
        when(userService.getUser(1L)).thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"));
    }
}
