package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class GetTransactionService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final TransactionRepositoryPort transactions;

    public GetTransactionService(TransactionRepositoryPort transactions) {
        this.transactions = transactions;
    }

    public Transaction getById(Long id, Long authenticatedUserId) {
        Transaction transaction = transactions.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        if (!canAccess(transaction, authenticatedUserId)) {
            throw new ForbiddenTransactionAccessException();
        }

        return transaction;
    }

    public List<Transaction> list(Long authenticatedUserId, LocalDate startDate, LocalDate endDate, TransactionType type) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidTransactionException("A data inicial deve ser anterior ou igual à data final.");
        }

        Instant startInclusive = startDate == null ? null : startDate.atStartOfDay(ZONE).toInstant();
        Instant endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay(ZONE).toInstant();

        return transactions.findVisibleByUserId(authenticatedUserId).stream()
                .filter(t -> startInclusive == null || !t.getCreationDate().isBefore(startInclusive))
                .filter(t -> endExclusive == null || t.getCreationDate().isBefore(endExclusive))
                .filter(t -> type == null || t.getType() == type)
                .toList();
    }

    private boolean canAccess(Transaction transaction, Long userId) {
        if (transaction.getOriginUserId().equals(userId)) {
            return true;
        }
        return transaction.getType() == TransactionType.TRANSFER
                && transaction.getStatus() == TransactionStatus.APPROVED
                && userId.equals(transaction.getDestinationUserId());
    }
}
