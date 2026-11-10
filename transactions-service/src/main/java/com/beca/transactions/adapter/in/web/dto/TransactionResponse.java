package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
import com.beca.transactions.domain.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant creationDate,
        String description,
        String category,
        Long originUserId,
        Long destinationUserId,
        TransactionStatus status,
        String rejectionReason,
        Long originalTransactionId,
        boolean conversionAvailable,
        List<ConvertedAmount> conversions,
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

    public record ConvertedAmount(ExchangeCurrency currency, BigDecimal amount,
                                  BigDecimal sellingRate, LocalDate rateDate,
                                  LocalDateTime quotedAt, String bulletinType) {}
}
