package com.beca.transactions.domain.port;

import com.beca.transactions.domain.model.Transaction;

import java.util.Optional;

public interface TransactionRepositoryPort {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(Long id);
}
