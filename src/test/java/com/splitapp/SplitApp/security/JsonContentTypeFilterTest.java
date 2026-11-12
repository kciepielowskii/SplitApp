package com.splitapp.SplitApp.security;

import com.splitapp.SplitApp.controller.PaymentController;
import com.splitapp.SplitApp.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@WithMockAppUser
class JsonContentTypeFilterTest {
    private static final String CONFIRM_URL = "/groups/1/payments/7/confirm";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void shouldRejectBodilessPostWithoutContentType() throws Exception {
        mockMvc.perform(post(CONFIRM_URL))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.instance").value(CONFIRM_URL));

        verifyNoInteractions(paymentService);
    }

    @Test
    void shouldRejectTheContentTypesAnHtmlFormCanSend() throws Exception {
        List<String> formTypes = List.of(
                MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                MediaType.MULTIPART_FORM_DATA_VALUE,
                MediaType.TEXT_PLAIN_VALUE);

        for (String type : formTypes) {
            mockMvc.perform(post(CONFIRM_URL).contentType(type))
                    .andExpect(status().isUnsupportedMediaType());
        }

        verifyNoInteractions(paymentService);
    }

    @Test
    void shouldAcceptJsonWithACharset() throws Exception {
        mockMvc.perform(post(CONFIRM_URL).contentType("application/json;charset=UTF-8"))
                .andExpect(status().isOk());

        verify(paymentService).confirmPayment(1L, 1L, 7L);
    }

    @Test
    @WithAnonymousUser
    void shouldAnswer401BeforeCheckingTheContentTypeWhenNotLoggedIn() throws Exception {
        mockMvc.perform(post(CONFIRM_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value(CONFIRM_URL));

        verifyNoInteractions(paymentService);
    }

    @Test
    @WithAnonymousUser
    void shouldAlsoGuardThePublicRegistrationEndpoint() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isUnsupportedMediaType());
    }
}
