package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.port.AccountOperationLockPort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessTransactionServiceTest {
    private static final Instant CREATION_DATE = Instant.parse("2026-11-06T12:00:00Z");

    @Mock private TransactionRepositoryPort transactions;
    @Mock private AccountOperationLockPort accountLock;

    private ProcessTransactionService processor;

    @BeforeEach
    void setUp() {
        processor = new ProcessTransactionService(transactions, accountLock);
    }

    @Test
    @DisplayName("verifica correção única aprovada somente após adquirir o lock")
    void testCorrection_WhenOriginalWasAlreadyCorrected_ShouldRejectAfterLock() {
        // given
        Transaction candidate = correction();
        when(transactions.findById(42L)).thenReturn(java.util.Optional.of(original()));
        when(transactions.existsByOriginalTransactionIdAndStatus(42L, TransactionStatus.APPROVED)).thenReturn(true);

        // when / then
        assertThrows(InvalidTransactionException.class, () -> processor.process(candidate, null, null));

        InOrder order = inOrder(accountLock, transactions);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).findById(42L);
        order.verify(transactions).existsByOriginalTransactionIdAndStatus(42L, TransactionStatus.APPROVED);
        verify(transactions, never()).save(any());
    }

    @Test
    @DisplayName("salva correção após revalidar original e ausência de outra correção aprovada")
    void testCorrection_WhenUnique_ShouldSaveAfterLock() {
        // given
        Transaction candidate = correction();
        when(transactions.findById(42L)).thenReturn(java.util.Optional.of(original()));

        // when
        processor.process(candidate, account("1000.00", "800.00", "500.00"), null);

        // then
        InOrder order = inOrder(accountLock, transactions);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).findById(42L);
        order.verify(transactions).existsByOriginalTransactionIdAndStatus(42L, TransactionStatus.APPROVED);
        order.verify(transactions).save(same(candidate));
    }

    @Test
    @DisplayName("permite nova correção após uma tentativa de correção rejeitada por saldo insuficiente")
    void testCorrection_WhenPreviousAttemptWasRejected_ShouldAllowRetryAndApprove() {
        // given
        Transaction originalWithdraw = new Transaction(42L, TransactionType.WITHDRAW, money("50.00"), null,
                1L, null, TransactionStatus.APPROVED, null, null, "Retirada original", CREATION_DATE);
        Transaction rejectedAttempt = new Transaction(null, TransactionType.WITHDRAW, money("120.00"), null,
                1L, null, TransactionStatus.APPROVED, null, 42L, "Tentativa acima do saldo", CREATION_DATE,
                List.of(), "Valor digitado errado");
        Transaction validCorrection = new Transaction(null, TransactionType.WITHDRAW, money("80.00"), null,
                1L, null, TransactionStatus.APPROVED, null, 42L, "Correção válida", CREATION_DATE,
                List.of(), "Valor correto era 80");
        AccountBalance account = account("100.00", "800.00", "500.00");

        when(transactions.findById(42L)).thenReturn(java.util.Optional.of(originalWithdraw));
        when(transactions.existsByOriginalTransactionIdAndStatus(42L, TransactionStatus.APPROVED)).thenReturn(false);
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of(originalWithdraw));
        when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                .thenReturn(List.of(originalWithdraw));
        when(transactions.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Transaction firstResult = processor.process(rejectedAttempt, account, null);
        Transaction secondResult = processor.process(validCorrection, account, null);

        // then
        assertEquals(TransactionStatus.REJECTED, firstResult.getStatus());
        assertEquals("Saldo insuficiente", firstResult.getRejectionReason());
        assertEquals(42L, firstResult.getOriginalTransactionId());
        assertEquals(TransactionStatus.APPROVED, secondResult.getStatus());
        assertSame(validCorrection, secondResult);
    }

    private Transaction original() {
        return new Transaction(42L, TransactionType.DEPOSIT, money("25.00"), null,
                1L, null, TransactionStatus.APPROVED, null, null, "Original", CREATION_DATE);
    }

    private Transaction correction() {
        return new Transaction(null, TransactionType.DEPOSIT, money("30.00"), null,
                1L, null, TransactionStatus.APPROVED, null, 42L, "Correção", CREATION_DATE);
    }

    @Test
    @DisplayName("aprova depósito com bloqueio sem consultar histórico")
    void testDeposit_WhenCandidateIsValid_ShouldSaveApprovedWithoutHistoryLookup() {
        // given
        Transaction candidate = candidate(TransactionType.DEPOSIT, "25.00");

        // when
        processor.process(candidate, null, null);

        // then
        assertSame(candidate, savedTransaction());
        InOrder order = inOrder(accountLock, transactions);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).save(same(candidate));
        verify(transactions, never()).findApprovedByOriginUserId(any());
    }

    @Test
    @DisplayName("aprova retirada dentro do saldo e dos limites após bloqueio")
    void testWithdrawal_WhenBalanceAndLimitsAllow_ShouldSaveApproved() {
        // given
        Transaction candidate = candidate(TransactionType.WITHDRAW, "100.00");
        AccountBalance account = account("1000.00", "800.00", "500.00");

        // when
        processor.process(candidate, account, null);

        // then
        assertSame(candidate, savedTransaction());
        InOrder order = inOrder(accountLock, transactions);
        order.verify(accountLock).lockForUser(1L);
        order.verify(transactions).findApprovedByOriginUserId(1L);
        order.verify(transactions).findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any());
        order.verify(transactions).findApprovedIncomingTransfersByUserId(1L);
        order.verify(transactions).save(same(candidate));
    }

    @Test
    @DisplayName("rejeita retirada com saldo insuficiente")
    void testWithdrawal_WhenBalanceIsInsufficient_ShouldSaveRejection() {
        // given
        Transaction candidate = candidate(TransactionType.WITHDRAW, "100.00");
        AccountBalance account = account("50.00", "800.00", "500.00");

        // when
        processor.process(candidate, account, null);

        // then
        assertRejection(candidate, "Saldo insuficiente");
        verify(accountLock).lockForUser(1L);
    }

    @Test
    @DisplayName("aprova retirada usando saldo de depósito aprovado anteriormente")
    void testWithdrawal_WhenApprovedDepositIncreasesBalance_ShouldSaveApproved() {
        // given
        Transaction deposit = candidate(TransactionType.DEPOSIT, "100.00");
        Transaction candidate = candidate(TransactionType.WITHDRAW, "120.00");
        AccountBalance account = account("50.00", "500.00", "500.00");
        when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of(deposit));

        // when
        processor.process(candidate, account, null);

        // then
        assertSame(candidate, savedTransaction());
        assertEquals("Retirada", candidate.getCategory());
        verify(accountLock).lockForUser(1L);
        verify(transactions).findApprovedByOriginUserId(1L);
    }

    @Test
    @DisplayName("aprova retirada com transferência recebida sem contá-la como gasto diário")
    void testWithdrawal_WhenReceivedTransferCoversAmount_ShouldSaveApproved() {
        // given
        Transaction candidate = candidate(TransactionType.WITHDRAW, "100.00");
        AccountBalance account = account("0.00", "100.00", "100.00");
        when(transactions.findApprovedIncomingTransfersByUserId(1L))
                .thenReturn(List.of(receivedTransfer("100.00")));

        // when
        processor.process(candidate, account, null);

        // then
        assertSame(candidate, savedTransaction());
        verify(accountLock).lockForUser(1L);
        verify(transactions).findApprovedIncomingTransfersByUserId(1L);
    }

    @Test
    @DisplayName("rejeita retirada maior que saldo com transferência recebida")
    void testWithdrawal_WhenReceivedTransferDoesNotCoverAmount_ShouldSaveRejection() {
        // given
        Transaction candidate = candidate(TransactionType.WITHDRAW, "95.01");
        AccountBalance account = account("20.00", "800.00", "500.00");
        when(transactions.findApprovedIncomingTransfersByUserId(1L))
                .thenReturn(List.of(receivedTransfer("75.00")));

        // when
        processor.process(candidate, account, null);

        // then
        assertRejection(candidate, "Saldo insuficiente");
        verify(accountLock).lockForUser(1L);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionType.class, names = {"TRANSFER", "PURCHASE"})
    @DisplayName("aprova transferência ou compra dentro do saldo e dos limites")
    void testOutflow_WhenBalanceAndLimitsAllow_ShouldSaveApproved(TransactionType type) {
        // given
        Transaction candidate = candidate(type, "100.00");
        AccountBalance account = account("1000.00", "800.00", "500.00");

        // when
        processor.process(candidate, account, null);

        // then
        assertSame(candidate, savedTransaction());
        assertEquals(type, candidate.getType());
        verify(accountLock).lockForUser(1L);
    }

    @ParameterizedTest
    @CsvSource({
            "WITHDRAW, 100.00, 50.00, 500.00, 500.00, 0.00, Saldo insuficiente",
            "TRANSFER, 100.00, 50.00, 500.00, 500.00, 0.00, Saldo insuficiente",
            "PURCHASE, 100.00, 50.00, 500.00, 500.00, 0.00, Saldo insuficiente",
            "WITHDRAW, 500.01, 1000.00, 800.00, 500.00, 0.00, Limite por transação excedido",
            "TRANSFER, 500.01, 1000.00, 800.00, 500.00, 0.00, Limite por transação excedido",
            "PURCHASE, 500.01, 1000.00, 800.00, 500.00, 0.00, Limite por transação excedido",
            "WITHDRAW, 100.01, 1000.00, 500.00, 500.00, 400.00, Limite diário excedido",
            "TRANSFER, 100.00, 1000.00, 500.00, 500.00, 450.00, Limite diário excedido",
            "PURCHASE, 100.00, 1000.00, 500.00, 500.00, 450.00, Limite diário excedido"
    })
    @DisplayName("registra saída rejeitada com o motivo correto")
    void testOutflow_WhenBalanceOrLimitFails_ShouldSaveRejection(
            TransactionType type, String amount, String openingBalance, String dailyLimit,
            String transactionLimit, String priorOutflowAmount, String expectedReason
    ) {
        // given
        Transaction candidate = candidate(type, amount);
        AccountBalance account = account(openingBalance, dailyLimit, transactionLimit);
        if (money(priorOutflowAmount).signum() > 0) {
            Transaction priorOutflow = candidate(TransactionType.PURCHASE, priorOutflowAmount);
            when(transactions.findApprovedByOriginUserId(1L)).thenReturn(List.of(priorOutflow));
            when(transactions.findApprovedOutflowsByOriginUserIdBetween(eq(1L), any(), any()))
                    .thenReturn(List.of(priorOutflow));
        }

        // when
        processor.process(candidate, account, null);

        // then
        assertRejection(candidate, expectedReason);
        verify(accountLock).lockForUser(1L);
    }

    @Test
    @DisplayName("rejeita saída sem conta sem adquirir bloqueio")
    void testWithdrawal_WhenAccountIsMissing_ShouldSaveRejectionWithoutLock() {
        // given
        Transaction candidate = candidate(TransactionType.WITHDRAW, "100.00");

        // when
        processor.process(candidate, null, null);

        // then
        assertRejection(candidate, "Conta de saldo não encontrada");
        verifyNoInteractions(accountLock);
        verify(transactions, never()).findApprovedByOriginUserId(any());
    }

    @Test
    @DisplayName("persiste rejeição externa sem adquirir bloqueio")
    void testTransfer_WhenExternalCheckRejected_ShouldSaveRejectionWithoutLock() {
        // given
        Transaction candidate = candidate(TransactionType.TRANSFER, "25.00");
        String reason = "Usuário de destino não encontrado";

        // when
        processor.process(candidate, null, reason);

        // then
        assertRejection(candidate, reason);
        verifyNoInteractions(accountLock);
        verify(transactions, never()).findApprovedByOriginUserId(any());
    }

    private Transaction candidate(TransactionType type, String amount) {
        return new Transaction(null, type, money(amount),
                type == TransactionType.PURCHASE ? "Alimentação" : null,
                1L, type == TransactionType.TRANSFER ? 2L : null,
                TransactionStatus.APPROVED, null, null, "Transação de teste", CREATION_DATE);
    }

    private Transaction receivedTransfer(String amount) {
        return new Transaction(null, TransactionType.TRANSFER, money(amount), null,
                2L, 1L, TransactionStatus.APPROVED, null, null,
                "Transferência recebida", CREATION_DATE);
    }

    private Transaction savedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(captor.capture());
        return captor.getValue();
    }

    private void assertRejection(Transaction candidate, String reason) {
        Transaction saved = savedTransaction();
        assertEquals(TransactionStatus.REJECTED, saved.getStatus());
        assertEquals(reason, saved.getRejectionReason());
        assertEquals(candidate.getType(), saved.getType());
        assertEquals(candidate.getCategory(), saved.getCategory());
        assertEquals(candidate.getOriginUserId(), saved.getOriginUserId());
        assertEquals(candidate.getDestinationUserId(), saved.getDestinationUserId());
    }

    private AccountBalance account(String balance, String daily, String perTransaction) {
        return new AccountBalance(1L, money(balance), money(daily), money(perTransaction));
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
