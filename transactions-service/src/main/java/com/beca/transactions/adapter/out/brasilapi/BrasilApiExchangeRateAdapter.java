package com.beca.transactions.adapter.out.brasilapi;

import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
import com.beca.transactions.domain.port.ExchangeRatePort;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class BrasilApiExchangeRateAdapter implements ExchangeRatePort {
    private static final int MAX_DAYS_BACK = 5;
    private static final Logger log = LoggerFactory.getLogger(BrasilApiExchangeRateAdapter.class);
    private final RestClient client;

    public BrasilApiExchangeRateAdapter(@Value("${brasil-api.base-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public Optional<ExchangeRate> findByCurrencyAndDate(ExchangeCurrency currency, LocalDate date) {
        if (currency == null || date == null) {
            throw new IllegalArgumentException("Moeda e data são obrigatórias");
        }
        LocalDate requestedDate = date;
        try {
            for (int daysBack = 0; daysBack <= MAX_DAYS_BACK; daysBack++) {
                requestedDate = date.minusDays(daysBack);
                try {
                    return readQuote(currency, requestedDate);
                } catch (HttpClientErrorException.NotFound ex) {
                    // sem boletim na data consultada, tenta somente o dia anterior no 404
                }
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Cotação {} indisponível para {}: {}", currency, requestedDate, ex.toString());
            return Optional.empty();
        }
    }

    private Optional<ExchangeRate> readQuote(ExchangeCurrency currency, LocalDate date) {
        QuoteResponse response = client.get()
                .uri("/cambio/v1/cotacao/{currency}/{date}", currency, date)
                .retrieve().body(QuoteResponse.class);
        if (response == null || !currency.name().equals(response.currency())
                || response.date() == null || response.quotes() == null
                || response.quotes().isEmpty()) {
            return Optional.empty();
        }
        Quote chosen = null;
        for (Quote quote : response.quotes()) {
            if (quote != null && "FECHAMENTO PTAX".equals(quote.bulletinType())) {
                chosen = quote;
            }
        }
        if (chosen == null) {
            chosen = response.quotes().getLast();
        }
        if (chosen == null || chosen.sellingRate() == null
                || chosen.sellingRate().signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ExchangeRate(currency, chosen.sellingRate(),
                LocalDate.parse(response.date()),
                LocalDateTime.parse(chosen.quotedAt().replace(' ', 'T')),
                chosen.bulletinType()));
    }

    private record QuoteResponse(
            @JsonProperty("cotacoes") List<Quote> quotes,
            @JsonProperty("moeda") String currency,
            @JsonProperty("data") String date
    ) {}

    private record Quote(
            @JsonProperty("cotacao_venda") BigDecimal sellingRate,
            @JsonProperty("data_hora_cotacao") String quotedAt,
            @JsonProperty("tipo_boletim") String bulletinType
    ) {}
}
