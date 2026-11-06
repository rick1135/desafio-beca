package com.beca.transactions.adapter.in.web.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        Long userId,
        BigDecimal openingBalance,
        BigDecimal effectiveBalance,
        BigDecimal dailySpent,
        BigDecimal dailyLimit,
        BigDecimal transactionLimit,
        String currency
) {
}
