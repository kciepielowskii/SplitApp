package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.CreateInvitationRequest;
import com.splitapp.SplitApp.dto.InvitationResponse;
import com.splitapp.SplitApp.security.AppUserDetails;
import com.splitapp.SplitApp.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/groups/{groupId}/invitations")
@RequiredArgsConstructor
public class GroupInvitationController {
    private final InvitationService invitationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@PathVariable Long groupId,
                                     @Valid @RequestBody CreateInvitationRequest request,
                                     @AuthenticationPrincipal AppUserDetails currentUser) {
        return invitationService.invite(groupId, currentUser.getId(), request.email());
    }

    @GetMapping
    public List<InvitationResponse> getInvitations(
            @PathVariable Long groupId,
            @AuthenticationPrincipal AppUserDetails currentUser) {
        return invitationService.getGroupInvitations(groupId, currentUser.getId());
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelInvitation(@PathVariable Long groupId, @PathVariable Long invitationId,
                                 @AuthenticationPrincipal AppUserDetails currentUser) {
        invitationService.cancelInvitation(groupId, currentUser.getId(), invitationId);
    }
}
