package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.application.usecase.GetExpensesByCategoryService.ExpenseSummary;

import java.math.BigDecimal;
import java.util.List;

public record ExpensesByCategoryResponse(
        String currency,
        BigDecimal total,
        List<CategoryExpenseResponse> categories
) {
    public static ExpensesByCategoryResponse from(ExpenseSummary summary) {
        List<CategoryExpenseResponse> categories = summary.categories().stream()
                .map(item -> new CategoryExpenseResponse(
                        item.category(),
                        item.total(),
                        item.percentage()
                ))
                .toList();
        return new ExpensesByCategoryResponse(
                summary.currency(),
                summary.total(),
                categories
        );
    }

    public record CategoryExpenseResponse(
            String category,
            BigDecimal total,
            BigDecimal percentage
    ) {}
}
