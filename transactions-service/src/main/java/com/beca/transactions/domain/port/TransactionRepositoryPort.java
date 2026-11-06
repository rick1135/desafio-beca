package com.beca.transactions.domain.port;

import com.beca.transactions.domain.model.Transaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepositoryPort {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(Long id);
    List<Transaction> findApprovedByOriginUserId(Long userId);
    List<Transaction> findApprovedOutflowsByOriginUserIdBetween(
            Long userId, Instant startInclusive, Instant endExclusive
    );
}
