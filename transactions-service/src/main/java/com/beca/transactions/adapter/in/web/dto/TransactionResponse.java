package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
import com.beca.transactions.domain.model.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionResponse(
        @Schema(description = "Identificador único da transação", example = "1")
        Long id,

        @Schema(description = "Tipo da transação", example = "EXPENSE")
        TransactionType type,

        @Schema(description = "Valor original da transação", example = "150.00")
        BigDecimal amount,

        @Schema(description = "Moeda da transação", example = "BRL")
        String currency,

        @Schema(description = "Data e hora de criação da transação em UTC", example = "2026-11-10T14:30:00Z")
        Instant creationDate,

        @Schema(description = "Descrição da transação", example = "Almoço de negócios")
        String description,

        @Schema(description = "Categoria da despesa", example = "ALIMENTACAO")
        String category,

        @Schema(description = "ID do usuário de origem", example = "1")
        Long originUserId,

        @Schema(description = "ID do usuário de destino (em transferências)", example = "2")
        Long destinationUserId,

        @Schema(description = "Status da transação: APPROVED ou REJECTED", example = "APPROVED")
        TransactionStatus status,

        @Schema(description = "Motivo da rejeição (se aplicável)", example = "Saldo insuficiente")
        String rejectionReason,

        @Schema(description = "ID da transação original se esta for uma correção", example = "10")
        Long originalTransactionId,

        @Schema(description = "Indica se conversão cambial está disponível", example = "true")
        boolean conversionAvailable,

        @Schema(description = "Cotações e valores convertidos para moedas estrangeiras")
        List<ConvertedAmount> conversions,

        @Schema(description = "Motivo da correção (se aplicável)", example = "Valor digitado incorretamente")
        String correctionReason
) {
    public static TransactionResponse from(Transaction transaction) {
        List<ConvertedAmount> conversions = transaction.getExchangeRates().stream()
                .map(rate -> converted(transaction.getAmount(), rate))
                .toList();
        return new TransactionResponse(
                transaction.getId(), transaction.getType(), transaction.getAmount(), "BRL",
                transaction.getCreationDate(), transaction.getDescription(), transaction.getCategory(),
                transaction.getOriginUserId(), transaction.getDestinationUserId(),
                transaction.getStatus(), transaction.getRejectionReason(),
                transaction.getOriginalTransactionId(), conversions.size() == 2, conversions,
                transaction.getCorrectionReason());
    }

    private static ConvertedAmount converted(BigDecimal amount, ExchangeRate rate) {
        return new ConvertedAmount(rate.currency(),
                rate.convertFromBrl(amount),
                rate.sellingRate(), rate.rateDate(), rate.quotedAt(), rate.bulletinType());
    }

    public record ConvertedAmount(
            @Schema(description = "Moeda de conversão: USD ou EUR", example = "USD")
            ExchangeCurrency currency,

            @Schema(description = "Valor convertido", example = "26.50")
            BigDecimal amount,

            @Schema(description = "Taxa de venda utilizada", example = "5.6600")
            BigDecimal sellingRate,

            @Schema(description = "Data de referência da cotação", example = "2026-11-10")
            LocalDate rateDate,

            @Schema(description = "Data e hora exata da cotação", example = "2026-11-10T13:00:00")
            LocalDateTime quotedAt,

            @Schema(description = "Tipo do boletim cambial", example = "Fechamento")
            String bulletinType) {}
}
