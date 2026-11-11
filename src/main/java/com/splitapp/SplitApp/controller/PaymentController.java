package com.splitapp.SplitApp.controller;

import com.splitapp.SplitApp.dto.CreatePaymentRequest;
import com.splitapp.SplitApp.dto.PaymentResponse;
import com.splitapp.SplitApp.security.AppUserDetails;
import com.splitapp.SplitApp.service.PaymentService;
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
@RequestMapping("/groups/{groupId}/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@PathVariable Long groupId,
                                         @Valid @RequestBody CreatePaymentRequest request,
                                         @AuthenticationPrincipal AppUserDetails currentUser) {
        return paymentService.createPayment(groupId, currentUser.getId(), request);
    }

    @GetMapping
    public List<PaymentResponse> getPayments(@PathVariable Long groupId,
                                             @AuthenticationPrincipal AppUserDetails currentUser) {
        return paymentService.getPayments(groupId, currentUser.getId());
    }

    @DeleteMapping("/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long groupId, @PathVariable Long paymentId,
                              @AuthenticationPrincipal AppUserDetails currentUser) {
        paymentService.deletePayment(groupId, currentUser.getId(), paymentId);
    }
}
