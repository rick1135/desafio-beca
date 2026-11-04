package com.beca.transactions.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, Long> {
}
