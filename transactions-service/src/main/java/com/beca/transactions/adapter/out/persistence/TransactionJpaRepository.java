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
    boolean existsByOriginalTransactionId(Long originalTransactionId);
    List<TransactionEntity> findByOriginalTransactionIdAndOriginUserIdOrderByCreationDateDescIdDesc(
            Long originalTransactionId, Long originUserId
    );
    List<TransactionEntity> findByOriginUserIdAndStatus(
            Long originUserId, TransactionStatus status
    );

    List<TransactionEntity> findByOriginUserIdAndStatusAndTypeInAndCreationDateGreaterThanEqualAndCreationDateLessThan(
            Long originUserId, TransactionStatus status, Collection<TransactionType> types,
            Instant startInclusive,
            Instant endExclusive
    );

    @Query("""
        select t from TransactionEntity t
        where t.type = :type and t.status = :status
          and (t.destinationUserId = :userId
               or t.originalTransactionId in
                  (select original.id from TransactionEntity original
                   where original.type = :type and original.destinationUserId = :userId))
        """)
    List<TransactionEntity> findIncomingTransfersAndCorrections(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status
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
