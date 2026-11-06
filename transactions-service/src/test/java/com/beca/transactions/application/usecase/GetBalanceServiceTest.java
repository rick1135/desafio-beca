package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.AccountBalanceNotFoundException;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetBalanceServiceTest {
    @Mock private AccountBalancePort accounts;
    @Mock private TransactionRepositoryPort transactions;

    @Test
    void returnsEffectiveBalanceAndCurrentLimits() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(new AccountBalance(
                1L, money("1000.00"), money("800.00"), money("500.00"))));
        Transaction deposit = transaction(TransactionType.DEPOSIT, "200.00");
        Transaction purchase = transaction(TransactionType.PURCHASE, "100.00");
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of(deposit, purchase));

        var result = new GetBalanceService(accounts, transactions).getBalance(1L);

        assertEquals(0, money("1000.00").compareTo(result.openingBalance()));
        assertEquals(0, money("1100.00").compareTo(result.effectiveBalance()));
        assertEquals(0, money("100.00").compareTo(result.dailySpent()));
        assertEquals(0, money("800.00").compareTo(result.dailyLimit()));
        assertEquals(0, money("500.00").compareTo(result.transactionLimit()));
        verify(accounts).findByUserId(1L);
        verify(transactions, never())
                .findApprovedOutflowsByOriginUserIdBetween(any(), any(), any());
    }

    @Test
    void previousDayWithdrawalReducesBalanceButNotTodaysSpending() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(new AccountBalance(
                1L, money("1000.00"), money("800.00"), money("500.00"))));
        Instant previousDay = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
                .minusDays(1)
                .atTime(12, 0)
                .atZone(ZoneId.of("America/Sao_Paulo"))
                .toInstant();
        Transaction oldWithdrawal = transaction(TransactionType.WITHDRAW, "150.00", previousDay);
        Transaction todayPurchase = transaction(TransactionType.PURCHASE, "20.00");
        when(transactions.findApprovedByOriginUserId(1L))
                .thenReturn(List.of(oldWithdrawal, todayPurchase));

        var result = new GetBalanceService(accounts, transactions).getBalance(1L);

        assertEquals(0, money("830.00").compareTo(result.effectiveBalance()));
        assertEquals(0, money("20.00").compareTo(result.dailySpent()));
        verify(transactions, never())
                .findApprovedOutflowsByOriginUserIdBetween(any(), any(), any());
    }

    @Test
    void missingAccountDoesNotQueryTransactions() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(AccountBalanceNotFoundException.class,
                () -> new GetBalanceService(accounts, transactions).getBalance(1L));
        verifyNoInteractions(transactions);
    }

    @Test
    void propagatesExternalFailureInsteadOfReturningStaleBalance() {
        when(accounts.findByUserId(1L)).thenThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> new GetBalanceService(accounts, transactions).getBalance(1L));
        verifyNoInteractions(transactions);
    }

    private Transaction transaction(TransactionType type, String amount) {
        return transaction(type, amount, Instant.now());
    }

    private Transaction transaction(TransactionType type, String amount, Instant creationDate) {
        return new Transaction(null, type, money(amount),
                type == TransactionType.PURCHASE ? "Alimentação" : null,
                1L, null, TransactionStatus.APPROVED, null, null, "Teste", creationDate);
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
}
