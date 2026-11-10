package com.beca.transactions.application.usecase;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

@Service
public class GetExpenseSeriesService {
    private final ExpenseSelection expenseSelection;

    public GetExpenseSeriesService(ExpenseSelection expenseSelection) {
        this.expenseSelection = expenseSelection;
    }

    public ExpenseSeries daily(Long userId, LocalDate startDate, LocalDate endDate) {
        return aggregate(userId, startDate, endDate, Function.<LocalDate>identity());
    }

    public ExpenseSeries monthly(Long userId, LocalDate startDate, LocalDate endDate) {
        return aggregate(userId, startDate, endDate, YearMonth::from);
    }

    private <P extends Comparable<? super P>> ExpenseSeries aggregate(
            Long userId, LocalDate startDate, LocalDate endDate, Function<LocalDate, P> periodOf
    ) {
        Map<P, BigDecimal> totalsByPeriod = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (var expense : expenseSelection.find(userId, startDate, endDate)) {
            BigDecimal amount = expense.transaction().getAmount();
            totalsByPeriod.merge(periodOf.apply(expense.date()), amount, BigDecimal::add);
            total = total.add(amount);
        }

        List<ExpensePoint> series = totalsByPeriod.entrySet().stream()
                .map(entry -> new ExpensePoint(entry.getKey().toString(), entry.getValue()))
                .toList();
        return new ExpenseSeries("BRL", total, series);
    }

    public record ExpenseSeries(String currency, BigDecimal total, List<ExpensePoint> series) {}

    public record ExpensePoint(String period, BigDecimal total) {}
}
