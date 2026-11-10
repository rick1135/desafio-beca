package com.beca.transactions.application.usecase;

import com.beca.transactions.application.usecase.GetExpensesByCategoryService.CategoryExpense;
import com.beca.transactions.application.usecase.GetExpensesByCategoryService.ExpenseSummary;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GetExpensesByCategoryServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-10T15:00:00Z"), ZONE
    );

    @Mock
    private TransactionRepositoryPort transactions;

    private GetExpensesByCategoryService service;

    @BeforeEach
    void setUp() {
        service = new GetExpensesByCategoryService(transactions, FIXED_CLOCK);
    }

    @Test
    @DisplayName("consolida saídas aprovadas do mês atual por categoria com total e percentual")
    void testGetExpenses_WhenNoDatesProvided_ShouldAggregateCurrentMonthApprovedOutflows() {
        // given
        Transaction food1 = new Transaction(1L, TransactionType.PURCHASE, money("60.00"),
                "Alimentação", 1L, null, TransactionStatus.APPROVED, null, null,
                "Almoço", Instant.parse("2026-11-05T15:00:00Z"));
        Transaction food2 = new Transaction(2L, TransactionType.PURCHASE, money("40.00"),
                " Alimentação ", 1L, null, TransactionStatus.APPROVED, null, null,
                "Jantar", Instant.parse("2026-11-08T22:00:00Z"));
        Transaction withdraw = new Transaction(3L, TransactionType.WITHDRAW, money("50.00"),
                null, 1L, null, TransactionStatus.APPROVED, null, null,
                "Saque", Instant.parse("2026-11-09T14:00:00Z"));
        Transaction transfer = new Transaction(4L, TransactionType.TRANSFER, money("50.00"),
                null, 1L, 2L, TransactionStatus.APPROVED, null, null,
                "Transferência", Instant.parse("2026-11-10T12:00:00Z"));
        Transaction deposit = new Transaction(5L, TransactionType.DEPOSIT, money("500.00"),
                null, 1L, null, TransactionStatus.APPROVED, null, null,
                "Depósito ignorado", Instant.parse("2026-11-02T12:00:00Z"));
        Transaction rejectedPurchase = new Transaction(6L, TransactionType.PURCHASE, money("90.00"),
                "Lazer", 1L, null, TransactionStatus.REJECTED, "Saldo insuficiente", null,
                "Compra rejeitada", Instant.parse("2026-11-06T12:00:00Z"));
        Transaction previousMonthPurchase = new Transaction(7L, TransactionType.PURCHASE, money("100.00"),
                "Transporte", 1L, null, TransactionStatus.APPROVED, null, null,
                "Mês anterior", Instant.parse("2026-10-31T20:00:00Z"));

        given(transactions.findApprovedByOriginUserId(1L)).willReturn(List.of(
                food1, food2, withdraw, transfer, deposit, rejectedPurchase, previousMonthPurchase
        ));

        // when
        ExpenseSummary summary = service.getExpenses(1L, null, null);

        // then
        assertEquals("BRL", summary.currency());
        assertEquals(money("200.00"), summary.total());
        assertEquals(List.of(
                new CategoryExpense("Alimentação", money("100.00"), money("50.00")),
                new CategoryExpense("Retirada", money("50.00"), money("25.00")),
                new CategoryExpense("Transferência", money("50.00"), money("25.00"))
        ), summary.categories());
    }

    @Test
    @DisplayName("ignora transação original substituída por correção aprovada ao somar despesas")
    void testGetExpenses_WhenTransactionWasCorrected_ShouldCountOnlyCorrection() {
        // given
        Transaction originalPurchase = new Transaction(10L, TransactionType.PURCHASE, money("100.00"),
                "Alimentação", 1L, null, TransactionStatus.APPROVED, null, null,
                "Compra original", Instant.parse("2026-11-06T12:00:00Z"));
        Transaction correctedPurchase = new Transaction(11L, TransactionType.PURCHASE, money("75.00"),
                "Alimentação", 1L, null, TransactionStatus.APPROVED, null, 10L,
                "Compra corrigida", Instant.parse("2026-11-06T13:00:00Z"),
                List.of(), "Valor lançado incorretamente");

        given(transactions.findApprovedByOriginUserId(1L)).willReturn(List.of(
                originalPurchase, correctedPurchase
        ));

        // when
        ExpenseSummary summary = service.getExpenses(1L, null, null);

        // then
        assertEquals(money("75.00"), summary.total());
        assertEquals(List.of(
                new CategoryExpense("Alimentação", money("75.00"), money("100.00"))
        ), summary.categories());
    }

    @Test
    @DisplayName("filtra despesas pelo intervalo de datas informado no fuso de São Paulo")
    void testGetExpenses_WhenDateRangeProvided_ShouldFilterBySaoPauloDate() {
        // given
        Transaction beforeRange = new Transaction(1L, TransactionType.PURCHASE, money("30.00"),
                "Alimentação", 1L, null, TransactionStatus.APPROVED, null, null,
                "Antes", Instant.parse("2026-11-05T02:59:59Z"));
        Transaction insideRange = new Transaction(2L, TransactionType.PURCHASE, money("80.00"),
                "Transporte", 1L, null, TransactionStatus.APPROVED, null, null,
                "Dentro", Instant.parse("2026-11-05T03:00:00Z"));
        Transaction afterRange = new Transaction(3L, TransactionType.PURCHASE, money("40.00"),
                "Lazer", 1L, null, TransactionStatus.APPROVED, null, null,
                "Depois", Instant.parse("2026-11-06T03:00:00Z"));

        given(transactions.findApprovedByOriginUserId(1L)).willReturn(List.of(
                beforeRange, insideRange, afterRange
        ));

        // when
        ExpenseSummary summary = service.getExpenses(
                1L, LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-05")
        );

        // then
        assertEquals(money("80.00"), summary.total());
        assertEquals(List.of(
                new CategoryExpense("Transporte", money("80.00"), money("100.00"))
        ), summary.categories());
    }

    @Test
    @DisplayName("retorna total zero e lista vazia quando não há despesas no período")
    void testGetExpenses_WhenNoOutflowsInPeriod_ShouldReturnZeroTotalAndEmptyList() {
        // given
        given(transactions.findApprovedByOriginUserId(1L)).willReturn(List.of());

        // when
        ExpenseSummary summary = service.getExpenses(1L, null, null);

        // then
        assertEquals("BRL", summary.currency());
        assertEquals(money("0.00"), summary.total());
        assertTrue(summary.categories().isEmpty());
    }

    @Test
    @DisplayName("lança exceção 400 quando data inicial é posterior à data final")
    void testGetExpenses_WhenStartDateIsAfterEndDate_ShouldThrowInvalidTransactionException() {
        // given
        LocalDate start = LocalDate.parse("2026-11-15");
        LocalDate end = LocalDate.parse("2026-11-10");

        // when / then
        assertThrows(InvalidTransactionException.class,
                () -> service.getExpenses(1L, start, end));
        verifyNoInteractions(transactions);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
