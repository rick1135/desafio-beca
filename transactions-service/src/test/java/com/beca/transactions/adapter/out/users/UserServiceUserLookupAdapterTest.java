package com.beca.transactions.adapter.out.users;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.config.UserServiceClientConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceUserLookupAdapterTest {
    private HttpServer server;
    private UserServiceUserLookupAdapter adapter;
    private int status;
    private String body;
    private String requestedPath;

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        body = "{\"id\":2,\"name\":\"Destinatário\",\"email\":\"destino@example.com\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users", exchange -> {
            requestedPath = exchange.getRequestURI().getPath();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        adapter = new UserServiceUserLookupAdapter(
                new UserServiceClientConfig().userServiceRestClient(
                        "http://127.0.0.1:" + server.getAddress().getPort(), 3000, 3000));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("confirma usuário retornado pelo endpoint do MS1")
    void testLookup_WhenUserExists_ShouldReturnTrue() {
        //given when
        boolean exists = adapter.existsById(2L);

        //then
        assertTrue(exists);
        assertEquals("/users/2", requestedPath);
    }

    @Test
    @DisplayName("interpreta 404 como destinatário inexistente")
    void testLookup_WhenUserIsNotFound_ShouldReturnFalse() {
        //given
        status = 404;
        body = "{\"message\":\"Usuário não encontrado\"}";

        //when then
        assertFalse(adapter.existsById(2L));
    }

    @Test
    @DisplayName("trata falha e falta de autorização no MS1 como indisponibilidade")
    void testLookup_WhenServerRejectsRequest_ShouldThrowExternalFailure() {
        //given
        for (int responseStatus : new int[]{500, 401, 403}) {
            status = responseStatus;

            //when then
            assertThrows(ExternalServiceUnavailableException.class, () -> adapter.existsById(2L));
        }
    }

    @Test
    @DisplayName("não confirma destinatário com resposta inválida ou ID diferente")
    void testLookup_WhenResponseIsInvalid_ShouldThrowExternalFailure() {
        //given
        for (String responseBody : new String[]{"", "{}", "{\"id\":3}", "invalid json"}) {
            body = responseBody;

            //when then
            assertThrows(ExternalServiceUnavailableException.class, () -> adapter.existsById(2L));
        }
    }

    @Test
    @DisplayName("trata conexão recusada como indisponibilidade")
    void testLookup_WhenConnectionFails_ShouldThrowExternalFailure() {
        //given
        server.stop(0);

        //when then
        assertThrows(ExternalServiceUnavailableException.class, () -> adapter.existsById(2L));
    }
}
