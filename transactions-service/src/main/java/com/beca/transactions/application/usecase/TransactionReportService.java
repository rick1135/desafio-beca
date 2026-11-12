package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TransactionReportService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CSV_HEADER = "data;tipo;categoria;descricao;valor_brl;status;motivo_rejeicao;valor_usd;valor_eur";
    private static final String UTF8_BOM = "\uFEFF";

    private final TransactionRepositoryPort transactions;
    private final Clock clock;

    @Autowired
    public TransactionReportService(TransactionRepositoryPort transactions) {
        this(transactions, Clock.system(ZONE));
    }

    public TransactionReportService(TransactionRepositoryPort transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock.withZone(ZONE);
    }

    public byte[] generateCsvReport(Long userId, LocalDate dataInicio, LocalDate dataFim) {
        LocalDate startDate = dataInicio;
        LocalDate endDate = dataFim;

        if (startDate == null && endDate == null) {
            YearMonth currentMonth = YearMonth.now(clock);
            startDate = currentMonth.atDay(1);
            endDate = currentMonth.atEndOfMonth();
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidTransactionException("A data inicial deve ser anterior ou igual à data final.");
        }

        List<Transaction> visibleTransactions = transactions.findVisibleByUserId(userId);

        StringBuilder sb = new StringBuilder();
        sb.append(UTF8_BOM).append(CSV_HEADER).append("\n");

        for (Transaction transaction : visibleTransactions) {
            LocalDate transactionDate = LocalDate.ofInstant(transaction.getCreationDate(), ZONE);
            if (startDate != null && transactionDate.isBefore(startDate)) {
                continue;
            }
            if (endDate != null && transactionDate.isAfter(endDate)) {
                continue;
            }

            sb.append(toCsvRow(transaction)).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String toCsvRow(Transaction transaction) {
        String data = transaction.getCreationDate() != null
                ? DATE_TIME_FORMATTER.format(transaction.getCreationDate().atZone(ZONE))
                : "";
        String tipo = transaction.getType() != null
                ? transaction.getType().name()
                : "";
        String categoria = transaction.getCategory() != null
                ? transaction.getCategory()
                : "";
        String descricao = transaction.getDescription() != null
                ? transaction.getDescription()
                : "";
        String valorBrl = transaction.getAmount() != null
                ? transaction.getAmount().setScale(2, RoundingMode.HALF_UP).toString()
                : "";
        String status = transaction.getStatus() != null
                ? transaction.getStatus().name()
                : "";
        String motivoRejeicao = transaction.getRejectionReason() != null
                ? transaction.getRejectionReason()
                : "";
        String valorUsd = extractConvertedAmount(transaction, ExchangeCurrency.USD);
        String valorEur = extractConvertedAmount(transaction, ExchangeCurrency.EUR);

        return String.join(";",
                escapeCsv(data),
                escapeCsv(tipo),
                escapeCsv(categoria),
                escapeCsv(descricao),
                escapeCsv(valorBrl),
                escapeCsv(status),
                escapeCsv(motivoRejeicao),
                escapeCsv(valorUsd),
                escapeCsv(valorEur)
        );
    }

    private String extractConvertedAmount(Transaction transaction, ExchangeCurrency currency) {
        if (transaction.getExchangeRates() == null || transaction.getAmount() == null) {
            return "";
        }
        return transaction.getExchangeRates().stream()
                .filter(rate -> rate.currency() == currency)
                .findFirst()
                .map(rate -> rate.convertFromBrl(transaction.getAmount()))
                .map(converted -> converted.setScale(2, RoundingMode.HALF_UP).toString())
                .orElse("");
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
