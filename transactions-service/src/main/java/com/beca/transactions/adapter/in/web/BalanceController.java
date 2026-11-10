package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.ApiErrorResponse;
import com.beca.transactions.adapter.in.web.dto.BalanceResponse;
import com.beca.transactions.application.usecase.GetBalanceService;
import com.beca.transactions.domain.model.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Saldo e Limites", description = "Consulta de saldo e limites da conta do usuário autenticado")
@RestController
public class BalanceController {
    private final GetBalanceService getBalanceService;

    public BalanceController(GetBalanceService getBalanceService){
        this.getBalanceService=getBalanceService;
    }

    @Operation(
            summary = "Consultar saldo e limites",
            description = "Retorna o saldo de abertura, saldo efetivo consolidado, total de despesas gastas no dia e os limites diário e por transação."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Saldo e limites consultados com sucesso",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = BalanceResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
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
