package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExpenseSelection {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<TransactionType> EXPENSE_TYPES = Set.of(
            TransactionType.PURCHASE, TransactionType.WITHDRAW, TransactionType.TRANSFER
    );

    private final TransactionRepositoryPort transactions;
    private final Clock clock;

    @Autowired
    public ExpenseSelection(TransactionRepositoryPort transactions) {
        this(transactions, Clock.system(ZONE));
    }

    ExpenseSelection(TransactionRepositoryPort transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock.withZone(ZONE);
    }

    List<DatedExpense> find(Long userId, LocalDate startDate, LocalDate endDate) {
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

        List<DatedExpense> expenses = new ArrayList<>();
        for (Transaction transaction : approved) {
            if (transaction.getStatus() != TransactionStatus.APPROVED
                    || !EXPENSE_TYPES.contains(transaction.getType())
                    || replacedIds.contains(transaction.getId())) {
                continue;
            }

            LocalDate date = LocalDate.ofInstant(transaction.getCreationDate(), ZONE);
            if ((startDate != null && date.isBefore(startDate))
                    || (endDate != null && date.isAfter(endDate))) {
                continue;
            }

            expenses.add(new DatedExpense(date, transaction));

        }
        return expenses;
    }

    record DatedExpense(LocalDate date, Transaction transaction) {}
}
