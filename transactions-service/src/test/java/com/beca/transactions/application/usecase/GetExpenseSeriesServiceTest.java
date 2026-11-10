package com.beca.transactions.application.usecase;

import com.beca.transactions.application.usecase.GetExpenseSeriesService.ExpensePoint;
import com.beca.transactions.application.usecase.GetExpenseSeriesService.ExpenseSeries;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetExpenseSeriesServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC
    );

    @Mock
    private TransactionRepositoryPort transactions;

    @Test
    @DisplayName("agrupa despesas por dia e mês em ordem cronológica e confere com o total geral")
    void groupsByLocalDateAndMonthAndMatchesCategoryTotal() {
        // given
        ExpenseSelection selection = new ExpenseSelection(transactions, CLOCK);
        GetExpenseSeriesService service = new GetExpenseSeriesService(selection);
        LocalDate start = LocalDate.parse("2026-10-30");
        LocalDate end = LocalDate.parse("2026-11-01");
        given(transactions.findApprovedByOriginUserId(1L)).willReturn(List.of(
                purchase(3L, "70.00", "2026-11-01T03:00:00Z", null),
                purchase(1L, "10.00", "2026-10-30T12:00:00Z", null),
                purchase(4L, "25.00", "2026-11-01T02:59:59Z", null),
                purchase(2L, "5.00", "2026-10-30T13:00:00Z", null),
                purchase(5L, "100.00", "2026-11-02T03:00:00Z", null)
        ));

        // when
        ExpenseSeries daily = service.daily(1L, start, end);
        ExpenseSeries monthly = service.monthly(1L, start, end);

        // then
        assertEquals(new ExpenseSeries("BRL", new BigDecimal("110.00"), List.of(
                point("2026-10-30", "15.00"),
                point("2026-10-31", "25.00"),
                point("2026-11-01", "70.00")
        )), daily);
        assertEquals(new ExpenseSeries("BRL", daily.total(), List.of(
                point("2026-10", "40.00"),
                point("2026-11", "70.00")
        )), monthly);
        assertEquals(daily.total(), new GetExpensesByCategoryService(selection)
                .getExpenses(1L, start, end).total());
    }

    @Test
    @DisplayName("computa correção aprovada exclusivamente no seu próprio mês e dia")
    void countsAnApprovedCorrectionOnlyInItsOwnMonth() {
        // given
        GetExpenseSeriesService service = new GetExpenseSeriesService(new ExpenseSelection(transactions, CLOCK));
        given(transactions.findApprovedByOriginUserId(1L)).willReturn(List.of(
                purchase(1L, "60.00", "2026-10-31T12:00:00Z", null),
                purchase(2L, "75.00", "2026-11-02T12:00:00Z", 1L)
        ));

        // when
        ExpenseSeries octoberMonthly = service.monthly(1L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
        ExpenseSeries currentMonthly = service.monthly(1L, null, null);
        ExpenseSeries currentDaily = service.daily(1L, null, null);

        // then
        assertEquals(new ExpenseSeries("BRL", new BigDecimal("0.00"), List.of()), octoberMonthly);
        assertEquals(new ExpenseSeries("BRL", new BigDecimal("75.00"), List.of(point("2026-11", "75.00"))), currentMonthly);
        assertEquals(new ExpenseSeries("BRL", new BigDecimal("75.00"), List.of(point("2026-11-02", "75.00"))), currentDaily);
    }

    private static ExpensePoint point(String period, String amount) {
        return new ExpensePoint(period, new BigDecimal(amount));
    }

    private static Transaction purchase(Long id, String amount, String createdAt, Long originalId) {
        return new Transaction(id, TransactionType.PURCHASE, new BigDecimal(amount),
                "Alimentação", 1L, null, TransactionStatus.APPROVED, null, originalId,
                "Compra", Instant.parse(createdAt));
    }
}

