package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.BalanceResponse;
import com.beca.transactions.application.usecase.GetBalanceService;
import com.beca.transactions.domain.model.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BalanceController {
    private final GetBalanceService getBalanceService;

    public BalanceController(GetBalanceService getBalanceService){
        this.getBalanceService=getBalanceService;
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(@AuthenticationPrincipal AuthenticatedUser user){
        var balance = getBalanceService.getBalance(user.id());

        return ResponseEntity.ok(new BalanceResponse(
                balance.userId(),
                balance.openingBalance(),
                balance.effectiveBalance(),
                balance.dailySpent(),
                balance.dailyLimit(),
                balance.transactionLimit(),
                "BRL"
        ));
    }
}
