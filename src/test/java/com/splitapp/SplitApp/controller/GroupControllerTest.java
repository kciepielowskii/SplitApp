package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.GroupMemberResponse;
import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.exception.BusinessRuleException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.service.GroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
class GroupControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupService groupService;

    @Test
    void shouldCreateGroup() throws Exception {
        GroupResponse response = new GroupResponse(1L, "Wyjazd", null, 1L,
                List.of(new GroupMemberResponse(1L, "Jan", "Kowalski", GroupRole.OWNER)));
        when(groupService.createGroup("Wyjazd", null, 1L)).thenReturn(response);

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupName").value("Wyjazd"))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.createdById").value(1));
    }

    @Test
    void shouldRejectBlankGroupName() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":" ","userId":1}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldRejectMissingUserId() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        when(groupService.createGroup("Wyjazd", null, 99L))
                .thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":99}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("User not found"))
                .andExpect(jsonPath("$.detail").value("User not found"));
    }

    @Test
    void shouldRejectGroupNameLongerThan100Characters() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"groupName":"%s","userId":1}
                            """.formatted("a".repeat(101))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldRejectNonNumericUserId() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"groupName":"Wyjazd","userId":"abc"}
                            """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldCreateGroupWithDescription() throws Exception {
        GroupResponse response = new GroupResponse(1L, "Wyjazd", "Weekend w górach", 1L,
                List.of(new GroupMemberResponse(1L, "Jan", "Kowalski", GroupRole.OWNER)));
        when(groupService.createGroup("Wyjazd", "Weekend w górach", 1L)).thenReturn(response);

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","groupDescription":"Weekend w górach","userId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupDescription").value("Weekend w górach"));
    }

    @Test
    void shouldRejectGroupDescriptionLongerThan255Characters() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","groupDescription":"%s","userId":1}
                                """.formatted("a".repeat(256))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldAddMember() throws Exception {
        GroupResponse response = new GroupResponse(1L, "Wyjazd", null, 1L,
                List.of(new GroupMemberResponse(1L, "Jan", "Kowalski", GroupRole.OWNER),
                        new GroupMemberResponse(2L, "Anna", "Nowak", GroupRole.MEMBER)));
        when(groupService.addMember(1L, 2L)).thenReturn(response);

        mockMvc.perform(post("/groups/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members[1].userId").value(2))
                .andExpect(jsonPath("$.members[1].role").value("MEMBER"));
    }

    @Test
    void shouldRejectMissingUserIdWhenAddingMember() throws Exception {
        mockMvc.perform(post("/groups/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldReturn404WhenAddingMemberToUnknownGroup() throws Exception {
        when(groupService.addMember(99L, 2L))
                .thenThrow(new GroupNotFoundException("Group not found"));

        mockMvc.perform(post("/groups/99/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldReturn422WhenUserIsAlreadyMember() throws Exception {
        when(groupService.addMember(1L, 2L))
                .thenThrow(new BusinessRuleException("User is already a member of this group"));

        mockMvc.perform(post("/groups/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":2}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violation"));
    }
}
