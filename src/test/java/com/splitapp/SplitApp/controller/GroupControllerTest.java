package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.GroupMemberResponse;
import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.entity.GroupRole;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        when(groupService.createGroup("Wyjazd", 1L)).thenReturn(response);

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/groups/1"))
                .andExpect(jsonPath("$.groupName").value("Wyjazd"))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"));
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
        when(groupService.createGroup("Wyjazd", 99L))
                .thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupName":"Wyjazd","userId":99}
                                """))
                .andExpect(status().isNotFound());
    }
}
