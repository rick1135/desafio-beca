package com.beca.transactions.adapter.out.brasilapi;

import com.beca.transactions.domain.model.ExchangeCurrency;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrasilApiExchangeRateAdapterTest {
    private HttpServer server;
    private BrasilApiExchangeRateAdapter adapter;
    private int status = 200;
    private String body;
    private String previousDayBody;
    private final List<String> requestedPaths = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        status = 200;
        previousDayBody = null;
        requestedPaths.clear();
        body = response("USD", """
                {"cotacao_venda":5.50,"data_hora_cotacao":"2026-11-06 10:00:00.123","tipo_boletim":"ABERTURA"},
                {"cotacao_venda":5.51,"data_hora_cotacao":"2026-11-06 13:00:00.123456","tipo_boletim":"FECHAMENTO PTAX"}
                """);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/cambio/v1/cotacao", this::respond);
        server.start();
        adapter = new BrasilApiExchangeRateAdapter("http://127.0.0.1:"
                + server.getAddress().getPort() + "/api");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void prefersLastPtaxAndKeepsReturnedDate() {
        previousDayBody = response("USD", """
                {"cotacao_venda":5.50,"data_hora_cotacao":"2026-11-05 13:00:00.123","tipo_boletim":"FECHAMENTO PTAX"},
                {"cotacao_venda":5.51,"data_hora_cotacao":"2026-11-05 13:00:00.123456","tipo_boletim":"FECHAMENTO PTAX"},
                {"cotacao_venda":5.52,"data_hora_cotacao":"2026-11-05 13:10:00","tipo_boletim":"INTERMEDIÁRIO"}
                """, "2026-11-05");
        var rate = adapter.findByCurrencyAndDate(ExchangeCurrency.USD,
                LocalDate.parse("2026-11-06")).orElseThrow();
        assertEquals("5.51", rate.sellingRate().toPlainString());
        assertEquals(LocalDate.parse("2026-11-05"), rate.rateDate());
        assertEquals(123456000, rate.quotedAt().getNano());
        assertEquals(List.of(
                "/api/cambio/v1/cotacao/USD/2026-11-06",
                "/api/cambio/v1/cotacao/USD/2026-11-05"), requestedPaths);
    }

    @Test
    void usesLastQuoteWhenPtaxAbsent() {
        body = response("USD", """
                {"cotacao_venda":5.50,"data_hora_cotacao":"2026-11-06 10:00:00","tipo_boletim":"ABERTURA"},
                {"cotacao_venda":5.52,"data_hora_cotacao":"2026-11-06 13:00:00","tipo_boletim":"INTERMEDIÁRIO"}
                """);
        assertEquals("5.52", adapter.findByCurrencyAndDate(ExchangeCurrency.USD,
                LocalDate.parse("2026-11-06")).orElseThrow().sellingRate().toPlainString());
    }

    @Test
    void returnsEmptyOnHttpFailureOrInvalidQuote() {
        status = 503;
        assertTrue(adapter.findByCurrencyAndDate(ExchangeCurrency.USD,
                LocalDate.parse("2026-11-06")).isEmpty());
        assertEquals(1, requestedPaths.size());
        status = 200;
        body = response("USD", """
                {"cotacao_venda":0,"data_hora_cotacao":"2026-11-06 13:00:00","tipo_boletim":"FECHAMENTO PTAX"}
                """);
        assertTrue(adapter.findByCurrencyAndDate(ExchangeCurrency.USD,
                LocalDate.parse("2026-11-06")).isEmpty());
    }

    @Test
    void stopsAfterFivePreviousDaysWithoutQuotes() {
        status = 404;

        assertTrue(adapter.findByCurrencyAndDate(ExchangeCurrency.USD,
                LocalDate.parse("2026-11-06")).isEmpty());
        assertEquals(6, requestedPaths.size());
        assertEquals("/api/cambio/v1/cotacao/USD/2026-11-01", requestedPaths.getLast());
    }

    private String response(String currency, String quotes) {
        return response(currency, quotes, "2026-11-06");
    }

    private String response(String currency, String quotes, String date) {
        return "{\"cotacoes\":[" + quotes + "],\"moeda\":\"" + currency
                + "\",\"data\":\"" + date + "\"}";
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestedPaths.add(path);
        boolean missingToday = previousDayBody != null && path.endsWith("/2026-11-06");
        int responseStatus = missingToday ? 404 : status;
        byte[] bytes = (missingToday ? "{}" : previousDayBody != null ? previousDayBody : body)
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        try (var stream = exchange.getResponseBody()) {
            stream.write(bytes);
        }
    }
}
