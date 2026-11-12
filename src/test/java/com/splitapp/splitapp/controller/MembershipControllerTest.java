package com.splitapp.splitapp.controller;

import com.splitapp.splitapp.dto.GroupMemberResponse;
import com.splitapp.splitapp.dto.GroupResponse;
import com.splitapp.splitapp.entity.GroupRole;
import com.splitapp.splitapp.exception.BusinessRuleException;
import com.splitapp.splitapp.exception.GroupAccessDeniedException;
import com.splitapp.splitapp.exception.GroupNotFoundException;
import com.splitapp.splitapp.security.SecurityConfig;
import com.splitapp.splitapp.security.WithMockAppUser;
import com.splitapp.splitapp.service.MembershipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MembershipController.class)
@Import(SecurityConfig.class)
@WithMockAppUser
class MembershipControllerTest {
    private static final String VALID_BODY = """
            {"userId":2}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MembershipService membershipService;

    @Test
    void shouldTransferOwnership() throws Exception {
        GroupResponse response = new GroupResponse(1L, "Wyjazd", null, 1L,
                List.of(new GroupMemberResponse(1L, "Jan", "Kowalski", GroupRole.MEMBER),
                        new GroupMemberResponse(2L, "Anna", "Nowak", GroupRole.OWNER)));
        when(membershipService.transferOwnership(1L, 1L, 2L)).thenReturn(response);

        mockMvc.perform(post("/groups/1/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.members[1].userId").value(2))
                .andExpect(jsonPath("$.members[1].role").value("OWNER"));
    }

    @Test
    void shouldRejectMissingUserIdWhenTransferringOwnership() throws Exception {
        mockMvc.perform(post("/groups/1/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists());

        verifyNoInteractions(membershipService);
    }

    @Test
    void shouldReturn403WhenNonOwnerTransfersOwnership() throws Exception {
        when(membershipService.transferOwnership(1L, 1L, 2L))
                .thenThrow(new GroupAccessDeniedException("Only the group owner can transfer ownership"));

        mockMvc.perform(post("/groups/1/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void shouldReturn422WhenNewOwnerIsNotMember() throws Exception {
        when(membershipService.transferOwnership(1L, 1L, 2L))
                .thenThrow(new BusinessRuleException("The new owner must be a member of this group"));

        mockMvc.perform(post("/groups/1/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"));
    }

    @Test
    void shouldLeaveGroup() throws Exception {
        mockMvc.perform(post("/groups/1/leave").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(membershipService).leaveGroup(1L, 1L);
    }

    @Test
    void shouldReturn422WhenLeavingWithOutstandingBalance() throws Exception {
        doThrow(new BusinessRuleException("Settle your balance before leaving the group"))
                .when(membershipService).leaveGroup(1L, 1L);

        mockMvc.perform(post("/groups/1/leave").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail")
                        .value("Settle your balance before leaving the group"));
    }

    @Test
    void shouldReturn404WhenLeavingUnknownGroup() throws Exception {
        doThrow(new GroupNotFoundException("Group not found"))
                .when(membershipService).leaveGroup(99L, 1L);

        mockMvc.perform(post("/groups/99/leave").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldReturn403WhenNonMemberLeaves() throws Exception {
        doThrow(new GroupAccessDeniedException("You are not a member of this group"))
                .when(membershipService).leaveGroup(1L, 1L);

        mockMvc.perform(post("/groups/1/leave").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }
}
