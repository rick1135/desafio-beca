package com.beca.transactions.adapter.out.persistence;

import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;

import com.beca.transactions.domain.exception.InvalidTransactionException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.sql.SQLException;
import java.util.Locale;

@Repository
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {
    private final TransactionJpaRepository repository;

    public TransactionPersistenceAdapter(TransactionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        if(transaction.getId()!=null) throw new IllegalArgumentException("" +
                "Uma nova transação não pode ter ID preenchido");
        TransactionEntity entity = toEntity(transaction);
        try {
            TransactionEntity savedEntity = repository.saveAndFlush(entity);
            return toDomain(savedEntity);
        } catch (DataIntegrityViolationException ex) {
            if (transaction.isCorrection() && isCorrectionUniqueViolation(ex)) {
                throw new InvalidTransactionException("Esta transação já foi corrigida");
            }
            throw ex;
        }
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return repository.findById(id).map(entity -> toDomain(entity));
    }

    @Override
    public boolean existsByOriginalTransactionIdAndStatus(Long originalTransactionId, TransactionStatus status) {
        return repository.existsByOriginalTransactionIdAndStatus(originalTransactionId, status);
    }

    private boolean isCorrectionUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && String.valueOf(sql.getMessage()).toLowerCase(Locale.ROOT)
                    .contains("uk_transactions_original_transaction_id")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Transaction> findCorrectionsByOriginalTransactionId(Long originalTransactionId, Long originUserId) {
        return repository.findByOriginalTransactionIdAndOriginUserIdOrderByCreationDateDescIdDesc(
                        originalTransactionId, originUserId
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findApprovedByOriginUserId(Long userId) {
        return repository
                .findByOriginUserIdAndStatus(userId, TransactionStatus.APPROVED)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findApprovedOutflowsByOriginUserIdBetween(
            Long userId,
            Instant startInclusive,
            Instant endExclusive
    ) {
        return repository
                .findByOriginUserIdAndStatusAndTypeInAndCreationDateGreaterThanEqualAndCreationDateLessThan(
                        userId,
                        TransactionStatus.APPROVED,
                        List.of(TransactionType.WITHDRAW, TransactionType.TRANSFER, TransactionType.PURCHASE
                        ),
                        startInclusive,
                        endExclusive
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findApprovedIncomingTransfersByUserId(Long userId) {
        return repository.findIncomingTransfersAndCorrections(
                        userId,
                        TransactionType.TRANSFER,
                        TransactionStatus.APPROVED
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findVisibleByUserId(Long userId) {
        return repository.findVisibleByUserId(userId, TransactionType.TRANSFER,
                        TransactionStatus.APPROVED)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private TransactionEntity toEntity(Transaction transaction) {
        return new TransactionEntity(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getOriginUserId(),
                transaction.getDestinationUserId(),
                transaction.getStatus(),
                transaction.getRejectionReason(),
                transaction.getOriginalTransactionId(),
                transaction.getDescription(),
                transaction.getCreationDate(),
                transaction.getExchangeRates(),
                transaction.getCorrectionReason()
        );
    }

    private Transaction toDomain(TransactionEntity entity) {
        return new Transaction(
                entity.getId(),
                entity.getType(),
                entity.getAmount(),
                entity.getCategory(),
                entity.getOriginUserId(),
                entity.getDestinationUserId(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getOriginalTransactionId(),
                entity.getDescription(),
                entity.getCreationDate(),
                entity.getExchangeRates(),
                entity.getCorrectionReason()
        );
    }
}
