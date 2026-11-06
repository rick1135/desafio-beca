package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.AccountOperationLockPort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
class CreateTransactionServiceTest {
    @Mock private TransactionRepositoryPort transactions;
    @Mock private AccountBalancePort accounts;
    @Mock private AccountOperationLockPort accountLock;

    @Test
    @DisplayName("aprova depósito sem consultar a MockAPI e aplica categoria padrão")
    void testDeposit_WhenCategoryIsMissing_ShouldApproveWithoutConsultingMockApi() {
        //given
        CreateTransactionService service = new CreateTransactionService(transactions, accounts, accountLock);

        //when
        service.create(1L, TransactionType.DEPOSIT, money("25.00"), "Depósito", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.APPROVED, saved.getStatus());
        assertNull(saved.getRejectionReason());
        assertEquals("Depósito", saved.getCategory());
        verifyNoInteractions(accounts);
        verify(transactions, never()).findApprovedByOriginUserId(any());

        InOrder order = inOrder(accountLock, transactions);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).save(any(Transaction.class));
    }

    @Test
    @DisplayName("aprova retirada dentro do saldo e dos limites")
    void testWithdrawal_WhenBalanceAndLimitsAllow_ShouldApprove() {
        //given
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("1000.00", "800.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        //then
        assertEquals(TransactionStatus.APPROVED, capturedTransaction().getStatus());

        InOrder order = inOrder(accounts, accountLock, transactions);
        order.verify(accounts).findByUserId(1L);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).findApprovedByOriginUserId(1L);
        order.verify(transactions).findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any());
        order.verify(transactions).save(any(Transaction.class));
    }

    @Test
    @DisplayName("rejeita retirada sem saldo suficiente e registra o motivo")
    void testWithdrawal_WhenBalanceIsInsufficient_ShouldSaveRejection() {
        //given
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("50.00", "800.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Saldo insuficiente", saved.getRejectionReason());
    }

    @Test
    @DisplayName("aprova retirada usando saldo de depósito aprovado anteriormente")
    void testWithdrawal_WhenApprovedDepositIncreasesBalance_ShouldApprove() {
        //given
        Transaction deposit = new Transaction(null, TransactionType.DEPOSIT, money("100.00"),
                null, 1L, null, TransactionStatus.APPROVED, null, null,
                "Depósito anterior", Instant.now());
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("50.00", "500.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of(deposit));
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("120.00"), "Retirada", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.APPROVED, saved.getStatus());
        assertNull(saved.getRejectionReason());
        assertEquals("Retirada", saved.getCategory());
        verify(transactions).findApprovedByOriginUserId(1L);
    }

    @Test
    @DisplayName("rejeita retirada acima do limite por transação e registra o motivo")
    void testWithdrawal_WhenTransactionLimitIsExceeded_ShouldSaveRejection() {
        //given
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("1000.00", "800.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("500.01"), "Retirada", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Limite por transação excedido", saved.getRejectionReason());
        assertEquals("Retirada", saved.getCategory());
        verify(accountLock).lockForUser(1L);
    }

    @Test
    @DisplayName("rejeita retirada que ultrapassa o limite diário acumulado")
    void testWithdrawal_WhenDailyLimitWouldBeExceeded_ShouldSaveRejection() {
        //given
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("1000.00", "500.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of(approvedWithdrawalToday("400.00")));
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of(approvedWithdrawalToday("400.00")));

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.01"), "Retirada", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Limite diário excedido", saved.getRejectionReason());
        assertEquals("Retirada", saved.getCategory());
        verify(accountLock).lockForUser(1L);
    }

    @Test
    @DisplayName("aprova retirada com transferência recebida sem contá-la no gasto diário")
    void testWithdrawal_WhenReceivedTransferCoversAmount_ShouldApproveWithoutCountingItAsDailySpending() {
        //given
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("0.00", "100.00", "100.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(transactions.findApprovedIncomingTransfersByUserId(1L))
                .thenReturn(List.of(receivedTransfer("100.00")));

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.APPROVED, saved.getStatus());
        assertNull(saved.getRejectionReason());
        InOrder order = inOrder(accountLock, transactions);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).findApprovedIncomingTransfersByUserId(1L);
        order.verify(transactions).save(any(Transaction.class));
    }

    @Test
    @DisplayName("rejeita retirada acima do saldo mesmo com transferência recebida")
    void testWithdrawal_WhenAmountExceedsBalanceIncludingReceivedTransfers_ShouldSaveRejection() {
        //given
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("20.00", "800.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(transactions.findApprovedIncomingTransfersByUserId(1L))
                .thenReturn(List.of(receivedTransfer("75.00")));

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("95.01"), "Retirada", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Saldo insuficiente", saved.getRejectionReason());
    }

    @Test
    @DisplayName("rejeita compra quando a MockAPI está indisponível")
    void testPurchase_WhenMockApiIsUnavailable_ShouldSaveRejection() {
        //given
        when(accounts.findByUserId(1L)).thenThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.PURCHASE, money("100.00"), "Compra", "Alimentação", null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Serviço externo de consulta de saldo e limites temporariamente indisponível",
                saved.getRejectionReason());
        verify(transactions, never()).findApprovedByOriginUserId(any());
        verifyNoInteractions(accountLock);
    }

    @Test
    @DisplayName("rejeita e registra retirada quando a MockAPI está indisponível")
    void testWithdrawal_WhenMockApiIsUnavailable_ShouldSaveRejection() {
        //given
        when(accounts.findByUserId(1L)).thenThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Serviço externo de consulta de saldo e limites temporariamente indisponível",
                saved.getRejectionReason());
        assertEquals("Retirada", saved.getCategory());
        verify(transactions, never()).findApprovedByOriginUserId(any());
        verifyNoInteractions(accountLock);
    }

    @Test
    @DisplayName("rejeita retirada sem conta de saldo cadastrada")
    void testWithdrawal_WhenAccountIsMissing_ShouldSaveRejection() {
        //given
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        //when
        new CreateTransactionService(transactions, accounts, accountLock)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Conta de saldo não encontrada", saved.getRejectionReason());
        verifyNoInteractions(accountLock);
    }

    private Transaction receivedTransfer(String amount) {
        return new Transaction(null, TransactionType.TRANSFER, money(amount),
                null, 2L, 1L, TransactionStatus.APPROVED, null, null,
                "Transferência recebida", Instant.now());
    }

    private Transaction approvedWithdrawalToday(String amount) {
        Instant today = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
                .atTime(12, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();
        return new Transaction(null, TransactionType.WITHDRAW, money(amount),
                null, 1L, null, TransactionStatus.APPROVED, null, null,
                "Retirada anterior", today);
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
