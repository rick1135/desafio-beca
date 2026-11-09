package com.beca.transactions.adapter.out.persistence;

import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Embeddable
public class ExchangeRateEmbeddable {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private ExchangeCurrency currency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal sellingRate;

    @Column(nullable = false)
    private LocalDate rateDate;

    @Column(nullable = false)
    private LocalDateTime quotedAt;

    @Column(nullable = false, length = 40)
    private String bulletinType;

    protected ExchangeRateEmbeddable() {}

    public ExchangeRateEmbeddable(ExchangeRate rate) {
        currency = rate.currency();
        sellingRate = rate.sellingRate();
        rateDate = rate.rateDate();
        quotedAt = rate.quotedAt();
        bulletinType = rate.bulletinType();
    }

    public ExchangeRate toDomain() {
        return new ExchangeRate(currency, sellingRate, rateDate, quotedAt, bulletinType);
    }
}
