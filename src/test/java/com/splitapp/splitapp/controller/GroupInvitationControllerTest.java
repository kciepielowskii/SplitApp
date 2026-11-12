package com.splitapp.splitapp.controller;

import com.splitapp.splitapp.dto.InvitationResponse;
import com.splitapp.splitapp.entity.InvitationStatus;
import com.splitapp.splitapp.exception.BusinessRuleException;
import com.splitapp.splitapp.exception.GroupAccessDeniedException;
import com.splitapp.splitapp.exception.GroupNotFoundException;
import com.splitapp.splitapp.exception.InvitationNotFoundException;
import com.splitapp.splitapp.security.SecurityConfig;
import com.splitapp.splitapp.security.WithMockAppUser;
import com.splitapp.splitapp.service.InvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupInvitationController.class)
@Import(SecurityConfig.class)
@WithMockAppUser
class GroupInvitationControllerTest {
    private static final String VALID_BODY = """
            {"email":"anna@example.com"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvitationService invitationService;

    @Test
    void shouldInviteByEmail() throws Exception {
        InvitationResponse response = new InvitationResponse(5L, 1L, "anna@example.com", 1L,
                InvitationStatus.PENDING, Instant.parse("2026-11-11T12:00:00Z"));
        when(invitationService.invite(1L, 1L, "anna@example.com")).thenReturn(response);

        mockMvc.perform(post("/groups/1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {
        mockMvc.perform(post("/groups/1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists());

        verifyNoInteractions(invitationService);
    }

    @Test
    void shouldRejectMissingEmail() throws Exception {
        mockMvc.perform(post("/groups/1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        verifyNoInteractions(invitationService);
    }

    @Test
    void shouldReturn403WhenNonOwnerInvites() throws Exception {
        when(invitationService.invite(1L, 1L, "anna@example.com"))
                .thenThrow(new GroupAccessDeniedException("Only the group owner can invite members"));

        mockMvc.perform(post("/groups/1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void shouldReturn422WhenEmailIsAlreadyInvited() throws Exception {
        when(invitationService.invite(1L, 1L, "anna@example.com"))
                .thenThrow(new BusinessRuleException("This e-mail already has a pending invitation"));

        mockMvc.perform(post("/groups/1/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"));
    }

    @Test
    void shouldReturn404WhenInvitingToUnknownGroup() throws Exception {
        when(invitationService.invite(99L, 1L, "anna@example.com"))
                .thenThrow(new GroupNotFoundException("Group not found"));

        mockMvc.perform(post("/groups/99/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldListGroupInvitations() throws Exception {
        InvitationResponse response = new InvitationResponse(5L, 1L, "anna@example.com", 1L,
                InvitationStatus.PENDING, Instant.parse("2026-11-11T12:00:00Z"));
        when(invitationService.getGroupInvitations(1L, 1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/groups/1/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("anna@example.com"));
    }

    @Test
    void shouldCancelInvitation() throws Exception {
        mockMvc.perform(delete("/groups/1/invitations/5"))
                .andExpect(status().isNoContent());

        verify(invitationService).cancelInvitation(1L, 1L, 5L);
    }

    @Test
    void shouldReturn404WhenCancellingUnknownInvitation() throws Exception {
        doThrow(new InvitationNotFoundException("Invitation not found"))
                .when(invitationService).cancelInvitation(1L, 1L, 99L);

        mockMvc.perform(delete("/groups/1/invitations/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Invitation not found"));
    }
}
