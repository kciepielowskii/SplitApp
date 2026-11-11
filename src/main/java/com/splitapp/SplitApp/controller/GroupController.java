package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.AddMemberRequest;
import com.splitapp.SplitApp.dto.CreateGroupRequest;
import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(
                request.groupName(), request.groupDescription(), request.userId());
    }

    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse addMember(@PathVariable Long groupId,
                                   @Valid @RequestBody AddMemberRequest request) {
        return groupService.addMember(groupId, request.userId());
    }

    @GetMapping("/{groupId}")
    public GroupResponse getGroup(@PathVariable Long groupId) {
        return groupService.getGroup(groupId);
    }
}
