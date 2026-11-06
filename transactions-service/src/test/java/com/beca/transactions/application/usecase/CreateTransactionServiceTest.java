package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.AccountOperationLockPort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import com.beca.transactions.domain.port.UserLookupPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
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
    @Mock private UserLookupPort users;

    @Test
    void approvesDepositWithoutConsultingMockApi() {
        CreateTransactionService service = new CreateTransactionService(transactions, accounts, accountLock, users);
        service.create(1L, TransactionType.DEPOSIT, money("25.00"), "Depósito", null, null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.APPROVED, saved.getStatus());
        assertNull(saved.getRejectionReason());
        verifyNoInteractions(accounts, users);
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

        new CreateTransactionService(transactions, accounts, accountLock, users)
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

        new CreateTransactionService(transactions, accounts, accountLock, users)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Saldo insuficiente", saved.getRejectionReason());
    }

    @Test
    void approvesWithdrawalUsingReceivedTransferWithoutCountingItAsDailySpending() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("0.00", "100.00", "100.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(transactions.findApprovedIncomingTransfersByUserId(1L))
                .thenReturn(List.of(receivedTransfer("100.00")));

        new CreateTransactionService(transactions, accounts, accountLock, users)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.APPROVED, saved.getStatus());
        assertNull(saved.getRejectionReason());
        InOrder order = inOrder(accountLock, transactions);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).findApprovedIncomingTransfersByUserId(1L);
        order.verify(transactions).save(any(Transaction.class));
    }

    @Test
    void rejectsWithdrawalAboveBalanceIncludingReceivedTransfers() {
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account("20.00", "800.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(transactions.findApprovedIncomingTransfersByUserId(1L))
                .thenReturn(List.of(receivedTransfer("75.00")));

        new CreateTransactionService(transactions, accounts, accountLock, users)
                .create(1L, TransactionType.WITHDRAW, money("95.01"), "Retirada", null, null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Saldo insuficiente", saved.getRejectionReason());
    }

    @Test
    void savesRejectionWhenMockApiIsUnavailable() {
        when(accounts.findByUserId(1L)).thenThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));

        new CreateTransactionService(transactions, accounts, accountLock, users)
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

        new CreateTransactionService(transactions, accounts, accountLock, users)
                .create(1L, TransactionType.WITHDRAW, money("100.00"), "Retirada", null, null, null);

        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Conta de saldo não encontrada", saved.getRejectionReason());
        verifyNoInteractions(accountLock);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = {"TRANSFER", "PURCHASE"})
    @DisplayName("aprova transferência ou compra dentro do saldo e dos limites")
    void testOutflow_WhenBalanceAndLimitsAllow_ShouldApprove(TransactionType type) {
        //given
        String category = type == TransactionType.PURCHASE ? "Alimentação" : null;
        Long destinationUserId = type == TransactionType.TRANSFER ? 2L : null;
        if (type == TransactionType.TRANSFER) {
            when(users.existsById(2L)).thenReturn(true);
        }
        when(accounts.findByUserId(1L))
                .thenReturn(Optional.of(account("1000.00", "800.00", "500.00")));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of());
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of());

        //when
        new CreateTransactionService(transactions, accounts, accountLock, users)
                .create(1L, type, money("100.00"), "Teste", category, destinationUserId, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.APPROVED, saved.getStatus());
        assertNull(saved.getRejectionReason());
        assertEquals(type, saved.getType());
        assertEquals(category, saved.getCategory());
        assertEquals(1L, saved.getOriginUserId());
        assertEquals(destinationUserId, saved.getDestinationUserId());
        verify(accountLock).lockForUser(1L);
        if (type == TransactionType.TRANSFER) {
            verify(users).existsById(2L);
        } else {
            verifyNoInteractions(users);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "TRANSFER, 100.00, 50.00, 500.00, 500.00, 0.00, Saldo insuficiente",
            "PURCHASE, 100.00, 50.00, 500.00, 500.00, 0.00, Saldo insuficiente",
            "TRANSFER, 500.01, 1000.00, 800.00, 500.00, 0.00, Limite por transação excedido",
            "PURCHASE, 500.01, 1000.00, 800.00, 500.00, 0.00, Limite por transação excedido",
            "TRANSFER, 100.00, 1000.00, 500.00, 500.00, 450.00, Limite diário excedido",
            "PURCHASE, 100.00, 1000.00, 500.00, 500.00, 450.00, Limite diário excedido"
    })
    @DisplayName("registra transferência ou compra rejeitada com o motivo correto")
    void testOutflow_WhenBalanceOrLimitFails_ShouldSaveRejection(
            TransactionType type, String amount, String openingBalance, String dailyLimit,
            String transactionLimit, String priorOutflowAmount, String expectedReason
    ) {
        //given
        String category = type == TransactionType.PURCHASE ? "Alimentação" : null;
        Long destinationUserId = type == TransactionType.TRANSFER ? 2L : null;
        if (type == TransactionType.TRANSFER) {
            when(users.existsById(2L)).thenReturn(true);
        }
        List<Transaction> previous = money(priorOutflowAmount).signum() == 0
                ? List.of()
                : List.of(new Transaction(
                        null, TransactionType.PURCHASE, money(priorOutflowAmount),
                        "Alimentação", 1L, null, TransactionStatus.APPROVED,
                        null, null, "Compra anterior", Instant.now()
                ));
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(
                account(openingBalance, dailyLimit, transactionLimit)
        ));
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(previous);
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(previous);

        //when
        new CreateTransactionService(transactions, accounts, accountLock, users)
                .create(1L, type, money(amount), "Teste", category, destinationUserId, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals(expectedReason, saved.getRejectionReason());
        assertEquals(type, saved.getType());
        assertEquals(destinationUserId, saved.getDestinationUserId());
        assertEquals(category, saved.getCategory());
        verify(accountLock).lockForUser(1L);
    }

    @Test
    @DisplayName("rejeita transferência para si mesmo antes de acessar a MockAPI ou o banco")
    void testTransfer_WhenDestinationIsOrigin_ShouldRejectBeforeExternalAccess() {
        //given
        CreateTransactionService service = new CreateTransactionService(transactions, accounts, accountLock, users);

        //when then
        assertThrows(InvalidTransactionException.class, () -> service.create(
                1L, TransactionType.TRANSFER, money("25.00"),
                "Transferência para si mesmo", null, 1L, null
        ));
        verifyNoInteractions(accounts, accountLock, transactions, users);
    }

    @Test
    @DisplayName("registra rejeição de transferência quando o destinatário não existe no MS1")
    void testTransfer_WhenRecipientIsMissing_ShouldSaveRejection() {
        //given
        when(users.existsById(2L)).thenReturn(false);

        //when
        new CreateTransactionService(transactions, accounts, accountLock, users)
                .create(1L, TransactionType.TRANSFER, money("25.00"), "Transferência", null, 2L, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Usuário de destino não encontrado", saved.getRejectionReason());
        assertEquals(2L, saved.getDestinationUserId());
        verify(users).existsById(2L);
        verifyNoInteractions(accounts, accountLock);
    }

    @Test
    @DisplayName("registra rejeição de transferência quando o MS1 está indisponível")
    void testTransfer_WhenUserServiceIsUnavailable_ShouldSaveRejection() {
        //given
        when(users.existsById(2L)).thenThrow(new ExternalServiceUnavailableException(
                "MS1 de usuários", new RuntimeException("timeout")));

        //when
        new CreateTransactionService(transactions, accounts, accountLock, users)
                .create(1L, TransactionType.TRANSFER, money("25.00"), "Transferência", null, 2L, null);

        //then
        Transaction saved = capturedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals("Serviço de consulta de usuários temporariamente indisponível", saved.getRejectionReason());
        verifyNoInteractions(accounts, accountLock);
    }

    private Transaction receivedTransfer(String amount) {
        return new Transaction(null, TransactionType.TRANSFER, money(amount),
                null, 2L, 1L, TransactionStatus.APPROVED, null, null,
                "Transferência recebida", Instant.now());
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
