package com.splitapp.splitapp.controller;

import com.splitapp.splitapp.dto.GroupResponse;
import com.splitapp.splitapp.dto.TransferOwnershipRequest;
import com.splitapp.splitapp.security.AppUserDetails;
import com.splitapp.splitapp.service.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}")
@RequiredArgsConstructor
public class MembershipController {
    private final MembershipService membershipService;

    @PostMapping("/owner")
    public GroupResponse transferOwnership(@PathVariable Long groupId,
                                           @Valid @RequestBody TransferOwnershipRequest request,
                                           @AuthenticationPrincipal AppUserDetails currentUser) {
        return membershipService.transferOwnership(groupId, currentUser.getId(), request.userId());
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveGroup(@PathVariable Long groupId,
                           @AuthenticationPrincipal AppUserDetails currentUser) {
        membershipService.leaveGroup(groupId, currentUser.getId());
    }
}
