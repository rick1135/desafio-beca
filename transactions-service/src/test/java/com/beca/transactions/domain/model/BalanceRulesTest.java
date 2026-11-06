package com.beca.transactions.domain.model;

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
                List.of(purchase, rejected, transfer));

        assertEquals(0, money("1025.00").compareTo(result.effectiveBalance()));
        assertEquals(0, money("175.00").compareTo(result.dailySpent()));
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

    private Transaction transaction(TransactionType type, TransactionStatus status, String amount) {
        return new Transaction(null, type, money(amount),
                type == TransactionType.PURCHASE ? "Alimentação" : null,
                1L, type == TransactionType.TRANSFER ? 2L : null,
                status, status == TransactionStatus.REJECTED ? "Recusada" : null,
                null, "Teste", Instant.parse("2026-11-06T12:00:00Z"));
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
}
