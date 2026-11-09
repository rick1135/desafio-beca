package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.exception.AccountBalanceNotFoundException;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.application.usecase.GetBalanceService;
import com.beca.transactions.config.ApiAuthenticationEntryPoint;
import com.beca.transactions.config.Ms1AuthenticationProvider;
import com.beca.transactions.config.SecurityConfig;
import com.beca.transactions.domain.model.AuthenticatedUser;
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
import java.util.Base64;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BalanceController.class)
@Import({SecurityConfig.class, Ms1AuthenticationProvider.class, ApiAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class BalanceControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetBalanceService service;

    @MockitoBean
    private UserAuthenticationPort authPort;

    @Test
    @DisplayName("deve retornar saldo efetivo e limites do usuário autenticado")
    void returnsEffectiveBalanceAndLimits() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));
        given(service.getBalance(1L)).willReturn(new GetBalanceService.BalanceDetails(
                1L, money("1000.00"), money("1025.00"), money("0.00"),
                money("800.00"), money("500.00")));

        // when / then
        mvc.perform(get("/balance")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123"))
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.openingBalance").value(1000.00))
                .andExpect(jsonPath("$.effectiveBalance").value(1025.00))
                .andExpect(jsonPath("$.dailyLimit").value(800.00))
                .andExpect(jsonPath("$.transactionLimit").value(500.00))
                .andExpect(jsonPath("$.currency").value("BRL"));

        then(service).should().getBalance(1L);
    }

    @Test
    @DisplayName("deve retornar 404 quando conta não existir na MockAPI")
    void returnsNotFoundForMissingAccount() throws Exception {
        // given
        given(authPort.authenticate("inexistente@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(999L, "Inexistente", "inexistente@example.com")));
        given(service.getBalance(999L)).willThrow(new AccountBalanceNotFoundException(999L));

        // when / then
        mvc.perform(get("/balance")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("inexistente@example.com", "Senha123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        then(service).should().getBalance(999L);
    }

    @Test
    @DisplayName("deve retornar 503 quando a MockAPI falhar")
    void returnsServiceUnavailableWhenMockApiFails() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));
        given(service.getBalance(1L)).willThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));

        // when / then
        mvc.perform(get("/balance")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        then(service).should().getBalance(1L);
    }

    @Test
    @DisplayName("deve retornar 401 quando credenciais não forem informadas")
    void returnsUnauthorizedWithoutCredentials() throws Exception {
        // when / then
        mvc.perform(get("/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"transactions-service\""))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));

        verifyNoInteractions(authPort, service);
    }

    @Test
    @DisplayName("deve retornar 401 quando credenciais forem recusadas pelo MS1")
    void returnsUnauthorizedForInvalidCredentials() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "errada")).willReturn(Optional.empty());

        // when / then
        mvc.perform(get("/balance")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));

        then(authPort).should().authenticate("ana@example.com", "errada");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("deve retornar 503 quando o MS1 de autenticação estiver indisponível")
    void returnsServiceUnavailableWhenMs1Fails() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willThrow(new ExternalServiceUnavailableException("MS1 de usuários", new RuntimeException("timeout")));

        // when / then
        mvc.perform(get("/balance")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(503));

        then(authPort).should().authenticate("ana@example.com", "Senha123");
        verifyNoInteractions(service);
    }

    private String basicAuth(String email, String password) {
        String token = Base64.getEncoder().encodeToString((email + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
}
