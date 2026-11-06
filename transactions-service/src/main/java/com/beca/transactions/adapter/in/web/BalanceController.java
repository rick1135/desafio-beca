package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.BalanceResponse;
import com.beca.transactions.application.usecase.GetBalanceService;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BalanceController {
    private final GetBalanceService getBalanceService;

    public BalanceController(GetBalanceService getBalanceService){
        this.getBalanceService=getBalanceService;
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(@RequestHeader("X-User-Id") Long userId){
        if (userId <= 0) {
            throw new InvalidTransactionException("ID do usuário inválido");
        }
        var balance = getBalanceService.getBalance(userId);

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
