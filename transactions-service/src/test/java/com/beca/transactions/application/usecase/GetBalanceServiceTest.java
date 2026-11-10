package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.AccountBalanceNotFoundException;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
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
    void sameTransferDebitsSenderAndCreditsRecipientWithoutSpendingRecipientsDailyLimit() {
        Transaction transfer = new Transaction(null, TransactionType.TRANSFER, money("75.00"),
                null, 1L, 2L, TransactionStatus.APPROVED, null, null,
                "Transferência", Instant.now());
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(new AccountBalance(
                1L, money("100.00"), money("800.00"), money("500.00"))));
        when(accounts.findByUserId(2L)).thenReturn(Optional.of(new AccountBalance(
                2L, money("20.00"), money("800.00"), money("500.00"))));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of(transfer));
        when(transactions.findApprovedByOriginUserId(2L)).thenReturn(List.of());
        when(transactions.findApprovedIncomingTransfersByUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedIncomingTransfersByUserId(2L)).thenReturn(List.of(transfer));

        GetBalanceService service = new GetBalanceService(accounts, transactions);
        var sender = service.getBalance(1L);
        var recipient = service.getBalance(2L);

        assertEquals(0, money("25.00").compareTo(sender.effectiveBalance()));
        assertEquals(0, money("75.00").compareTo(sender.dailySpent()));
        assertEquals(0, money("95.00").compareTo(recipient.effectiveBalance()));
        assertEquals(0, money("0.00").compareTo(recipient.dailySpent()));
        verify(transactions).findApprovedIncomingTransfersByUserId(1L);
        verify(transactions).findApprovedIncomingTransfersByUserId(2L);
    }

    @Test
    @DisplayName("correção de transferência estorna crédito do destinatário original e credita o novo destinatário")
    void correctedTransferRemovesOriginalRecipientCreditAndCreditsNewRecipient() {
        // given
        Instant now = Instant.now();
        Transaction original = new Transaction(42L, TransactionType.TRANSFER, money("100.00"),
                null, 1L, 2L, TransactionStatus.APPROVED, null, null,
                "Transferência original", now);
        Transaction correction = new Transaction(43L, TransactionType.TRANSFER, money("80.00"),
                null, 1L, 3L, TransactionStatus.APPROVED, null, 42L,
                "Transferência corrigida", now.plusSeconds(1));
        for (Long userId : List.of(1L, 2L, 3L)) {
            doReturn(Optional.of(new AccountBalance(
                    userId, userId == 1L ? money("1000.00") : money("0.00"),
                    money("800.00"), money("500.00"))))
                    .when(accounts).findByUserId(userId);
        }
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of(original, correction));
        when(transactions.findApprovedIncomingTransfersByUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedIncomingTransfersByUserId(2L))
                .thenReturn(List.of(original, correction));
        when(transactions.findApprovedIncomingTransfersByUserId(3L))
                .thenReturn(List.of(correction));

        GetBalanceService service = new GetBalanceService(accounts, transactions);

        // when
        var sender = service.getBalance(1L);
        var oldRecipient = service.getBalance(2L);
        var newRecipient = service.getBalance(3L);

        // then
        assertEquals(money("920.00"), sender.effectiveBalance());
        assertEquals(money("80.00"), sender.dailySpent());
        assertEquals(0, money("0.00").compareTo(oldRecipient.effectiveBalance()));
        assertEquals(0, money("0.00").compareTo(oldRecipient.dailySpent()));
        assertEquals(money("80.00"), newRecipient.effectiveBalance());
        assertEquals(0, money("0.00").compareTo(newRecipient.dailySpent()));
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
