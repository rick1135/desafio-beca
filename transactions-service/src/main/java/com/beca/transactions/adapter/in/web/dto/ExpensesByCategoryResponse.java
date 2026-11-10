package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.application.usecase.GetExpensesByCategoryService.ExpenseSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record ExpensesByCategoryResponse(
        @Schema(description = "Moeda dos valores", example = "BRL")
        String currency,

        @Schema(description = "Valor total acumulado das despesas no período", example = "1250.00")
        BigDecimal total,

        @Schema(description = "Lista de despesas consolidadas por categoria")
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
            @Schema(description = "Nome da categoria", example = "ALIMENTACAO")
            String category,

            @Schema(description = "Total gasto na categoria", example = "500.00")
            BigDecimal total,

            @Schema(description = "Percentual da categoria sobre o total de despesas", example = "40.00")
            BigDecimal percentage
    ) {}
}
