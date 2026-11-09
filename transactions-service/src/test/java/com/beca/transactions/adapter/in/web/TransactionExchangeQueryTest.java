package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.application.usecase.GetTransactionService;
import com.beca.transactions.domain.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionExchangeQueryTest {
    private final GetTransactionService service = mock(GetTransactionService.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("retorna conversões históricas sem consultar API externa")
    void returnsHistoricalConversionsWithoutCallingExternalApi() throws Exception {
        // given
        authenticateAs(1L);
        when(service.getById(42L, 1L)).thenReturn(transaction(List.of(
                rate(ExchangeCurrency.USD, "5.00"), rate(ExchangeCurrency.EUR, "10.00"))));

        // when / then
        mvc().perform(get("/transactions/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.conversionAvailable").value(true))
                .andExpect(jsonPath("$.conversions[0].amount").value(20.00))
                .andExpect(jsonPath("$.conversions[1].amount").value(10.00))
                .andExpect(jsonPath("$.conversions[0].rateDate").value("2026-11-06"));
    }

    @Test
    @DisplayName("retorna BRL com HTTP 200 quando cotações não estiverem disponíveis")
    void returnsBrlWithHttp200WhenRatesWereUnavailable() throws Exception {
        // given
        authenticateAs(1L);
        when(service.getById(42L, 1L)).thenReturn(transaction(List.of()));

        // when / then
        mvc().perform(get("/transactions/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.conversionAvailable").value(false))
                .andExpect(jsonPath("$.conversions").isEmpty());
    }

    @Test
    @DisplayName("retorna 403 ao tentar acessar transação de outro usuário")
    void forbidsAccessToTransactionsOfOtherUsers() throws Exception {
        // given
        authenticateAs(2L);
        when(service.getById(42L, 2L)).thenThrow(new ForbiddenTransactionAccessException());

        // when / then
        mvc().perform(get("/transactions/42"))
                .andExpect(status().isForbidden());
    }

    private void authenticateAs(Long userId) {
        var principal = new AuthenticatedUser(userId, "Usuário", "user@example.com");
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Transaction transaction(List<ExchangeRate> rates) {
        return new Transaction(42L, TransactionType.DEPOSIT, new BigDecimal("100.00"),
                "Depósito", 1L, null, TransactionStatus.APPROVED, null, null,
                "Teste", Instant.parse("2026-11-06T16:00:00Z"), rates);
    }

    private ExchangeRate rate(ExchangeCurrency currency, String value) {
        return new ExchangeRate(currency, new BigDecimal(value), LocalDate.parse("2026-11-06"),
                LocalDateTime.parse("2026-11-06T13:06:13"), "FECHAMENTO PTAX");
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new TransactionController(
                mock(CreateTransactionService.class), service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
