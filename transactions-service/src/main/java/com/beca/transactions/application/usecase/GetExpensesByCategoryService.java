package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class GetExpensesByCategoryService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<TransactionType> EXPENSE_TYPES = Set.of(
            TransactionType.PURCHASE,
            TransactionType.WITHDRAW,
            TransactionType.TRANSFER
    );

    private final TransactionRepositoryPort transactions;
    private final Clock clock;

    @Autowired
    public GetExpensesByCategoryService(TransactionRepositoryPort transactions) {
        this(transactions, Clock.system(ZONE));
    }

    GetExpensesByCategoryService(TransactionRepositoryPort transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    public ExpenseSummary getExpenses(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            YearMonth month = YearMonth.now(clock);
            startDate = month.atDay(1);
            endDate = month.atEndOfMonth();
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidTransactionException("A data inicial deve ser anterior ou igual à data final.");
        }

        List<Transaction> approved = transactions.findApprovedByOriginUserId(userId);
        Set<Long> replacedIds = approved.stream()
                .filter(t -> t.getStatus() == TransactionStatus.APPROVED && t.isCorrection())
                .map(Transaction::getOriginalTransactionId)
                .collect(Collectors.toSet());

        Map<String, BigDecimal> totalsByCategory = new TreeMap<>();

        for (Transaction transaction : approved) {
            if (transaction.getStatus() != TransactionStatus.APPROVED
                    || !EXPENSE_TYPES.contains(transaction.getType())
                    || replacedIds.contains(transaction.getId())) {
                continue;
            }

            LocalDate transactionDate = LocalDate.ofInstant(transaction.getCreationDate(), ZONE);
            if ((startDate != null && transactionDate.isBefore(startDate))
                    || (endDate != null && transactionDate.isAfter(endDate))) {
                continue;
            }

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
