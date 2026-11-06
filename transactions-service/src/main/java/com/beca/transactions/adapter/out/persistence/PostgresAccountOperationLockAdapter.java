package com.beca.transactions.adapter.out.persistence;

import com.beca.transactions.domain.port.AccountOperationLockPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class PostgresAccountOperationLockAdapter implements AccountOperationLockPort {
    private final JdbcTemplate jdbcTemplate;

    public PostgresAccountOperationLockAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lockForUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("ID do usuário inválido");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("O bloqueio exige uma transação ativa");
        }

        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?)",
                (RowCallbackHandler) rs -> { },
                userId
        );
    }
}
