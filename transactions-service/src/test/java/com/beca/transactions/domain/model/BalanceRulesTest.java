package com.beca.transactions.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BalanceRulesTest {
    private final BalanceRules rules = new BalanceRules();
    private final AccountBalance account = new AccountBalance(1L, money("1000.00"),
            money("800.00"), money("500.00"));

    @Test
    void sumsOnlyApprovedDepositsAndOutflows() {
        Transaction deposit = transaction(TransactionType.DEPOSIT, TransactionStatus.APPROVED, "200.00");
        Transaction purchase = transaction(TransactionType.PURCHASE, TransactionStatus.APPROVED, "100.00");
        Transaction rejected = transaction(TransactionType.WITHDRAW, TransactionStatus.REJECTED, "50.00");
        Transaction transfer = transaction(TransactionType.TRANSFER, TransactionStatus.APPROVED, "75.00");

        var result = rules.calculate(account, List.of(deposit, purchase, rejected, transfer),
                List.of(purchase, rejected, transfer), List.of());

        assertEquals(0, money("1025.00").compareTo(result.effectiveBalance()));
        assertEquals(0, money("175.00").compareTo(result.dailySpent()));
    }

    @Test
    void incomingTransferIncreasesBalanceWithoutIncreasingDailySpending() {
        Transaction incoming = new Transaction(null, TransactionType.TRANSFER, money("75.00"),
                null, 2L, 1L, TransactionStatus.APPROVED, null, null,
                "Transferência recebida", Instant.parse("2026-11-06T12:00:00Z"));
        Transaction purchase = transaction(TransactionType.PURCHASE, TransactionStatus.APPROVED, "100.00");

        var result = rules.calculate(account, List.of(purchase), List.of(purchase), List.of(incoming));

        assertEquals(0, money("975.00").compareTo(result.effectiveBalance()));
        assertEquals(0, money("100.00").compareTo(result.dailySpent()));
    }

    @Test
    void rejectedIncomingTransferDoesNotChangeBalanceOrDailySpending() {
        Transaction incoming = new Transaction(null, TransactionType.TRANSFER, money("75.00"),
                null, 2L, 1L, TransactionStatus.REJECTED, "Saldo insuficiente", null,
                "Transferência rejeitada", Instant.parse("2026-11-06T12:00:00Z"));

        var result = rules.calculate(account, List.of(), List.of(), List.of(incoming));

        assertEquals(0, money("1000.00").compareTo(result.effectiveBalance()));
        assertEquals(0, money("0.00").compareTo(result.dailySpent()));
    }

    @Test
    void incomingListDoesNotCreditAnotherRecipient() {
        Transaction anotherRecipient = transaction(TransactionType.TRANSFER, TransactionStatus.APPROVED, "75.00");

        var result = rules.calculate(account, List.of(), List.of(), List.of(anotherRecipient));

        assertEquals(0, money("1000.00").compareTo(result.effectiveBalance()));
        assertEquals(0, money("0.00").compareTo(result.dailySpent()));
    }

    @Test
    void rejectsForInsufficientFundsBeforeOtherLimits() {
        var decision = rules.evaluateOutflow(account,
                new BalanceRules.BalanceSummary(money("50.00"), money("0.00")), money("100.00"));
        assertFalse(decision.approved());
        assertEquals("Saldo insuficiente", decision.rejectionReason());
    }

    @Test
    void rejectsWhenTransactionLimitIsExceeded() {
        var decision = rules.evaluateOutflow(account,
                new BalanceRules.BalanceSummary(money("1000.00"), money("0.00")), money("500.01"));
        assertFalse(decision.approved());
        assertEquals("Limite por transação excedido", decision.rejectionReason());
    }

    @Test
    void rejectsWhenDailyTotalWouldExceedLimit() {
        var decision = rules.evaluateOutflow(account,
                new BalanceRules.BalanceSummary(money("1000.00"), money("400.00")), money("400.01"));
        assertFalse(decision.approved());
        assertEquals("Limite diário excedido", decision.rejectionReason());
    }

    @Test
    void acceptsAmountsExactlyAtEachLimit() {
        var decision = rules.evaluateOutflow(account,
                new BalanceRules.BalanceSummary(money("500.00"), money("300.00")), money("500.00"));
        assertTrue(decision.approved());
        assertNull(decision.rejectionReason());
    }

    @Test
    @DisplayName("correção de depósito substitui o valor original e exige saldo para o débito líquido")
    void correctedDepositReplacesOriginalAndRequiresFundsForNetDebit() {
        // given
        Transaction original = entry(42L, TransactionType.DEPOSIT, "100.00", 1L, null, null);
        Transaction correction = entry(null, TransactionType.DEPOSIT, "20.00", 1L, null, 42L);

        // when
        var after = rules.calculate(account, List.of(original, correction), List.of(), List.of());
        var decision = rules.evaluateCorrection(account,
                new BalanceRules.BalanceSummary(money("20.00"), money("0.00")),
                original, correction, true);

        // then
        assertEquals(money("1020.00"), after.effectiveBalance());
        assertFalse(decision.approved());
        assertEquals("Saldo insuficiente", decision.rejectionReason());
    }

    @Test
    @DisplayName("correção de saída substitui o valor original e recompõe o limite diário")
    void correctedOutflowsReplaceOriginalAndFreeDailyLimit() {
        for (TransactionType type : new TransactionType[]{
                TransactionType.WITHDRAW, TransactionType.PURCHASE, TransactionType.TRANSFER}) {
            // given
            Transaction original = entry(42L, type, "100.00", 1L, 2L, null);
            Transaction correction = entry(null, type, "80.00", 1L, 2L, 42L);

            // when
            var after = rules.calculate(account, List.of(original, correction),
                    List.of(original, correction), List.of());
            var decision = rules.evaluateCorrection(account,
                    new BalanceRules.BalanceSummary(money("900.00"), money("100.00")),
                    original, correction, true);

            // then
            assertEquals(money("920.00"), after.effectiveBalance());
            assertEquals(money("80.00"), after.dailySpent());
            assertTrue(decision.approved());
        }
    }

    @Test
    @DisplayName("correção de transferência estorna o crédito do destinatário antigo e credita o novo destinatário")
    void transferCorrectionMovesCreditFromOldRecipientToNewRecipient() {
        // given
        Transaction original = entry(42L, TransactionType.TRANSFER, "100.00", 1L, 2L, null);
        Transaction correction = entry(null, TransactionType.TRANSFER, "80.00", 1L, 3L, 42L);
        AccountBalance oldRecipient = new AccountBalance(2L, money("0.00"), money("800.00"), money("500.00"));
        AccountBalance newRecipient = new AccountBalance(3L, money("0.00"), money("800.00"), money("500.00"));

        // when
        var oldRecipientSummary = rules.calculate(oldRecipient, List.of(), List.of(),
                List.of(original, correction));
        var newRecipientSummary = rules.calculate(newRecipient, List.of(), List.of(),
                List.of(correction));

        // then
        assertEquals(money("0.00"), oldRecipientSummary.effectiveBalance());
        assertEquals(money("80.00"), newRecipientSummary.effectiveBalance());
    }

    private Transaction entry(Long id, TransactionType type, String amount, Long origin,
                              Long destination, Long originalId) {
        return new Transaction(id, type, money(amount),
                type == TransactionType.PURCHASE ? "Alimentação" : null,
                origin, type == TransactionType.TRANSFER ? destination : null,
                TransactionStatus.APPROVED, null, originalId, "Teste",
                Instant.parse("2026-11-06T12:00:00Z"));
    }

    private Transaction transaction(TransactionType type, TransactionStatus status, String amount) {
        return new Transaction(null, type, money(amount),
                type == TransactionType.PURCHASE ? "Alimentação" : null,
                1L, type == TransactionType.TRANSFER ? 2L : null,
                status, status == TransactionStatus.REJECTED ? "Recusada" : null,
                null, "Teste", Instant.parse("2026-11-06T12:00:00Z"));
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
}
