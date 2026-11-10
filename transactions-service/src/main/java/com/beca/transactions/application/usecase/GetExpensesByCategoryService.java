package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.model.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class GetExpensesByCategoryService {
    private final ExpenseSelection expenseSelection;

    public GetExpensesByCategoryService(ExpenseSelection expenseSelection) {
        this.expenseSelection = expenseSelection;
    }

    public ExpenseSummary getExpenses(Long userId, LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> totalsByCategory = new TreeMap<>();
        for (var expense : expenseSelection.find(userId, startDate, endDate)) {
            Transaction transaction = expense.transaction();
            totalsByCategory.merge(categoryOf(transaction), transaction.getAmount(), BigDecimal::add);
        }

        BigDecimal total = totalsByCategory.values()
                .stream()
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        List<CategoryExpense> categories = totalsByCategory.entrySet().stream()
                .map(entry -> new CategoryExpense(
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue().movePointRight(2)
                                .divide(total, 2, RoundingMode.HALF_UP)
                ))
                .toList();

        return new ExpenseSummary("BRL", total, categories);
    }

    private static String categoryOf(Transaction transaction) {
        String category = transaction.getCategory();
        if (category != null && !category.isBlank()) {
            return category.trim();
        }

        return switch (transaction.getType()) {
            case TRANSFER -> "Transferência";
            case WITHDRAW -> "Retirada";
            default -> "Sem categoria";
        };
    }

    public record ExpenseSummary(
            String currency,
            BigDecimal total,
            List<CategoryExpense> categories
    ) {}

    public record CategoryExpense(
            String category,
            BigDecimal total,
            BigDecimal percentage
    ) {}
}
