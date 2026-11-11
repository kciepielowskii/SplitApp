package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.GroupBalancesResponse;
import com.splitapp.SplitApp.dto.MemberBalanceResponse;
import com.splitapp.SplitApp.dto.SettlementTransferResponse;
import com.splitapp.SplitApp.exception.GroupNotFoundException;
import com.splitapp.SplitApp.service.BalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
class BalanceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BalanceService balanceService;

    @Test
    void shouldReturnBalances() throws Exception {
        GroupBalancesResponse response = new GroupBalancesResponse(1L,
                List.of(new MemberBalanceResponse(1L, "Jan", "Kowalski", new BigDecimal("5.00")),
                        new MemberBalanceResponse(2L, "Anna", "Nowak", new BigDecimal("-5.00"))),
                List.of(new SettlementTransferResponse(2L, 1L, new BigDecimal("5.00"))));
        when(balanceService.getBalances(1L)).thenReturn(response);

        mockMvc.perform(get("/groups/1/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(1))
                .andExpect(jsonPath("$.balances[0].balance").value(5.0))
                .andExpect(jsonPath("$.balances[1].balance").value(-5.0))
                .andExpect(jsonPath("$.transfers[0].fromUserId").value(2))
                .andExpect(jsonPath("$.transfers[0].toUserId").value(1));
    }

    @Test
    void shouldReturn404WhenGroupDoesNotExist() throws Exception {
        when(balanceService.getBalances(99L)).thenThrow(new GroupNotFoundException("Group not found"));

        mockMvc.perform(get("/groups/99/balances"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Group not found"));
    }
}
