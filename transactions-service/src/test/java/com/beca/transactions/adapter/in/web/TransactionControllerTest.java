package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.application.usecase.GetTransactionService;
import com.beca.transactions.application.usecase.ProcessTransactionService;
import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.AccountOperationLockPort;
import com.beca.transactions.domain.port.ExchangeRatePort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import com.beca.transactions.domain.port.UserLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        mvc = MockMvcBuilders.standaloneSetup(new TransactionController(
                        service, mock(GetTransactionService.class)))
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

    @Test
    void createsTransferWithRecipient() throws Exception {
        when(service.create(eq(1L), eq(TransactionType.TRANSFER), cmpEq(new BigDecimal("25.00")),
                eq("Transferência de teste"), isNull(), eq(2L), isNull()))
                .thenReturn(new Transaction(42L, TransactionType.TRANSFER, new BigDecimal("25.00"),
                        null, 1L, 2L, TransactionStatus.APPROVED, null, null,
                        "Transferência de teste", Instant.parse("2026-11-06T12:00:00Z")));

        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TRANSFER","amount":25.00,"description":"Transferência de teste","destinationUserId":2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.originUserId").value(1))
                .andExpect(jsonPath("$.destinationUserId").value(2));
    }

    @Test
    void createsPurchaseWithCategory() throws Exception {
        when(service.create(eq(1L), eq(TransactionType.PURCHASE), cmpEq(new BigDecimal("25.00")),
                eq("Compra de teste"), eq("Alimentação"), isNull(), isNull()))
                .thenReturn(new Transaction(42L, TransactionType.PURCHASE, new BigDecimal("25.00"),
                        "Alimentação", 1L, null, TransactionStatus.APPROVED, null, null,
                        "Compra de teste", Instant.parse("2026-11-06T12:00:00Z")));

        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PURCHASE","amount":25.00,"description":"Compra de teste","category":"Alimentação"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PURCHASE"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.category").value("Alimentação"));
    }

    @Test
    void returnsCreatedTransactionWithExchangeConversions() throws Exception {
        List<ExchangeRate> rates = List.of(
                new ExchangeRate(ExchangeCurrency.USD, new BigDecimal("5.00"),
                        LocalDate.parse("2026-11-06"), LocalDateTime.parse("2026-11-06T13:06:13"),
                        "FECHAMENTO PTAX"),
                new ExchangeRate(ExchangeCurrency.EUR, new BigDecimal("6.25"),
                        LocalDate.parse("2026-11-06"), LocalDateTime.parse("2026-11-06T13:06:13"),
                        "FECHAMENTO PTAX")
        );
        when(service.create(eq(1L), eq(TransactionType.PURCHASE), cmpEq(new BigDecimal("100.00")),
                eq("Compra internacional"), eq("Viagem"), isNull(), isNull()))
                .thenReturn(new Transaction(42L, TransactionType.PURCHASE, new BigDecimal("100.00"),
                        "Viagem", 1L, null, TransactionStatus.APPROVED, null, null,
                        "Compra internacional", Instant.parse("2026-11-06T12:00:00Z"), rates));

        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PURCHASE","amount":100.00,"description":"Compra internacional","category":"Viagem"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/transactions/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.conversionAvailable").value(true))
                .andExpect(jsonPath("$.conversions[0].currency").value("USD"))
                .andExpect(jsonPath("$.conversions[0].amount").value(20.00))
                .andExpect(jsonPath("$.conversions[0].sellingRate").value(5.00))
                .andExpect(jsonPath("$.conversions[1].currency").value("EUR"))
                .andExpect(jsonPath("$.conversions[1].amount").value(16.00))
                .andExpect(jsonPath("$.conversions[1].sellingRate").value(6.25));
    }

    @Test
    void returnsCreatedTransactionWhenExchangeIsUnavailable() throws Exception {
        when(service.create(eq(1L), eq(TransactionType.DEPOSIT), cmpEq(new BigDecimal("50.00")),
                eq("Depósito sem câmbio"), isNull(), isNull(), isNull()))
                .thenReturn(new Transaction(42L, TransactionType.DEPOSIT, new BigDecimal("50.00"),
                        null, 1L, null, TransactionStatus.APPROVED, null, null,
                        "Depósito sem câmbio", Instant.parse("2026-11-06T12:00:00Z"), List.of()));

        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":50.00,"description":"Depósito sem câmbio"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.conversionAvailable").value(false))
                .andExpect(jsonPath("$.conversions").isEmpty());
    }

    @Test
    void rejectsTransferWithoutRecipientBeforeCallingService() throws Exception {
        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TRANSFER","amount":25.00,"description":"Transferência de teste"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.destinationUserId").exists());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsDestinationOnPurchaseBeforeCallingService() throws Exception {
        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PURCHASE","amount":25.00,"description":"Compra de teste","category":"Alimentação","destinationUserId":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.destinationUserId").exists());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsSelfTransferBeforeAccessingExternalServices() throws Exception {
        TransactionRepositoryPort repository = mock(TransactionRepositoryPort.class);
        AccountBalancePort accounts = mock(AccountBalancePort.class);
        AccountOperationLockPort lock = mock(AccountOperationLockPort.class);
        UserLookupPort users = mock(UserLookupPort.class);
        ExchangeRatePort exchangeRates = mock(ExchangeRatePort.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new TransactionController(new CreateTransactionService(
                                accounts, users, new ProcessTransactionService(repository, lock),
                                exchangeRates), mock(GetTransactionService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/transactions")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TRANSFER","amount":25.00,"description":"Transferência para si mesmo","destinationUserId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Não é possível realizar transferência para si mesmo"));

        verifyNoInteractions(repository, accounts, lock, users, exchangeRates);
    }

    private Transaction transaction(TransactionType type, TransactionStatus status, String reason) {
        String description = type == TransactionType.DEPOSIT
                ? "Depósito de teste" : "Retirada de teste";
        return new Transaction(42L, type, new BigDecimal("25.00"),
                null, 1L, null, status, reason, null, description,
                Instant.parse("2026-11-06T12:00:00Z"));
    }
}
