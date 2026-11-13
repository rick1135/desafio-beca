package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransactionReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-11-15T12:00:00Z"), ZONE
    );

    @Mock
    private TransactionRepositoryPort transactions;

    private TransactionReportService service;

    @BeforeEach
    void setUp() {
        service = new TransactionReportService(transactions, FIXED_CLOCK);
    }

    @Test
    @DisplayName("deve filtrar transações pelo mês corrente quando datas não forem informadas")
    void whenDatesAreNull_shouldDefaultToCurrentMonth() {
        // given
        Long userId = 1L;
        Transaction octTx = createTransaction(1L, TransactionType.PURCHASE, "100.00", "Alimentação",
                "Compra Outubro", Instant.parse("2026-10-31T20:00:00Z"), TransactionStatus.APPROVED, null, List.of());
        Transaction novTxStart = createTransaction(2L, TransactionType.PURCHASE, "50.00", "Alimentação",
                "Compra Início Novembro", Instant.parse("2026-11-01T10:00:00Z"), TransactionStatus.APPROVED, null, List.of());
        Transaction novTxEnd = createTransaction(3L, TransactionType.WITHDRAW, "80.00", "Retirada",
                "Saque Fim Novembro", Instant.parse("2026-11-30T22:00:00Z"), TransactionStatus.APPROVED, null, List.of());
        Transaction decTx = createTransaction(4L, TransactionType.PURCHASE, "200.00", "Transporte",
                "Compra Dezembro", Instant.parse("2026-12-01T10:00:00Z"), TransactionStatus.APPROVED, null, List.of());

        given(transactions.findVisibleByUserId(userId)).willReturn(List.of(novTxEnd, novTxStart, octTx, decTx));

        // when
        byte[] csvBytes = service.generateCsvReport(userId, null, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("Compra Início Novembro"));
        assertTrue(csv.contains("Saque Fim Novembro"));
        assertFalse(csv.contains("Compra Outubro"));
        assertFalse(csv.contains("Compra Dezembro"));
    }

    @Test
    @DisplayName("deve filtrar transações no intervalo customizado de datas")
    void whenCustomDateRangeProvided_shouldFilterCorrectly() {
        // given
        Long userId = 1L;
        Transaction beforeRange = createTransaction(1L, TransactionType.PURCHASE, "40.00", "Lazer",
                "Antes do período", Instant.parse("2026-11-09T15:00:00Z"), TransactionStatus.APPROVED, null, List.of());
        Transaction insideRange = createTransaction(2L, TransactionType.PURCHASE, "150.00", "Alimentação",
                "Dentro do período", Instant.parse("2026-11-15T15:00:00Z"), TransactionStatus.APPROVED, null, List.of());
        Transaction afterRange = createTransaction(3L, TransactionType.PURCHASE, "90.00", "Transporte",
                "Depois do período", Instant.parse("2026-11-21T15:00:00Z"), TransactionStatus.APPROVED, null, List.of());

        given(transactions.findVisibleByUserId(userId)).willReturn(List.of(afterRange, insideRange, beforeRange));

        // when
        byte[] csvBytes = service.generateCsvReport(userId, LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-20"));
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("Dentro do período"));
        assertFalse(csv.contains("Antes do período"));
        assertFalse(csv.contains("Depois do período"));
    }

    @Test
    @DisplayName("deve lançar InvalidTransactionException quando data inicial for posterior à data final")
    void whenStartDateAfterEndDate_shouldThrowInvalidTransactionException() {
        // given
        Long userId = 1L;
        LocalDate start = LocalDate.parse("2026-11-20");
        LocalDate end = LocalDate.parse("2026-11-10");

        // when / then
        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> service.generateCsvReport(userId, start, end));
        assertEquals("A data inicial deve ser anterior ou igual à data final.", ex.getMessage());
        verifyNoInteractions(transactions);
    }

    @Test
    @DisplayName("deve lançar InvalidTransactionException quando apenas dataInicial for fornecida")
    void whenOnlyStartDateProvided_shouldThrowInvalidTransactionException() {
        // given
        Long userId = 1L;
        LocalDate start = LocalDate.parse("2026-11-01");

        // when / then
        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> service.generateCsvReport(userId, start, null));
        assertEquals("Para filtrar por período, informe a data inicial e a data final.", ex.getMessage());
        verifyNoInteractions(transactions);
    }

    @Test
    @DisplayName("deve lançar InvalidTransactionException quando apenas dataFim for fornecida")
    void whenOnlyEndDateProvided_shouldThrowInvalidTransactionException() {
        // given
        Long userId = 1L;
        LocalDate end = LocalDate.parse("2026-11-30");

        // when / then
        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> service.generateCsvReport(userId, null, end));
        assertEquals("Para filtrar por período, informe a data inicial e a data final.", ex.getMessage());
        verifyNoInteractions(transactions);
    }

    @Test
    @DisplayName("deve lançar InvalidTransactionException quando o período for superior a 1 ano")
    void whenDateRangeExceedsOneYear_shouldThrowInvalidTransactionException() {
        // given
        Long userId = 1L;
        LocalDate start = LocalDate.parse("2025-01-01");
        LocalDate end = LocalDate.parse("2026-01-02");

        // when / then
        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> service.generateCsvReport(userId, start, end));
        assertEquals("O período do relatório não pode ser superior a 1 ano.", ex.getMessage());
        verifyNoInteractions(transactions);
    }

    @Test
    @DisplayName("não deve gerar erro 500 nem NPE quando transação não possuir data de criação")
    void whenTransactionHasNullCreationDate_shouldSkipWithoutThrowingException() {
        // given
        Long userId = 1L;
        Transaction txWithDate = createTransaction(1L, TransactionType.PURCHASE, "50.00", "Alimentação",
                "Com data", Instant.parse("2026-11-10T10:00:00Z"), TransactionStatus.APPROVED, null, List.of());
        Transaction txWithoutDate = createTransaction(2L, TransactionType.PURCHASE, "30.00", "Lazer",
                "Sem data", null, TransactionStatus.APPROVED, null, List.of());

        given(transactions.findVisibleByUserId(userId)).willReturn(List.of(txWithDate, txWithoutDate));

        // when
        byte[] csvBytes = service.generateCsvReport(userId, null, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("Com data"));
        assertFalse(csv.contains("Sem data"));
    }

    @Test
    @DisplayName("deve neutralizar caracteres de injeção de fórmulas (=, +, -, @) com apóstrofo")
    void whenFieldsStartWithFormulaCharacters_shouldNeutralizeWithSingleQuote() {
        // given
        Long userId = 1L;
        Transaction txFormula1 = createTransaction(1L, TransactionType.PURCHASE, "10.00", "=SUM(A1:A10)",
                "+cmd|' /C calc'!A0", Instant.parse("2026-11-10T10:00:00Z"), TransactionStatus.APPROVED, null, List.of());
        Transaction txFormula2 = createTransaction(2L, TransactionType.PURCHASE, "20.00", "-categoria",
                "@administrador", Instant.parse("2026-11-10T11:00:00Z"), TransactionStatus.APPROVED, null, List.of());

        given(transactions.findVisibleByUserId(userId)).willReturn(List.of(txFormula1, txFormula2));

        // when
        byte[] csvBytes = service.generateCsvReport(userId, null, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("'=SUM(A1:A10)"));
        assertTrue(csv.contains("'+cmd|' /C calc'!A0"));
        assertTrue(csv.contains("'-categoria"));
        assertTrue(csv.contains("'@administrador"));
    }

    @Test
    @DisplayName("deve iniciar o arquivo com o BOM UTF-8 (0xEF, 0xBB, 0xBF) e cabeçalho exato")
    void csvContent_shouldStartWithUtf8BomAndHeader() {
        // given
        Long userId = 1L;
        given(transactions.findVisibleByUserId(userId)).willReturn(List.of());

        // when
        byte[] csvBytes = service.generateCsvReport(userId, null, null);

        // then
        assertNotNull(csvBytes);
        assertTrue(csvBytes.length >= 3);
        assertEquals((byte) 0xEF, csvBytes[0]);
        assertEquals((byte) 0xBB, csvBytes[1]);
        assertEquals((byte) 0xBF, csvBytes[2]);

        String csvString = new String(csvBytes, StandardCharsets.UTF_8);
        String expectedHeader = "\uFEFFdata;tipo;categoria;descricao;valor_brl;status;motivo_rejeicao;valor_usd;valor_eur\n";
        assertEquals(expectedHeader, csvString);
    }

    @Test
    @DisplayName("deve preencher valor_usd e valor_eur com 2 casas decimais a partir das cotações")
    void whenExchangeRatesPresent_shouldIncludeUsdAndEurConversions() {
        // given
        Long userId = 1L;
        ExchangeRate usdRate = new ExchangeRate(ExchangeCurrency.USD, new BigDecimal("5.00"),
                LocalDate.parse("2026-11-10"), LocalDateTime.parse("2026-11-10T13:00:00"), "Fechamento");
        ExchangeRate eurRate = new ExchangeRate(ExchangeCurrency.EUR, new BigDecimal("6.00"),
                LocalDate.parse("2026-11-10"), LocalDateTime.parse("2026-11-10T13:00:00"), "Fechamento");

        Transaction tx = createTransaction(1L, TransactionType.PURCHASE, "150.00", "Alimentação",
                "Almoço executivo", Instant.parse("2026-11-10T15:00:00Z"),
                TransactionStatus.APPROVED, null, List.of(usdRate, eurRate));

        given(transactions.findVisibleByUserId(userId)).willReturn(List.of(tx));

        // when
        byte[] csvBytes = service.generateCsvReport(userId, null, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // then
        // 150.00 BRL / 5.00 = 30.00 USD, 150.00 BRL / 6.00 = 25.00 EUR
        assertTrue(csv.contains("2026-11-10 12:00:00;PURCHASE;Alimentação;Almoço executivo;150.00;APPROVED;;30.00;25.00"));
    }

    @Test
    @DisplayName("deve deixar colunas valor_usd e valor_eur vazias quando cotações não estiverem presentes")
    void whenExchangeRatesEmpty_shouldLeaveUsdAndEurEmpty() {
        // given
        Long userId = 1L;
        Transaction tx = createTransaction(1L, TransactionType.DEPOSIT, "500.00", "Depósito",
                "Depósito em conta", Instant.parse("2026-11-10T15:00:00Z"),
                TransactionStatus.APPROVED, null, List.of());

        given(transactions.findVisibleByUserId(userId)).willReturn(List.of(tx));

        // when
        byte[] csvBytes = service.generateCsvReport(userId, null, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("2026-11-10 12:00:00;DEPOSIT;Depósito;Depósito em conta;500.00;APPROVED;;;"));
    }

    @Test
    @DisplayName("deve escapar adequadamente campos com ponto e vírgula, aspas e quebras de linha")
    void whenFieldsContainSpecialCharacters_shouldEscapeWithDoubleQuotes() {
        // given
        Long userId = 1L;
        Transaction tx = createTransaction(1L, TransactionType.PURCHASE, "120.00", "Restaurante; Gourmet",
                "Lanche \"Especial\"; combo\ncom batata", Instant.parse("2026-11-10T15:00:00Z"),
                TransactionStatus.APPROVED, null, List.of());

        given(transactions.findVisibleByUserId(userId)).willReturn(List.of(tx));

        // when
        byte[] csvBytes = service.generateCsvReport(userId, null, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains("\"Restaurante; Gourmet\""));
        assertTrue(csv.contains("\"Lanche \"\"Especial\"\"; combo\ncom batata\""));
    }

    @Test
    @DisplayName("deve incluir status REJECTED e motivo de rejeição na linha do relatório")
    void whenTransactionIsRejected_shouldIncludeRejectionReason() {
        // given
        Long userId = 1L;
        Transaction tx = createTransaction(1L, TransactionType.PURCHASE, "1000.00", "Eletrônicos",
                "Compra recusada", Instant.parse("2026-11-10T15:00:00Z"),
                TransactionStatus.REJECTED, "Saldo insuficiente para a transação", List.of());

        given(transactions.findVisibleByUserId(userId)).willReturn(List.of(tx));

        // when
        byte[] csvBytes = service.generateCsvReport(userId, null, null);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // then
        assertTrue(csv.contains(";REJECTED;Saldo insuficiente para a transação;;"));
    }

    private Transaction createTransaction(Long id, TransactionType type, String amount, String category,
                                          String description, Instant creationDate,
                                          TransactionStatus status, String rejectionReason,
                                          List<ExchangeRate> exchangeRates) {
        return new Transaction(
                id,
                type,
                new BigDecimal(amount),
                category,
                1L,
                type == TransactionType.TRANSFER ? 2L : null,
                status,
                rejectionReason,
                null,
                description,
                creationDate,
                exchangeRates,
                null
        );
    }
}
