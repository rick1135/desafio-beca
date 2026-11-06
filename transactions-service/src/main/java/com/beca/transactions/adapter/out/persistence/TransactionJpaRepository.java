package com.beca.transactions.adapter.out.persistence;

import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, Long> {
    List<TransactionEntity> findByOriginUserIdAndStatus(
            Long originUserId, TransactionStatus status
    );

    List<TransactionEntity> findByOriginUserIdAndStatusAndTypeInAndCreationDateGreaterThanEqualAndCreationDateLessThan(
            Long originUserId, TransactionStatus status, Collection<TransactionType> types,
            Instant startInclusive,
            Instant endExclusive
    );

    List<TransactionEntity> findByDestinationUserIdAndTypeAndStatus(
            Long destinationUserId,
            TransactionType type,
            TransactionStatus status
    );
}
