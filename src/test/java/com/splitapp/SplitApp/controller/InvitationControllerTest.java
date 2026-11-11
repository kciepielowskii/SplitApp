package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.GroupMemberResponse;
import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.dto.PendingInvitationResponse;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.exception.InvitationNotFoundException;
import com.splitapp.SplitApp.security.SecurityConfig;
import com.splitapp.SplitApp.security.WithMockAppUser;
import com.splitapp.SplitApp.service.InvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvitationController.class)
@Import(SecurityConfig.class)
@WithMockAppUser
class InvitationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvitationService invitationService;

    @Test
    void shouldListMyInvitationsWithoutGroupDetails() throws Exception {
        PendingInvitationResponse response = new PendingInvitationResponse(
                5L, "Wyjazd", "Jan", "Kowalski", Instant.parse("2026-11-11T12:00:00Z"));
        when(invitationService.getMyInvitations(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/invitations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].groupName").value("Wyjazd"))
                .andExpect(jsonPath("$[0].invitedByFirstName").value("Jan"))
                .andExpect(jsonPath("$[0].members").doesNotExist());
    }

    @Test
    void shouldAcceptInvitation() throws Exception {
        GroupResponse response = new GroupResponse(1L, "Wyjazd", null, 2L,
                List.of(new GroupMemberResponse(2L, "Jan", "Kowalski", GroupRole.OWNER),
                        new GroupMemberResponse(1L, "Anna", "Nowak", GroupRole.MEMBER)));
        when(invitationService.acceptInvitation(1L, 5L)).thenReturn(response);

        mockMvc.perform(post("/invitations/5/accept").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.members[1].userId").value(1))
                .andExpect(jsonPath("$.members[1].role").value("MEMBER"));
    }

    @Test
    void shouldReturn404WhenAcceptingUnknownInvitation() throws Exception {
        when(invitationService.acceptInvitation(1L, 99L))
                .thenThrow(new InvitationNotFoundException("Invitation not found"));

        mockMvc.perform(post("/invitations/99/accept").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Invitation not found"));
    }

    @Test
    void shouldDeclineInvitation() throws Exception {
        mockMvc.perform(post("/invitations/5/decline").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(invitationService).declineInvitation(1L, 5L);
    }

    @Test
    void shouldReturn404WhenDecliningUnknownInvitation() throws Exception {
        doThrow(new InvitationNotFoundException("Invitation not found"))
                .when(invitationService).declineInvitation(1L, 99L);

        mockMvc.perform(post("/invitations/99/decline").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Invitation not found"));
    }

    @Test
    void shouldRejectNonNumericInvitationId() throws Exception {
        mockMvc.perform(post("/invitations/abc/accept").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(invitationService);
    }
}
