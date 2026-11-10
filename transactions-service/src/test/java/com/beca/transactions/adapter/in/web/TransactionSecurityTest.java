package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.application.usecase.GetTransactionService;
import com.beca.transactions.config.ApiAuthenticationEntryPoint;
import com.beca.transactions.config.Ms1AuthenticationProvider;
import com.beca.transactions.config.SecurityConfig;
import com.beca.transactions.domain.model.AuthenticatedUser;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import com.beca.transactions.domain.port.UserAuthenticationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, Ms1AuthenticationProvider.class,
        ApiAuthenticationEntryPoint.class, GetTransactionService.class, GlobalExceptionHandler.class})
class TransactionSecurityTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CreateTransactionService createService;
    @MockitoBean private TransactionRepositoryPort transactions;
    @MockitoBean private UserAuthenticationPort authPort;

    @Test
    @DisplayName("deve retornar 401 ao consultar transação sem credenciais")
    void rejectsRequestWithoutCredentials() throws Exception {
        // when / then
        mvc.perform(get("/transactions/42"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(authPort, transactions, createService);
    }

    @Test
    @DisplayName("deve retornar 401 ao consultar correções de uma transação sem credenciais")
    void rejectsCorrectionsQueryWithoutCredentials() throws Exception {
        // when / then
        mvc.perform(get("/transactions/42/corrections"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(authPort, transactions, createService);
    }

    @Test
    @DisplayName("deve listar correções usando o usuário autenticado e ignorar header X-User-Id")
    void listsCorrectionsOnlyForAuthenticatedOwner() throws Exception {
        // given
        given(authPort.authenticate("b@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(2L, "Usuário B", "b@example.com")));
        given(transactions.findById(42L)).willReturn(Optional.of(new Transaction(
                42L, TransactionType.DEPOSIT, new BigDecimal("25.00"), null,
                2L, null, TransactionStatus.APPROVED, null, null,
                "Original", Instant.parse("2026-11-06T12:00:00Z"))));
        given(transactions.findCorrectionsByOriginalTransactionId(42L, 2L)).willReturn(List.of());

        // when / then
        mvc.perform(get("/transactions/42/corrections")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("b@example.com", "Senha123"))
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        then(transactions).should().findCorrectionsByOriginalTransactionId(42L, 2L);
    }

    @Test
    @DisplayName("deve retornar 403 quando usuário B autenticado tentar consultar transação do usuário A")
    void forbidsOtherUsersTransaction() throws Exception {
        // given
        given(authPort.authenticate("b@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(2L, "Usuário B", "b@example.com")));
        given(transactions.findById(42L)).willReturn(Optional.of(new Transaction(
                42L, TransactionType.DEPOSIT, new BigDecimal("25.00"), null,
                1L, null, TransactionStatus.APPROVED, null, null,
                "Depósito de A", Instant.parse("2026-11-06T12:00:00Z"))));

        // when / then
        mvc.perform(get("/transactions/42")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("b@example.com", "Senha123"))
                        .header("X-User-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        then(authPort).should().authenticate("b@example.com", "Senha123");
        then(transactions).should().findById(42L);
        verifyNoInteractions(createService);
    }

    private String basicAuth(String email, String password) {
        String token = Base64.getEncoder().encodeToString(
                (email + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
