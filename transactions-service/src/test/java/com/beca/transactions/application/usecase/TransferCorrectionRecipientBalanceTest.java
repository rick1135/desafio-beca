package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.model.*;
import com.beca.transactions.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferCorrectionRecipientBalanceTest {
    private final List<Transaction> ledger = new ArrayList<>();
    private final TransactionRepositoryPort repo = mock(TransactionRepositoryPort.class);
    private final AccountOperationLockPort locks = mock(AccountOperationLockPort.class);
    private final AccountBalancePort accounts = mock(AccountBalancePort.class);
    private final UserLookupPort users = mock(UserLookupPort.class);
    private final ExchangeRatePort rates = mock(ExchangeRatePort.class);

    private CreateTransactionService createService;
    private GetBalanceService balanceService;

    @BeforeEach
    void setUp() {
        ledger.clear();

        when(accounts.findByUserId(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.of(new AccountBalance(
                    id,
                    new BigDecimal(id == 1L ? "1000.00" : "0.00"),
                    new BigDecimal("1000.00"),
                    new BigDecimal("1000.00")
            ));
        });

        when(users.existsById(anyLong())).thenReturn(true);

        when(repo.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return ledger.stream().filter(t -> t.getId().equals(id)).findFirst();
        });

        when(repo.findApprovedByOriginUserId(anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return ledger.stream()
                    .filter(t -> t.getOriginUserId().equals(userId) && t.getStatus() == TransactionStatus.APPROVED)
                    .toList();
        });

        when(repo.findApprovedOutflowsByOriginUserIdBetween(anyLong(), any(), any())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return ledger.stream()
                    .filter(t -> t.getOriginUserId().equals(userId)
                            && t.getStatus() == TransactionStatus.APPROVED
                            && t.getType() != TransactionType.DEPOSIT)
                    .toList();
        });

        when(repo.findApprovedIncomingTransfersByUserId(anyLong())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return ledger.stream()
                    .filter(t -> Objects.equals(t.getDestinationUserId(), userId)
                            && t.getType() == TransactionType.TRANSFER
                            && t.getStatus() == TransactionStatus.APPROVED)
                    .toList();
        });

        when(repo.existsByOriginalTransactionIdAndStatus(anyLong(), eq(TransactionStatus.APPROVED))).thenAnswer(invocation -> {
            Long origId = invocation.getArgument(0);
            return ledger.stream()
                    .anyMatch(t -> Objects.equals(t.getOriginalTransactionId(), origId)
                            && t.getStatus() == TransactionStatus.APPROVED);
        });

        when(repo.save(any())).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            Transaction saved = new Transaction(
                    (long) ledger.size() + 1,
                    t.getType(), t.getAmount(), t.getCategory(),
                    t.getOriginUserId(), t.getDestinationUserId(),
                    t.getStatus(), t.getRejectionReason(),
                    t.getOriginalTransactionId(), t.getDescription(),
                    t.getCreationDate(), t.getExchangeRates(),
                    t.getCorrectionReason()
            );
            ledger.add(saved);
            return saved;
        });

        ProcessTransactionService processService = new ProcessTransactionService(repo, locks, accounts);
        createService = new CreateTransactionService(accounts, users, processService, rates, repo);
        balanceService = new GetBalanceService(accounts, repo);
    }

    @Test
    @DisplayName("F01: rejeita correção de transferência que deixaria o saldo do destinatário negativo após ele gastar o crédito")
    void testCorrectionRejectedWhenRecipientSpentCredit() {
        // 1. A transfere 100 para B: APROVADA
        Transaction transfer = createService.create(1L, TransactionType.TRANSFER, new BigDecimal("100.00"),
                "Transferência para B", null, 2L, null);
        assertEquals(TransactionStatus.APPROVED, transfer.getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(balanceService.getBalance(2L).effectiveBalance()));

        // 2. B gasta os 100 em uma compra: APROVADA
        Transaction purchase = createService.create(2L, TransactionType.PURCHASE, new BigDecimal("100.00"),
                "Almoço", "Alimentação", null, null);
        assertEquals(TransactionStatus.APPROVED, purchase.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(balanceService.getBalance(2L).effectiveBalance()));

        // 3. A tenta corrigir a transferência para 10 (reduzindo 90 do destinatário que já gastou)
        Transaction correction = createService.create(1L, TransactionType.TRANSFER, new BigDecimal("10.00"),
                "Correção de valor", null, 2L, transfer.getId(), "Valor digitado errado");

        // DEVE SER REJEITADA!
        assertEquals(TransactionStatus.REJECTED, correction.getStatus());
        assertEquals("Saldo do destinatário insuficiente para estorno da transferência", correction.getRejectionReason());

        // Saldo do destinatário B NUNCA fica negativo: permanece em 0.00
        assertEquals(0, BigDecimal.ZERO.compareTo(balanceService.getBalance(2L).effectiveBalance()));

        // 4. Bloqueios de concorrência foram invocados em ambas as contas (1L e 2L)
        verify(locks, atLeastOnce()).lockForUser(1L);
        verify(locks, atLeastOnce()).lockForUser(2L);
    }

    @Test
    @DisplayName("F01: aprova correção de transferência quando o destinatário ainda possui saldo para cobrir a redução")
    void testCorrectionApprovedWhenRecipientHasSufficientBalance() {
        // 1. A transfere 100 para B: APROVADA
        Transaction transfer = createService.create(1L, TransactionType.TRANSFER, new BigDecimal("100.00"),
                "Transferência para B", null, 2L, null);
        assertEquals(TransactionStatus.APPROVED, transfer.getStatus());

        // 2. B gasta apenas 20 (saldo restante de B = 80)
        Transaction purchase = createService.create(2L, TransactionType.PURCHASE, new BigDecimal("20.00"),
                "Café", "Alimentação", null, null);
        assertEquals(TransactionStatus.APPROVED, purchase.getStatus());
        assertEquals(0, new BigDecimal("80.00").compareTo(balanceService.getBalance(2L).effectiveBalance()));

        // 3. A corrige a transferência de 100 para 60 (redução de 40 no saldo de B)
        Transaction correction = createService.create(1L, TransactionType.TRANSFER, new BigDecimal("60.00"),
                "Correção para 60", null, 2L, transfer.getId(), "Valor era 60");

        // DEVE SER APROVADA!
        assertEquals(TransactionStatus.APPROVED, correction.getStatus());
        assertNull(correction.getRejectionReason());

        // Saldo de B vira 60 - 20 = 40.00
        assertEquals(0, new BigDecimal("40.00").compareTo(balanceService.getBalance(2L).effectiveBalance()));
    }

    @Test
    @DisplayName("F01: rejeita correção quando alteração de destinatário deixa destinatário anterior negativo")
    void testCorrectionRejectedWhenChangingRecipientAndOldRecipientCannotCoverReversal() {
        // 1. A transfere 100 para B: APROVADA
        Transaction transfer = createService.create(1L, TransactionType.TRANSFER, new BigDecimal("100.00"),
                "Transferência para B", null, 2L, null);
        assertEquals(TransactionStatus.APPROVED, transfer.getStatus());

        // 2. B gasta os 100
        Transaction purchase = createService.create(2L, TransactionType.PURCHASE, new BigDecimal("100.00"),
                "Almoço", "Alimentação", null, null);
        assertEquals(TransactionStatus.APPROVED, purchase.getStatus());

        // 3. A tenta corrigir a transferência mudando o destinatário para C (3L)
        Transaction correction = createService.create(1L, TransactionType.TRANSFER, new BigDecimal("100.00"),
                "Mudar para C", null, 3L, transfer.getId(), "Mandei para a pessoa errada");

        // DEVE SER REJEITADA pois B ficaria com -100!
        assertEquals(TransactionStatus.REJECTED, correction.getStatus());
        assertEquals("Saldo do destinatário insuficiente para estorno da transferência", correction.getRejectionReason());

        // Saldo de B permanece em 0.00
        assertEquals(0, BigDecimal.ZERO.compareTo(balanceService.getBalance(2L).effectiveBalance()));
    }
}
