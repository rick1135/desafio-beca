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

    public Transaction getById(Long id, Long userId) {
        Transaction transaction = transactions.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        boolean isRecipientOfApprovedTransfer = transaction.getType() == TransactionType.TRANSFER
                && transaction.getStatus() == TransactionStatus.APPROVED
                && userId.equals(transaction.getDestinationUserId());
        if (!transaction.getOriginUserId().equals(userId) && !isRecipientOfApprovedTransfer) {
            throw new ForbiddenTransactionAccessException();
        }
        return transaction;
    }
}
