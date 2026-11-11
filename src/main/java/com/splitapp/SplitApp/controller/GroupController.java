package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.CreateGroupRequest;
import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.security.AppUserDetails;
import com.splitapp.SplitApp.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request,
                                     @AuthenticationPrincipal AppUserDetails currentUser) {
        return groupService.createGroup(
                request.groupName(), request.groupDescription(), currentUser.getId());
    }

    @GetMapping("/{groupId}")
    public GroupResponse getGroup(@PathVariable Long groupId,
                                  @AuthenticationPrincipal AppUserDetails currentUser) {
        return groupService.getGroup(groupId, currentUser.getId());
    }
}
