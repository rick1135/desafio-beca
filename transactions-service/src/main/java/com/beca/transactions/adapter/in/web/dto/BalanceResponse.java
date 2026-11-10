package com.beca.transactions.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record BalanceResponse(
        @Schema(description = "ID do usuário autenticado", example = "1")
        Long userId,

        @Schema(description = "Saldo de abertura informado pela API mock bancária", example = "1000.00")
        BigDecimal openingBalance,

        @Schema(description = "Saldo efetivo consolidado com as transações aprovadas", example = "850.00")
        BigDecimal effectiveBalance,

        @Schema(description = "Total de despesas aprovadas no dia corrente", example = "150.00")
        BigDecimal dailySpent,

        @Schema(description = "Limite diário total disponível para transações", example = "5000.00")
        BigDecimal dailyLimit,

        @Schema(description = "Limite máximo permitido por transação individual", example = "2000.00")
        BigDecimal transactionLimit,

        @Schema(description = "Moeda dos valores monetários", example = "BRL")
        String currency
) {
}
