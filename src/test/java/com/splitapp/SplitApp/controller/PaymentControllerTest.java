package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.CreatePaymentRequest;
import com.splitapp.SplitApp.dto.PaymentResponse;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.security.SecurityConfig;
import com.splitapp.SplitApp.security.WithMockAppUser;
import com.splitapp.SplitApp.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@WithMockAppUser
class PaymentControllerTest {
    private static final String VALID_BODY = """
            {"senderId":1,"receiverId":2,"amount":10.00}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void shouldCreatePayment() throws Exception {
        PaymentResponse response = new PaymentResponse(7L, 1L, 1L, 2L,
                new BigDecimal("10.00"), Instant.parse("2026-11-11T12:00:00Z"));
        when(paymentService.createPayment(eq(1L), eq(1L), any(CreatePaymentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/groups/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.groupId").value(1))
                .andExpect(jsonPath("$.senderId").value(1))
                .andExpect(jsonPath("$.receiverId").value(2));
    }

    @Test
    void shouldRejectInvalidRequestWith400() throws Exception {
        mockMvc.perform(post("/groups/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.senderId").exists())
                .andExpect(jsonPath("$.errors.receiverId").exists());

        verifyNoInteractions(paymentService);
    }

    @Test
    void shouldRejectNonNumericGroupId() throws Exception {
        mockMvc.perform(post("/groups/abc/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);
    }

    @Test
    void shouldReturn404WhenGroupDoesNotExist() throws Exception {
        when(paymentService.createPayment(eq(99L), eq(1L), any(CreatePaymentRequest.class)))
                .thenThrow(new GroupNotFoundException("Group not found"));

        mockMvc.perform(post("/groups/99/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldReturn422OnBusinessRuleViolation() throws Exception {
        when(paymentService.createPayment(eq(1L), eq(1L), any(CreatePaymentRequest.class)))
                .thenThrow(new BusinessRuleException("Sender and receiver must differ"));

        mockMvc.perform(post("/groups/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"))
                .andExpect(jsonPath("$.detail").value("Sender and receiver must differ"));
    }

    @Test
    void shouldReturnGroupPayments() throws Exception {
        PaymentResponse response = new PaymentResponse(7L, 1L, 1L, 2L,
                new BigDecimal("10.00"), Instant.parse("2026-11-11T12:00:00Z"));
        when(paymentService.getPayments(1L, 1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/groups/1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].senderId").value(1))
                .andExpect(jsonPath("$[0].receiverId").value(2));
    }

    @Test
    void shouldReturn404WhenListingPaymentsOfUnknownGroup() throws Exception {
        when(paymentService.getPayments(99L, 1L)).thenThrow(new GroupNotFoundException("Group not found"));

        mockMvc.perform(get("/groups/99/payments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }
}
