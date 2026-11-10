package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.application.usecase.GetExpenseSeriesService.ExpenseSeries;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseSeriesResponse(
        @Schema(description = "Moeda dos valores", example = "BRL")
        String currency,

        @Schema(description = "Valor total acumulado na série temporal", example = "3500.00")
        BigDecimal total,

        @Schema(description = "Pontos da série temporal ordenados cronologicamente")
        List<ExpensePointResponse> series
) {
    public static ExpenseSeriesResponse from(ExpenseSeries domain) {
        List<ExpensePointResponse> points = domain.series().stream()
                .map(p -> new ExpensePointResponse(p.period(), p.total()))
                .toList();
        return new ExpenseSeriesResponse(domain.currency(), domain.total(), points);
    }

    public record ExpensePointResponse(
            @Schema(description = "Período correspondente: YYYY-MM-DD (diário) ou YYYY-MM (mensal)", example = "2026-11-10")
            String period,

            @Schema(description = "Total gasto no período", example = "250.00")
            BigDecimal total
    ) {}
}
