package com.beca.transactions.adapter.out.mockapi;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MockApiAccountBalanceAdapterTest {
    private HttpServer server;
    private MockApiAccountBalanceAdapter adapter;
    private String filteredBody;
    private String collectionBody;
    private int filteredStatus;
    private int collectionStatus;
    private int collectionCalls;

    @BeforeEach
    void startServer() throws IOException {
        filteredStatus = 200;
        collectionStatus = 200;
        filteredBody = account(1, "BRL");
        collectionBody = "[" + accountObject(1, "BRL") + "]";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/accounts", this::respond);
        server.start();
        adapter = new MockApiAccountBalanceAdapter("http://127.0.0.1:"
                + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void mapsValidAccountAndMonetaryFields() {
        var result = adapter.findByUserId(1L).orElseThrow();
        assertEquals(1L, result.userId());
        assertEquals(0, new java.math.BigDecimal("1000.00").compareTo(result.openingBalance()));
        assertEquals(0, new java.math.BigDecimal("800.00").compareTo(result.dailyLimit()));
        assertEquals(0, new java.math.BigDecimal("500.00").compareTo(result.transactionLimit()));
    }

    @Test
    void confirmsResourceExistsWhenFilteredQueryReturnsNotFound() {
        filteredStatus = 404;
        filteredBody = "\"Not found\"";

        assertTrue(adapter.findByUserId(999L).isEmpty());
        assertEquals(1, collectionCalls);
    }

    @Test
    void treatsMissingResourceAsExternalFailure() {
        filteredStatus = 404;
        collectionStatus = 404;
        filteredBody = "\"Not found\"";
        collectionBody = "\"Not found\"";

        assertThrows(ExternalServiceUnavailableException.class,
                () -> adapter.findByUserId(999L));
    }

    @Test
    void rejectsDuplicateAccountsForTheSameUser() {
        filteredBody = "[" + accountObject(1, "BRL") + "," + accountObject(1, "BRL") + "]";
        assertThrows(ExternalServiceUnavailableException.class,
                () -> adapter.findByUserId(1L));
    }

    @Test
    void rejectsMissingBalanceAndWrongCurrency() {
        filteredBody = "[{\"id\":\"1\",\"userId\":1,\"dailyLimit\":800,"
                + "\"transactionLimit\":500,\"currency\":\"BRL\"}]";
        assertThrows(ExternalServiceUnavailableException.class,
                () -> adapter.findByUserId(1L));

        filteredBody = account(1, "USD");
        assertThrows(ExternalServiceUnavailableException.class,
                () -> adapter.findByUserId(1L));
    }

    @Test
    void treatsServerErrorAsExternalFailure() {
        filteredStatus = 500;
        filteredBody = "\"Error\"";
        assertThrows(ExternalServiceUnavailableException.class,
                () -> adapter.findByUserId(1L));
    }

    private void respond(HttpExchange exchange) throws IOException {
        boolean filtered = exchange.getRequestURI().getRawQuery() != null;
        if (!filtered) collectionCalls++;
        int status = filtered ? filteredStatus : collectionStatus;
        byte[] body = (filtered ? filteredBody : collectionBody).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }

    private String account(int userId, String currency) {
        return "[" + accountObject(userId, currency) + "]";
    }

    private String accountObject(int userId, String currency) {
        return "{\"id\":\"1\",\"userId\":" + userId + ",\"balance\":1000,"
                + "\"dailyLimit\":800,\"transactionLimit\":500,\"currency\":\""
                + currency + "\"}";
    }
}
