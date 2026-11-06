package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.exception.AccountBalanceNotFoundException;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.application.usecase.GetBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BalanceControllerTest {
    private GetBalanceService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(GetBalanceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BalanceController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsEffectiveBalanceAndLimits() throws Exception {
        when(service.getBalance(1L)).thenReturn(new GetBalanceService.BalanceDetails(
                1L, money("1000.00"), money("1025.00"), money("0.00"),
                money("800.00"), money("500.00")));

        mvc.perform(get("/balance").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.openingBalance").value(1000.00))
                .andExpect(jsonPath("$.effectiveBalance").value(1025.00))
                .andExpect(jsonPath("$.dailyLimit").value(800.00))
                .andExpect(jsonPath("$.transactionLimit").value(500.00))
                .andExpect(jsonPath("$.currency").value("BRL"));
        verify(service).getBalance(1L);
    }

    @Test
    void returnsNotFoundForMissingAccount() throws Exception {
        when(service.getBalance(999L)).thenThrow(new AccountBalanceNotFoundException(999L));
        mvc.perform(get("/balance").header("X-User-Id", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returnsServiceUnavailableWhenMockApiFails() throws Exception {
        when(service.getBalance(1L)).thenThrow(new ExternalServiceUnavailableException(
                "MockAPI", new RuntimeException("timeout")));
        mvc.perform(get("/balance").header("X-User-Id", "1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void rejectsNonNumericUserId() throws Exception {
        mvc.perform(get("/balance").header("X-User-Id", "abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonPositiveUserIdBeforeCallingService() throws Exception {
        mvc.perform(get("/balance").header("X-User-Id", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ID do usuário inválido"));
        verifyNoInteractions(service);
    }

    private BigDecimal money(String value) { return new BigDecimal(value); }
}
