package com.splitapp.splitapp.controller;

import com.splitapp.splitapp.dto.GroupBalancesResponse;
import com.splitapp.splitapp.security.AppUserDetails;
import com.splitapp.splitapp.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups/{groupId}/balances")
@RequiredArgsConstructor
public class BalanceController {
    private final BalanceService balanceService;

    @GetMapping
    public GroupBalancesResponse getBalances(@PathVariable Long groupId,
                                             @AuthenticationPrincipal AppUserDetails currentUser) {
        return balanceService.getBalances(groupId, currentUser.getId());
    }
}
