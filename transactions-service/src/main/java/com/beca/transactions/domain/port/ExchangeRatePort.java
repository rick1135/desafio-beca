package com.beca.transactions.domain.port;

import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRatePort {
    Optional<ExchangeRate> findByCurrencyAndDate(ExchangeCurrency currency, LocalDate date);
}
