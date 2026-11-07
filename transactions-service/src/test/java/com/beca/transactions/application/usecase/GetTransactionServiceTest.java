package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTransactionServiceTest {
    @Mock private TransactionRepositoryPort transactions;

    @Test
    @DisplayName("retorna transação quando pertence ao usuário autenticado")
    void testGetById_WhenTransactionBelongsToUser_ShouldReturnTransaction() {
        // given
        Transaction transaction = transaction(1L);
        when(transactions.findById(42L)).thenReturn(Optional.of(transaction));

        // when
        Transaction found = new GetTransactionService(transactions).getById(42L, 1L);

        // then
        assertSame(transaction, found);
    }

    @Test
    @DisplayName("lança exceção 404 quando transação não existe")
    void testGetById_WhenTransactionIsMissing_ShouldThrowNotFound() {
        // given
        when(transactions.findById(42L)).thenReturn(Optional.empty());

        // when then
        assertThrows(TransactionNotFoundException.class,
                () -> new GetTransactionService(transactions).getById(42L, 1L));
    }

    @Test
    @DisplayName("lança exceção 403 quando transação pertence a outro usuário")
    void testGetById_WhenTransactionBelongsToAnotherUser_ShouldThrowForbidden() {
        // given
        when(transactions.findById(42L)).thenReturn(Optional.of(transaction(1L)));

        // when then
        assertThrows(ForbiddenTransactionAccessException.class,
                () -> new GetTransactionService(transactions).getById(42L, 2L));
    }

    private Transaction transaction(Long originUserId) {
        return new Transaction(42L, TransactionType.DEPOSIT, new BigDecimal("100.00"),
                "Depósito", originUserId, null, TransactionStatus.APPROVED, null, null,
                "Depósito de teste", Instant.parse("2026-11-06T12:00:00Z"));
    }
}
