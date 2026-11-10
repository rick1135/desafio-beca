package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.ExchangeRatePort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
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
import java.time.Instant;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateTransactionServiceTest {
    @Mock private AccountBalancePort accounts;
    @Mock private UserLookupPort users;
    @Mock private ProcessTransactionService processor;
    @Mock private ExchangeRatePort exchangeRates;
    @Mock private TransactionRepositoryPort transactions;

    private CreateTransactionService service;

    @BeforeEach
    void setUp() {
        service = new CreateTransactionService(accounts, users, processor, exchangeRates, transactions);
    }

    @Test
    @DisplayName("rejeita correção com 404 quando a transação original não existe")
    void testCreate_WhenOriginalTransactionDoesNotExist_ShouldThrowNotFound() {
        // given
        when(transactions.findById(42L)).thenReturn(Optional.empty());

        // when / then
        assertThrows(TransactionNotFoundException.class, () ->
                service.create(1L, TransactionType.DEPOSIT, money("25.00"),
                        "Correção", null, null, 42L, "Valor incorreto"));

        verifyNoInteractions(processor, exchangeRates, accounts, users);
    }

    @Test
    @DisplayName("rejeita correção com 403 quando a transação original pertence a outro usuário")
    void testCreate_WhenOriginalTransactionBelongsToAnotherUser_ShouldThrowForbidden() {
        // given
        when(transactions.findById(42L)).thenReturn(Optional.of(originalForUser(2L)));

        // when / then
        assertThrows(ForbiddenTransactionAccessException.class, () ->
                service.create(1L, TransactionType.DEPOSIT, money("25.00"),
                        "Correção", null, null, 42L, "Valor incorreto"));

        verifyNoInteractions(processor, exchangeRates, accounts, users);
    }

    @Test
    @DisplayName("rejeita correção sem motivo antes de consultar o banco ou serviços externos")
    void testCreate_WhenCorrectionReasonIsMissing_ShouldRejectBeforeExternalAccess() {
        // given
        BigDecimal amount = money("25.00");

        // when / then
        assertThrows(InvalidTransactionException.class, () ->
                service.create(1L, TransactionType.DEPOSIT, amount,
                        "Correção sem motivo", null, null, 42L, null));

        verifyNoInteractions(transactions, processor, exchangeRates, accounts, users);
    }

    @Test
    @DisplayName("processa correção autorizada normalmente preservando o vínculo e o motivo")
    void testCreate_WhenCorrectionIsAuthorized_ShouldDelegateWithReason() {
        // given
        when(transactions.findById(42L)).thenReturn(Optional.of(originalForUser(1L)));
        when(processor.process(any(Transaction.class), isNull(), isNull()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Transaction correction = service.create(1L, TransactionType.DEPOSIT, money("25.00"),
                "Correção", null, null, 42L, "Valor incorreto");

        // then
        assertEquals(42L, correction.getOriginalTransactionId());
        assertEquals("Valor incorreto", correction.getCorrectionReason());
        assertEquals(1L, correction.getOriginUserId());
        verify(processor).process(any(Transaction.class), isNull(), isNull());
    }

    @Test
    @DisplayName("rejeita correção de transação rejeitada antes de consultar serviços externos")
    void testCreate_WhenOriginalIsRejected_ShouldRejectBeforeExternalAccess() {
        // given
        Transaction original = originalForUser(1L).reject("Rejeitada");
        when(transactions.findById(42L)).thenReturn(Optional.of(original));

        // when / then
        assertThrows(InvalidTransactionException.class, () -> service.create(1L, TransactionType.DEPOSIT,
                money("30.00"), "Correção", null, null, 42L, "Valor incorreto"));
        verifyNoInteractions(processor, exchangeRates, accounts, users);
    }

    @Test
    @DisplayName("rejeita correção de outra correção antes de consultar serviços externos")
    void testCreate_WhenOriginalIsCorrection_ShouldRejectBeforeExternalAccess() {
        // given
        Transaction original = new Transaction(42L, TransactionType.DEPOSIT, money("10.00"),
                null, 1L, null, TransactionStatus.APPROVED, null, 10L,
                "Correção anterior", Instant.parse("2026-11-06T12:00:00Z"));
        when(transactions.findById(42L)).thenReturn(Optional.of(original));

        // when / then
        assertThrows(InvalidTransactionException.class, () -> service.create(1L, TransactionType.DEPOSIT,
                money("30.00"), "Correção", null, null, 42L, "Valor incorreto"));
        verifyNoInteractions(processor, exchangeRates, accounts, users);
    }

    @Test
    @DisplayName("rejeita correção de tipo diferente antes de consultar serviços externos")
    void testCreate_WhenTypeDiffersFromOriginal_ShouldRejectBeforeExternalAccess() {
        // given
        when(transactions.findById(42L)).thenReturn(Optional.of(originalForUser(1L)));

        // when / then
        assertThrows(InvalidTransactionException.class, () -> service.create(1L, TransactionType.WITHDRAW,
                money("30.00"), "Correção", null, null, 42L, "Valor incorreto"));
        verifyNoInteractions(processor, exchangeRates, accounts, users);
    }

    private Transaction originalForUser(Long userId) {
        return new Transaction(42L, TransactionType.DEPOSIT, money("10.00"),
                "Depósito", userId, null, TransactionStatus.APPROVED, null, null,
                "Original", Instant.parse("2026-11-06T12:00:00Z"));
    }

    @Test
    @DisplayName("anexa cotações de USD e EUR ao candidato quando BrasilAPI responde")
    void testCreate_WhenBothExchangeRatesExist_ShouldAttachRatesToCandidate() {
        // given
        ExchangeRate usd = new ExchangeRate(ExchangeCurrency.USD, money("5.5013"),
                LocalDate.parse("2026-11-06"), LocalDateTime.parse("2026-11-06T13:06:13"),
                "FECHAMENTO PTAX");
        ExchangeRate eur = new ExchangeRate(ExchangeCurrency.EUR, money("6.1317"),
                LocalDate.parse("2026-11-06"), LocalDateTime.parse("2026-11-06T13:06:13"),
                "FECHAMENTO PTAX");
        when(exchangeRates.findByCurrencyAndDate(eq(ExchangeCurrency.USD), any()))
                .thenReturn(Optional.of(usd));
        when(exchangeRates.findByCurrencyAndDate(eq(ExchangeCurrency.EUR), any()))
                .thenReturn(Optional.of(eur));

        // when
        service.create(1L, TransactionType.DEPOSIT, money("100.00"), "Depósito", null, null, null);

        // then
        ArgumentCaptor<Transaction> candidate = ArgumentCaptor.forClass(Transaction.class);
        verify(processor).process(candidate.capture(), isNull(), isNull());
        assertEquals(java.util.List.of(usd, eur), candidate.getValue().getExchangeRates());
    }

    @Test
    @DisplayName("consulta cotações de USD e EUR em paralelo")
    void testCreate_WhenFetchingExchangeRates_ShouldQueryBothCurrenciesConcurrently() {
        // given
        CountDownLatch bothStarted = new CountDownLatch(2);
        when(exchangeRates.findByCurrencyAndDate(any(), any())).thenAnswer(invocation -> {
            ExchangeCurrency currency = invocation.getArgument(0);
            bothStarted.countDown();
            if (!bothStarted.await(2, TimeUnit.SECONDS)) {
                return Optional.empty();
            }
            return Optional.of(new ExchangeRate(currency, money("5.00"),
                    LocalDate.parse("2026-11-06"), LocalDateTime.parse("2026-11-06T13:00:00"),
                    "FECHAMENTO PTAX"));
        });

        // when
        service.create(1L, TransactionType.DEPOSIT, money("100.00"), "Depósito", null, null, null);

        // then
        ArgumentCaptor<Transaction> candidate = ArgumentCaptor.forClass(Transaction.class);
        verify(processor).process(candidate.capture(), isNull(), isNull());
        assertEquals(2, candidate.getValue().getExchangeRates().size());
    }

    @Test
    @DisplayName("mantém transação sem câmbio parcial quando uma moeda falha")
    void testCreate_WhenOneExchangeRateFails_ShouldDelegateWithoutPartialRates() {
        // given
        ExchangeRate usd = new ExchangeRate(ExchangeCurrency.USD, money("5.5013"),
                LocalDate.parse("2026-11-06"), LocalDateTime.parse("2026-11-06T13:06:13"),
                "FECHAMENTO PTAX");
        when(exchangeRates.findByCurrencyAndDate(eq(ExchangeCurrency.USD), any()))
                .thenReturn(Optional.of(usd));
        when(exchangeRates.findByCurrencyAndDate(eq(ExchangeCurrency.EUR), any()))
                .thenThrow(new IllegalStateException("BrasilAPI indisponível"));

        // when
        service.create(1L, TransactionType.DEPOSIT, money("100.00"), "Depósito", null, null, null);

        // then
        ArgumentCaptor<Transaction> candidate = ArgumentCaptor.forClass(Transaction.class);
        verify(processor).process(candidate.capture(), isNull(), isNull());
        assertTrue(candidate.getValue().getExchangeRates().isEmpty());
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

    @Test
    @DisplayName("consulta conta do destinatário e do remetente quando correção de transferência reduz o valor")
    void testTransferCorrection_WhenReducingAmount_ShouldLookupRecipientAccount() {
        // given
        Transaction original = new Transaction(42L, TransactionType.TRANSFER, money("100.00"),
                null, 1L, 2L, TransactionStatus.APPROVED, null, null,
                "Transferência original", Instant.parse("2026-11-06T12:00:00Z"));
        AccountBalance senderAccount = account("1000.00", "800.00", "500.00");
        AccountBalance recipientAccount = new AccountBalance(2L, money("50.00"), money("800.00"), money("500.00"));

        when(transactions.findById(42L)).thenReturn(Optional.of(original));
        when(users.existsById(2L)).thenReturn(true);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(senderAccount));
        when(accounts.findByUserId(2L)).thenReturn(Optional.of(recipientAccount));

        // when
        service.create(1L, TransactionType.TRANSFER, money("10.00"),
                "Correção para 10", null, 2L, 42L, "Errei o valor");

        // then
        verify(accounts).findByUserId(1L);
        verify(accounts).findByUserId(2L);
        ArgumentCaptor<Transaction> candidateCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(processor).process(candidateCaptor.capture(), same(senderAccount), same(recipientAccount), isNull());
        assertEquals(42L, candidateCaptor.getValue().getOriginalTransactionId());
    }

    @Test
    @DisplayName("delega rejeição quando MockAPI do destinatário falha na correção de transferência")
    void testTransferCorrection_WhenRecipientMockApiFails_ShouldDelegateRejection() {
        // given
        Transaction original = new Transaction(42L, TransactionType.TRANSFER, money("100.00"),
                null, 1L, 2L, TransactionStatus.APPROVED, null, null,
                "Transferência original", Instant.parse("2026-11-06T12:00:00Z"));
        AccountBalance senderAccount = account("1000.00", "800.00", "500.00");

        when(transactions.findById(42L)).thenReturn(Optional.of(original));
        when(users.existsById(2L)).thenReturn(true);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(senderAccount));
        when(accounts.findByUserId(2L)).thenThrow(new ExternalServiceUnavailableException("MockAPI", new RuntimeException("timeout")));

        // when
        service.create(1L, TransactionType.TRANSFER, money("10.00"),
                "Correção para 10", null, 2L, 42L, "Errei o valor");

        // then
        verify(processor).process(any(Transaction.class), same(senderAccount),
                eq("Serviço externo de consulta de saldo e limites temporariamente indisponível"));
    }

    @Test
    @DisplayName("não consulta MockAPI do destinatário quando correção aumenta o valor da transferência")
    void testTransferCorrection_WhenIncreasingAmount_ShouldNotLookupRecipientAccount() {
        // given
        Transaction original = new Transaction(42L, TransactionType.TRANSFER, money("100.00"),
                null, 1L, 2L, TransactionStatus.APPROVED, null, null,
                "Transferência original", Instant.parse("2026-11-06T12:00:00Z"));
        AccountBalance senderAccount = account("1000.00", "800.00", "500.00");

        when(transactions.findById(42L)).thenReturn(Optional.of(original));
        when(users.existsById(2L)).thenReturn(true);
        when(accounts.findByUserId(1L)).thenReturn(Optional.of(senderAccount));

        // when
        service.create(1L, TransactionType.TRANSFER, money("150.00"),
                "Correção para 150", null, 2L, 42L, "Aumentar valor");

        // then
        verify(accounts).findByUserId(1L);
        verify(accounts, never()).findByUserId(2L);
        verify(processor).process(any(Transaction.class), same(senderAccount), isNull());
    }

    private AccountBalance account(String balance, String daily, String perTransaction) {
        return new AccountBalance(1L, money(balance), money(daily), money(perTransaction));
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
