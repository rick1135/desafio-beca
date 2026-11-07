package com.beca.transactions.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExchangeRate(
        ExchangeCurrency currency,
        BigDecimal sellingRate,
        LocalDate rateDate,
        LocalDateTime quotedAt,
        String bulletinType
) {
    public ExchangeRate {
        if (currency == null) {
            throw new IllegalArgumentException("Moeda é obrigatória");
        }
        if (sellingRate == null || sellingRate.signum() <= 0) {
            throw new IllegalArgumentException("Cotação de venda deve ser maior que zero");
        }
        if (rateDate == null) {
            throw new IllegalArgumentException("Data da cotação é obrigatória");
        }
        if (quotedAt == null) {
            throw new IllegalArgumentException("Horário da cotação é obrigatório");
        }
        if (bulletinType == null || bulletinType.isBlank()) {
            throw new IllegalArgumentException("Tipo do boletim é obrigatório");
        }
    }
}
