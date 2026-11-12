package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.GroupMemberResponse;
import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.GroupRole;
import com.splitapp.SplitApp.exception.GroupAccessDeniedException;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.exception.UserNotFoundException;
import com.splitapp.SplitApp.security.SecurityConfig;
import com.splitapp.SplitApp.security.WithMockAppUser;
import com.splitapp.SplitApp.service.GroupService;
import com.splitapp.SplitApp.dto.GroupSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupController.class)
@Import(SecurityConfig.class)
@WithMockAppUser
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
                                {"groupName":"Wyjazd"}
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
                                {"groupName":" "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        when(groupService.createGroup("Wyjazd", null, 1L))
                .thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd"}
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
                                {"groupName":"%s"}
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
    void shouldCreateGroupWithDescription() throws Exception {
        GroupResponse response = new GroupResponse(1L, "Wyjazd", "Weekend w górach", 1L,
                List.of(new GroupMemberResponse(1L, "Jan", "Kowalski", GroupRole.OWNER)));
        when(groupService.createGroup("Wyjazd", "Weekend w górach", 1L)).thenReturn(response);

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","groupDescription":"Weekend w górach"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupDescription").value("Weekend w górach"));
    }

    @Test
    void shouldRejectGroupDescriptionLongerThan255Characters() throws Exception {
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","groupDescription":"%s"}
                                """.formatted("a".repeat(256))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldReturnGroup() throws Exception {
        GroupResponse response = new GroupResponse(1L, "Wyjazd", null, 1L,
                List.of(new GroupMemberResponse(1L, "Jan", "Kowalski", GroupRole.OWNER)));
        when(groupService.getGroup(1L, 1L)).thenReturn(response);

        mockMvc.perform(get("/groups/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.groupName").value("Wyjazd"))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
    }

    @Test
    void shouldReturn404WhenGettingUnknownGroup() throws Exception {
        when(groupService.getGroup(99L, 1L)).thenThrow(new GroupNotFoundException("Group not found"));

        mockMvc.perform(get("/groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }

    @Test
    void shouldRejectNonNumericGroupIdWhenGettingGroup() throws Exception {
        mockMvc.perform(get("/groups/abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(groupService);
    }

    @Test
    void shouldReturn403WhenUserIsNotGroupMember() throws Exception {
        when(groupService.getGroup(1L, 1L))
                .thenThrow(new GroupAccessDeniedException("You are not a member of this group"));

        mockMvc.perform(get("/groups/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You are not a member of this group"));
    }
    @Test
    void shouldListTheGroupsOfTheLoggedInUser() throws Exception {
        when(groupService.getMyGroups(1L)).thenReturn(List.of(
                new GroupSummaryResponse(2L, "Wspólna", null, GroupRole.MEMBER),
                new GroupSummaryResponse(1L, "Wyjazd", "Weekend w górach", GroupRole.OWNER)));

        mockMvc.perform(get("/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].myRole").value("MEMBER"))
                .andExpect(jsonPath("$[1].groupDescription").value("Weekend w górach"))
                .andExpect(jsonPath("$[1].myRole").value("OWNER"))
                .andExpect(jsonPath("$[0].members").doesNotExist());
    }
}
