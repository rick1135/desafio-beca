package com.beca.transactions.adapter.out.persistence;

import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
        select t from TransactionEntity t
        where t.originUserId = :userId
           or (t.destinationUserId = :userId
               and t.type = :transferType
               and t.status = :approvedStatus)
        order by t.creationDate desc, t.id desc
        """)
    List<TransactionEntity> findVisibleByUserId(
            @Param("userId") Long userId,
            @Param("transferType") TransactionType transferType,
            @Param("approvedStatus") TransactionStatus approvedStatus
    );
}
