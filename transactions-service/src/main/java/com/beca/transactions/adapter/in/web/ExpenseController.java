package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.ExpenseSeriesResponse;
import com.beca.transactions.adapter.in.web.dto.ExpensesByCategoryResponse;
import com.beca.transactions.application.usecase.GetExpenseSeriesService;
import com.beca.transactions.application.usecase.GetExpenseSeriesService.ExpenseSeries;
import com.beca.transactions.application.usecase.GetExpensesByCategoryService;
import com.beca.transactions.domain.model.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {
    private final GetExpensesByCategoryService expenses;
    private final GetExpenseSeriesService expenseSeries;

    public ExpenseController(GetExpensesByCategoryService expenses, GetExpenseSeriesService expenseSeries) {
        this.expenses = expenses;
        this.expenseSeries = expenseSeries;
    }

    @GetMapping("/by-category")
    public ResponseEntity<ExpensesByCategoryResponse> getExpensesByCategory(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "dataInicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(name = "dataFim", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        LocalDate effectiveStartDate = startDate != null ? startDate : dataInicio;
        LocalDate effectiveEndDate = endDate != null ? endDate : dataFim;
        var summary = expenses.getExpenses(user.id(), effectiveStartDate, effectiveEndDate);
        return ResponseEntity.ok(ExpensesByCategoryResponse.from(summary));
    }

    @GetMapping("/daily")
    public ResponseEntity<ExpenseSeriesResponse> getDailyExpenses(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "dataInicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(name = "dataFim", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        LocalDate effectiveStartDate = startDate != null ? startDate : dataInicio;
        LocalDate effectiveEndDate = endDate != null ? endDate : dataFim;
        ExpenseSeries result = expenseSeries.daily(user.id(), effectiveStartDate, effectiveEndDate);
        return ResponseEntity.ok(ExpenseSeriesResponse.from(result));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ExpenseSeriesResponse> getMonthlyExpenses(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "dataInicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(name = "dataFim", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        LocalDate effectiveStartDate = startDate != null ? startDate : dataInicio;
        LocalDate effectiveEndDate = endDate != null ? endDate : dataFim;
        ExpenseSeries result = expenseSeries.monthly(user.id(), effectiveStartDate, effectiveEndDate);
        return ResponseEntity.ok(ExpenseSeriesResponse.from(result));
    }
}

