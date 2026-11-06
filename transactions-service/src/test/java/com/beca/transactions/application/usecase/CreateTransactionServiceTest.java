package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.AccountOperationLockPort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateTransactionServiceTest {
    @Mock private TransactionRepositoryPort transactions;
    @Mock private AccountBalancePort accounts;
    @Mock private AccountOperationLockPort accountLock;

    @Test
    void approvesDepositWithoutConsultingMockApi() {
        CreateTransactionService service = new CreateTransactionService(transactions, accounts, accountLock);
        service.create(1L, TransactionType.DEPOSIT, money("25.00"), "Depósito", null, null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.APPROVED, saved.getStatus());
        assertNull(saved.getRejectionReason());
        verifyNoInteractions(accounts);
        verify(transactions, never()).findApprovedByOriginUserId(any());

        InOrder order = inOrder(accountLock, transactions);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).save(any(Transaction.class));
    }

    @Test
    void approvesWithdrawalWithinBalanceAndBothLimits() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("1000.00", "800.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());

        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        assertEquals(TransactionStatus.APPROVED, capturedTransaction().getStatus());

        InOrder order = inOrder(accounts, accountLock, transactions);
        order.verify(accounts).findByUserId(1L);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).findApprovedByOriginUserId(1L);
        order.verify(transactions).findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any());
        order.verify(transactions).save(any(Transaction.class));
    }

    @Test
    void savesRejectionWhenBalanceIsInsufficient() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("50.00", "800.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());

        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Saldo insuficiente", saved.getRejectionReason());
    }

    @Test
    void savesRejectionWhenMockApiIsUnavailable() {
        when(accounts.findByUserId(1L)).thenThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));

        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.PURCHASE, money("100.00"), "Compra", "Alimentação", null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Serviço externo de consulta de saldo e limites temporariamente indisponível",
                saved.getRejectionReason());
        verify(transactions, never()).findApprovedByOriginUserId(any());
        verifyNoInteractions(accountLock);
    }

    @Test
    void rejectsMissingAccountWithoutApprovingBlindly() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Conta de saldo não encontrada", saved.getRejectionReason());
        verifyNoInteractions(accountLock);
    }

    private Transaction capturedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(captor.capture());
        return captor.getValue();
    }

    private AccountBalance account(String balance, String daily, String perTransaction) {
        return new AccountBalance(1L, money(balance), money(daily), money(perTransaction));
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
}
