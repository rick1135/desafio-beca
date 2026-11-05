package com.beca.transactions.adapter.out.persistence;

import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
        TransactionEntity savedEntity = repository.saveAndFlush(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return repository.findById(id).map(entity -> toDomain(entity));
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
                transaction.getCreationDate()
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
                entity.getCreationDate()
        );
    }
}
