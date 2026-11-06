package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.cmpEq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionControllerTest {
    private CreateTransactionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CreateTransactionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TransactionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsCreatedTransactionWithLocation() throws Exception {
        when(service.create(eq(1L), eq(TransactionType.DEPOSIT), cmpEq(new BigDecimal("25.00")),
                eq("Depósito de teste"), isNull(), isNull(), isNull()))
                .thenReturn(transaction(TransactionType.DEPOSIT, TransactionStatus.APPROVED, null));

        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":25.00,"description":"Depósito de teste"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/transactions/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.originUserId").value(1));
    }

    @Test
    void returnsPersistedRejectionWithReason() throws Exception {
        when(service.create(eq(1L), eq(TransactionType.WITHDRAW), cmpEq(new BigDecimal("25.00")),
                eq("Retirada de teste"), isNull(), isNull(), isNull()))
                .thenReturn(transaction(TransactionType.WITHDRAW, TransactionStatus.REJECTED,
                        "Limite diário excedido"));

        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"WITHDRAW","amount":25.00,"description":"Retirada de teste"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/transactions/42"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Limite diário excedido"));
    }

    @Test
    void rejectsInvalidRequestBeforeCallingService() throws Exception {
        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PURCHASE","amount":25.00,"description":"Compra"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.category").exists());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidUserIdBeforeCallingService() throws Exception {
        mvc.perform(post("/transactions")
                        .header("X-User-Id", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":25.00,"description":"Depósito de teste"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private Transaction transaction(TransactionType type, TransactionStatus status, String reason) {
        String description = type == TransactionType.DEPOSIT
                ? "Depósito de teste" : "Retirada de teste";
        return new Transaction(42L, type, new BigDecimal("25.00"),
                null, 1L, null, status, reason, null, description,
                Instant.parse("2026-11-06T12:00:00Z"));
    }
}
