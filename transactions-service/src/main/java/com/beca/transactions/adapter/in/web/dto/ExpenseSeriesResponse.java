package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.application.usecase.GetExpenseSeriesService.ExpenseSeries;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseSeriesResponse(
        String currency,
        BigDecimal total,
        List<ExpensePointResponse> series
) {
    public static ExpenseSeriesResponse from(ExpenseSeries domain) {
        List<ExpensePointResponse> points = domain.series().stream()
                .map(p -> new ExpensePointResponse(p.period(), p.total()))
                .toList();
        return new ExpenseSeriesResponse(domain.currency(), domain.total(), points);
    }

    public record ExpensePointResponse(
            String period,
            BigDecimal total
    ) {}
}
