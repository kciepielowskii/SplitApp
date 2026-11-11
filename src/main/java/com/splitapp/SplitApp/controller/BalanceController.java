package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.GroupBalancesResponse;
import com.splitapp.SplitApp.service.BalanceService;
import lombok.RequiredArgsConstructor;
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
    public GroupBalancesResponse getBalances(@PathVariable Long groupId) {
        return balanceService.getBalances(groupId);
    }
}
