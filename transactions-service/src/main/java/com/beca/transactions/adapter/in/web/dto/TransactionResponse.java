package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

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
        Long originalTransactionId
) {
}
