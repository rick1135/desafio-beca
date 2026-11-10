package com.beca.transactions.domain.port;

import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepositoryPort {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(Long id);
    boolean existsByOriginalTransactionIdAndStatus(Long originalTransactionId, TransactionStatus status);
    List<Transaction> findCorrectionsByOriginalTransactionId(Long originalTransactionId, Long originUserId);
    List<Transaction> findApprovedByOriginUserId(Long userId);
    List<Transaction> findApprovedOutflowsByOriginUserIdBetween(
            Long userId, Instant startInclusive, Instant endExclusive
    );
    List<Transaction> findApprovedIncomingTransfersByUserId(Long userId);
    List<Transaction> findVisibleByUserId(Long userId);
}
