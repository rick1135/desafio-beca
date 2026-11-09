package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class GetTransactionService {
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

    private boolean canAccess(Transaction transaction, Long userId) {
        if (transaction.getOriginUserId().equals(userId)) {
            return true;
        }
        return transaction.getType() == TransactionType.TRANSFER
                && transaction.getStatus() == TransactionStatus.APPROVED
                && userId.equals(transaction.getDestinationUserId());
    }
}
