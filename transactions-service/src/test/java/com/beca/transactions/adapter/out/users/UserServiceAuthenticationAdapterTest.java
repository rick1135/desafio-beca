package com.beca.transactions.adapter.out.users;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.AuthenticatedUser;
import com.beca.transactions.config.UserServiceClientConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceAuthenticationAdapterTest {
    private HttpServer server;
    private UserServiceAuthenticationAdapter adapter;
    private int status;
    private String body;
    private String receivedAuthKey;
    private String receivedBody;

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        body = "{\"id\":15,\"nome\":\"Ana\",\"email\":\"ana@example.com\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/auth/validate", exchange -> {
            receivedAuthKey = exchange.getRequestHeaders().getFirst("X-Internal-Auth-Key");
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        adapter = new UserServiceAuthenticationAdapter(
                new UserServiceClientConfig().userServiceRestClient(
                        "http://127.0.0.1:" + server.getAddress().getPort(), 3000, 3000),
                "chave-interna-teste"
        );
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("retorna usuário autenticado quando MS1 responde 200 e envia X-Internal-Auth-Key")
    void testAuthenticate_WhenCredentialsAreValid_ShouldReturnAuthenticatedUser() {
        // given / when
        Optional<AuthenticatedUser> result = adapter.authenticate("ana@example.com", "Senha123");

        // then
        assertTrue(result.isPresent());
        assertEquals(new AuthenticatedUser(15L, "Ana", "ana@example.com"), result.get());
        assertEquals("chave-interna-teste", receivedAuthKey);
        assertTrue(receivedBody.contains("\"email\":\"ana@example.com\""));
    }

    @Test
    @DisplayName("retorna vazio quando MS1 responde 401 ou 400")
    void testAuthenticate_WhenCredentialsAreInvalid_ShouldReturnEmpty() {
        // given
        for (int code : new int[]{401, 400}) {
            status = code;
            body = "{\"status\":" + code + "}";

            // when / then
            assertTrue(adapter.authenticate("ana@example.com", "errada").isEmpty());
        }
    }

    @Test
    @DisplayName("lança ExternalServiceUnavailableException quando MS1 responde 503 ou 403")
    void testAuthenticate_WhenServerFailsOrRejectsKey_ShouldThrowExternalFailure() {
        // given
        for (int code : new int[]{503, 500, 403}) {
            status = code;
            body = "{\"status\":" + code + "}";

            // when / then
            assertThrows(ExternalServiceUnavailableException.class,
                    () -> adapter.authenticate("ana@example.com", "Senha123"));
        }
    }

    @Test
    @DisplayName("lança ExternalServiceUnavailableException quando resposta for malformada")
    void testAuthenticate_WhenResponseIsInvalid_ShouldThrowExternalFailure() {
        // given
        for (String invalidBody : new String[]{"", "{}", "{\"id\":0,\"email\":\"a@b.com\"}", "{\"id\":1}"}) {
            body = invalidBody;

            // when / then
            assertThrows(ExternalServiceUnavailableException.class,
                    () -> adapter.authenticate("ana@example.com", "Senha123"));
        }
    }

    @Test
    @DisplayName("trata conexão recusada pelo MS1 como indisponibilidade")
    void testAuthenticate_WhenConnectionIsRefused_ShouldThrowExternalFailure() {
        // given
        server.stop(0);

        // when / then
        assertThrows(ExternalServiceUnavailableException.class,
                () -> adapter.authenticate("ana@example.com", "Senha123"));
    }
}
