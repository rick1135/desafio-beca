package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.ApiErrorResponse;
import com.beca.transactions.adapter.in.web.dto.ExpenseSeriesResponse;
import com.beca.transactions.adapter.in.web.dto.ExpensesByCategoryResponse;
import com.beca.transactions.application.usecase.GetExpenseSeriesService;
import com.beca.transactions.application.usecase.GetExpenseSeriesService.ExpenseSeries;
import com.beca.transactions.application.usecase.GetExpensesByCategoryService;
import com.beca.transactions.domain.model.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Análise de Despesas", description = "Agrupamento de despesas por categoria e séries temporais diárias e mensais")
@ApiResponse(
        responseCode = "503",
        description = "Serviço de autenticação do MS1 indisponível",
        content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))
)
@RestController
@RequestMapping("/expenses")
public class ExpenseController {
    private final GetExpensesByCategoryService expenses;
    private final GetExpenseSeriesService expenseSeries;

    public ExpenseController(GetExpensesByCategoryService expenses, GetExpenseSeriesService expenseSeries) {
        this.expenses = expenses;
        this.expenseSeries = expenseSeries;
    }

    @Operation(
            summary = "Despesas agrupadas por categoria",
            description = "Retorna o total gasto agrupado por categoria no período especificado. Se as datas forem omitidas, utiliza o mês corrente."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Despesas por categoria calculadas com sucesso",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ExpensesByCategoryResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Intervalo de datas inválido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @GetMapping("/by-category")
    public ResponseEntity<ExpensesByCategoryResponse> getExpensesByCategory(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Data inicial no formato ISO (YYYY-MM-DD)", example = "2026-11-01")
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Data final no formato ISO (YYYY-MM-DD)", example = "2026-11-30")
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        var summary = expenses.getExpenses(user.id(), startDate, endDate);
        return ResponseEntity.ok(ExpensesByCategoryResponse.from(summary));
    }

    @Operation(
            summary = "Série temporal diária de despesas",
            description = "Retorna a série temporal diária de despesas (YYYY-MM-DD) do usuário autenticado no período especificado."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Série temporal diária calculada com sucesso",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ExpenseSeriesResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Intervalo de datas inválido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @GetMapping("/daily")
    public ResponseEntity<ExpenseSeriesResponse> getDailyExpenses(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Data inicial no formato ISO (YYYY-MM-DD)", example = "2026-11-01")
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Data final no formato ISO (YYYY-MM-DD)", example = "2026-11-30")
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ExpenseSeries result = expenseSeries.daily(user.id(), startDate, endDate);
        return ResponseEntity.ok(ExpenseSeriesResponse.from(result));
    }

    @Operation(
            summary = "Série temporal mensal de despesas",
            description = "Retorna a série temporal mensal de despesas (YYYY-MM) do usuário autenticado no período especificado."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Série temporal mensal calculada com sucesso",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ExpenseSeriesResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Intervalo de datas inválido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @GetMapping("/monthly")
    public ResponseEntity<ExpenseSeriesResponse> getMonthlyExpenses(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Data inicial no formato ISO (YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Data final no formato ISO (YYYY-MM-DD)", example = "2026-12-31")
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ExpenseSeries result = expenseSeries.monthly(user.id(), startDate, endDate);
        return ResponseEntity.ok(ExpenseSeriesResponse.from(result));
    }
}
