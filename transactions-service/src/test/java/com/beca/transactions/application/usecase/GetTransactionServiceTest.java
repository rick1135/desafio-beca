package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    @DisplayName("permite consulta de transferência aprovada pelo destinatário")
    void testGetById_WhenUserIsRecipientOfApprovedTransfer_ShouldReturnTransaction() {
        // given
        Transaction transfer = new Transaction(42L, TransactionType.TRANSFER, new BigDecimal("100.00"),
                null, 1L, 2L, TransactionStatus.APPROVED, null,
                null, "Transferência", Instant.parse("2026-11-06T12:00:00Z")
        );
        when(transactions.findById(42L)).thenReturn(Optional.of(transfer));

        // when
        Transaction found = new GetTransactionService(transactions).getById(42L, 2L);

        // then
        assertSame(transfer, found);
    }

    @Test
    @DisplayName("bloqueia acesso do destinatário a transferência rejeitada")
    void testGetById_WhenUserIsRecipientOfRejectedTransfer_ShouldThrowForbidden() {
        // given
        Transaction transfer = new Transaction(42L, TransactionType.TRANSFER,
                new BigDecimal("100.00"), null, 1L, 2L, TransactionStatus.REJECTED,
                "Saldo insuficiente", null, "Transferência", Instant.parse("2026-11-06T12:00:00Z"));
        when(transactions.findById(42L)).thenReturn(Optional.of(transfer));

        // when then
        assertThrows(ForbiddenTransactionAccessException.class,
                () -> new GetTransactionService(transactions).getById(42L, 2L));
    }

    @Test
    @DisplayName("lista todas as transações visíveis do usuário quando não há filtros")
    void testList_WhenNoFiltersProvided_ShouldReturnAllVisibleTransactions() {
        // given
        Transaction first = transaction(1L, TransactionType.DEPOSIT, Instant.parse("2026-11-06T15:00:00Z"));
        Transaction second = transaction(1L, TransactionType.PURCHASE, Instant.parse("2026-11-05T15:00:00Z"));
        when(transactions.findVisibleByUserId(1L)).thenReturn(List.of(first, second));

        // when
        List<Transaction> result = new GetTransactionService(transactions).list(1L, null, null, null);

        // then
        assertEquals(List.of(first, second), result);
    }

    @Test
    @DisplayName("filtra transações por período no fuso de São Paulo e por tipo")
    void testList_WhenFilteringByDateRangeAndType_ShouldRespectSaoPauloTimezoneAndType() {
        // given
        Transaction beforeRange = transaction(1L, TransactionType.PURCHASE, Instant.parse("2026-11-06T02:59:59Z"));
        Transaction matchingStart = transaction(1L, TransactionType.PURCHASE, Instant.parse("2026-11-06T03:00:00Z"));
        Transaction matchingEnd = transaction(1L, TransactionType.PURCHASE, Instant.parse("2026-11-07T02:59:59Z"));
        Transaction otherTypeInRange = transaction(1L, TransactionType.DEPOSIT, Instant.parse("2026-11-06T12:00:00Z"));
        Transaction afterRange = transaction(1L, TransactionType.PURCHASE, Instant.parse("2026-11-07T03:00:00Z"));
        when(transactions.findVisibleByUserId(1L)).thenReturn(List.of(
                afterRange, matchingEnd, otherTypeInRange, matchingStart, beforeRange
        ));

        // when
        List<Transaction> result = new GetTransactionService(transactions).list(
                1L,
                LocalDate.parse("2026-11-06"),
                LocalDate.parse("2026-11-06"),
                TransactionType.PURCHASE
        );

        // then
        assertEquals(List.of(matchingEnd, matchingStart), result);
    }

    @Test
    @DisplayName("lança exceção 400 quando data inicial for posterior à data final")
    void testList_WhenStartDateIsAfterEndDate_ShouldThrowInvalidTransactionException() {
        // given
        LocalDate start = LocalDate.parse("2026-11-10");
        LocalDate end = LocalDate.parse("2026-11-09");

        // when / then
        assertThrows(InvalidTransactionException.class,
                () -> new GetTransactionService(transactions).list(1L, start, end, null));
        verifyNoInteractions(transactions);
    }

    private Transaction transaction(Long originUserId) {
        return transaction(originUserId, TransactionType.DEPOSIT, Instant.parse("2026-11-06T12:00:00Z"));
    }

    private Transaction transaction(Long originUserId, TransactionType type, Instant creationDate) {
        return new Transaction(42L, type, new BigDecimal("100.00"),
                type == TransactionType.PURCHASE ? "Alimentação" : null,
                originUserId, null, TransactionStatus.APPROVED, null, null,
                "Transação de teste", creationDate);
    }
}
