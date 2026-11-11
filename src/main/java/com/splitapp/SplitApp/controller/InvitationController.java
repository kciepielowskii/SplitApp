package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.GroupResponse;
import com.splitapp.SplitApp.dto.PendingInvitationResponse;
import com.splitapp.SplitApp.security.AppUserDetails;
import com.splitapp.SplitApp.service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/invitations")
@RequiredArgsConstructor
public class InvitationController {
    private final InvitationService invitationService;

    @GetMapping
    public List<PendingInvitationResponse> getMyInvitations(
            @AuthenticationPrincipal AppUserDetails currentUser) {
        return invitationService.getMyInvitations(currentUser.getId());
    }

    @PostMapping("/{invitationId}/accept")
    public GroupResponse acceptInvitation(@PathVariable Long invitationId,
                                          @AuthenticationPrincipal AppUserDetails currentUser) {
        return invitationService.acceptInvitation(currentUser.getId(), invitationId);
    }

    @PostMapping("/{invitationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineInvitation(@PathVariable Long invitationId,
                                  @AuthenticationPrincipal AppUserDetails currentUser) {
        invitationService.declineInvitation(currentUser.getId(), invitationId);
    }
}
