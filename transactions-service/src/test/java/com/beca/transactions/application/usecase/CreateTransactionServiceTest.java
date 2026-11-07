package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.UserLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateTransactionServiceTest {
    @Mock private AccountBalancePort accounts;
    @Mock private UserLookupPort users;
    @Mock private ProcessTransactionService processor;

    private CreateTransactionService service;

    @BeforeEach
    void setUp() {
        service = new CreateTransactionService(accounts, users, processor);
    }

    @Test
    @DisplayName("delega depósito sem consultar MS1 ou MockAPI e aplica categoria padrão")
    void testDeposit_WhenDataIsValid_ShouldDelegateWithoutExternalLookup() {
        // given
        BigDecimal amount = money("25.00");

        // when
        service.create(1L, TransactionType.DEPOSIT, amount, "Depósito", null, null, null);

        // then
        ArgumentCaptor<Transaction> candidate = ArgumentCaptor.forClass(Transaction.class);
        verify(processor).process(candidate.capture(), isNull(), isNull());
        assertEquals(TransactionType.DEPOSIT, candidate.getValue().getType());
        assertEquals(amount, candidate.getValue().getAmount());
        assertEquals("Depósito", candidate.getValue().getCategory());
        assertEquals(TransactionStatus.APPROVED, candidate.getValue().getStatus());
        verifyNoInteractions(accounts, users);
    }

    @Test
    @DisplayName("delega retirada com a conta consultada na MockAPI e categoria padrão")
    void testWithdrawal_WhenAccountExists_ShouldDelegateCandidateAndAccount() {
        // given
        AccountBalance account = account("100.00", "100.00", "100.00");
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account));

        // when
        service.create(1L, TransactionType.WITHDRAW, money("25.00"),
                "Retirada", null, null, null);

        // then
        ArgumentCaptor<Transaction> candidate = ArgumentCaptor.forClass(Transaction.class);
        verify(processor).process(candidate.capture(), same(account), isNull());
        assertEquals(TransactionType.WITHDRAW, candidate.getValue().getType());
        assertEquals("Retirada", candidate.getValue().getCategory());
        assertEquals(1L, candidate.getValue().getOriginUserId());
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("delega ausência de conta para rejeição no processamento")
    void testWithdrawal_WhenAccountIsMissing_ShouldDelegateNullAccount() {
        // given
        when(accounts.findByUserId(1L)).thenReturn(Optional.empty());

        // when
        service.create(1L, TransactionType.WITHDRAW, money("25.00"),
                "Retirada", null, null, null);

        // then
        verify(processor).process(any(Transaction.class), isNull(), isNull());
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("delega rejeição de compra quando a MockAPI está indisponível")
    void testPurchase_WhenMockApiIsUnavailable_ShouldDelegateRejection() {
        // given
        when(accounts.findByUserId(1L)).thenThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));

        // when
        service.create(1L, TransactionType.PURCHASE, money("25.00"),
                "Compra", "Alimentação", null, null);

        // then
        verify(processor).process(any(Transaction.class), isNull(), eq(
                "Serviço externo de consulta de saldo e limites temporariamente indisponível"));
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("delega rejeição de retirada quando a MockAPI está indisponível")
    void testWithdrawal_WhenMockApiIsUnavailable_ShouldDelegateRejection() {
        // given
        when(accounts.findByUserId(1L)).thenThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));

        // when
        service.create(1L, TransactionType.WITHDRAW, money("25.00"),
                "Retirada", null, null, null);

        // then
        ArgumentCaptor<Transaction> candidate = ArgumentCaptor.forClass(Transaction.class);
        verify(processor).process(candidate.capture(), isNull(), eq(
                "Serviço externo de consulta de saldo e limites temporariamente indisponível"));
        assertEquals("Retirada", candidate.getValue().getCategory());
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("consulta MS1 e MockAPI antes de delegar a transferência")
    void testTransfer_WhenRecipientAndAccountExist_ShouldDelegateAfterExternalLookups() {
        // given
        AccountBalance account = account("100.00", "100.00", "100.00");
        when(users.existsById(2L)).thenReturn(true);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(account));

        // when
        service.create(1L, TransactionType.TRANSFER, money("25.00"),
                "Transferência", null, 2L, null);

        // then
        InOrder order = inOrder(users, accounts, processor);
        order.verify(users).existsById(2L);
        order.verify(accounts).findByUserId(1L);
        ArgumentCaptor<Transaction> candidate = ArgumentCaptor.forClass(Transaction.class);
        order.verify(processor).process(candidate.capture(), same(account), isNull());
        assertEquals(1L, candidate.getValue().getOriginUserId());
        assertEquals(2L, candidate.getValue().getDestinationUserId());
        assertEquals(TransactionStatus.APPROVED, candidate.getValue().getStatus());
    }

    @Test
    @DisplayName("delega rejeição sem MockAPI quando destinatário não existe")
    void testTransfer_WhenRecipientIsMissing_ShouldDelegateRejection() {
        // given
        when(users.existsById(2L)).thenReturn(false);

        // when
        service.create(1L, TransactionType.TRANSFER, money("25.00"),
                "Transferência", null, 2L, null);

        // then
        verify(processor).process(any(Transaction.class), isNull(),
                eq("Usuário de destino não encontrado"));
        verifyNoInteractions(accounts);
    }

    @Test
    @DisplayName("delega rejeição sem MockAPI quando MS1 está indisponível")
    void testTransfer_WhenUserServiceIsUnavailable_ShouldDelegateRejection() {
        // given
        when(users.existsById(2L)).thenThrow(new ExternalServiceUnavailableException(
                "MS1 de usuários", new RuntimeException("timeout")));

        // when
        service.create(1L, TransactionType.TRANSFER, money("25.00"),
                "Transferência", null, 2L, null);

        // then
        verify(processor).process(any(Transaction.class), isNull(), eq(
                "Serviço de consulta de usuários temporariamente indisponível"));
        verifyNoInteractions(accounts);
    }

    @Test
    @DisplayName("rejeita transferência para si mesmo antes de acessar dependências")
    void testTransfer_WhenDestinationIsOrigin_ShouldRejectBeforeExternalAccess() {
        // given
        BigDecimal amount = money("25.00");

        // when then
        assertThrows(InvalidTransactionException.class, () -> service.create(
                1L, TransactionType.TRANSFER, amount,
                "Transferência para si mesmo", null, 1L, null));
        verifyNoInteractions(accounts, users, processor);
    }

    private AccountBalance account(String balance, String daily, String perTransaction) {
        return new AccountBalance(1L, money(balance), money(daily), money(perTransaction));
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
