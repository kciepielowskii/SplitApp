package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.CreateGroupRequest;
import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.createGroup(request.groupName(), request.userId());
        return ResponseEntity.created(URI.create("/groups/" + response.id())).body(response);
    }
}
