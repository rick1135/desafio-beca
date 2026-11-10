package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.application.usecase.GetTransactionService;
import com.beca.transactions.application.usecase.ProcessTransactionService;
import com.beca.transactions.domain.model.AuthenticatedUser;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.cmpEq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionControllerTest {
    private CreateTransactionService service;
    private GetTransactionService getTransactionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authenticateAs(1L);
        service = mock(CreateTransactionService.class);
        getTransactionService = mock(GetTransactionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TransactionController(
                        service, getTransactionService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        var principal = new AuthenticatedUser(userId, "Usuário Teste", "teste@example.com");
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void returnsCreatedTransactionWithLocation() throws Exception {
        when(service.create(eq(1L), eq(TransactionType.DEPOSIT), cmpEq(new BigDecimal("25.00")),
                eq("Depósito de teste"), isNull(), isNull(), isNull(), isNull()))
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
    @DisplayName("deve registrar correção vinculada à transação original com motivo da correção")
    void createsCorrectionWithItsReason() throws Exception {
        // given
        given(service.create(eq(1L), eq(TransactionType.DEPOSIT), cmpEq(new BigDecimal("25.00")),
                eq("Correção"), isNull(), isNull(), eq(42L), eq("Valor incorreto")))
                .willReturn(new Transaction(43L, TransactionType.DEPOSIT, new BigDecimal("25.00"),
                        "Depósito", 1L, null, TransactionStatus.APPROVED, null, 42L,
                        "Correção", Instant.parse("2026-11-06T12:00:00Z"),
                        List.of(), "Valor incorreto"));

        // when / then
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":25.00,"description":"Correção",
                                 "originalTransactionId":42,"correctionReason":"Valor incorreto"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(43))
                .andExpect(jsonPath("$.originalTransactionId").value(42))
                .andExpect(jsonPath("$.correctionReason").value("Valor incorreto"));

        then(service).should().create(eq(1L), eq(TransactionType.DEPOSIT), cmpEq(new BigDecimal("25.00")),
                eq("Correção"), isNull(), isNull(), eq(42L), eq("Valor incorreto"));
    }

    @Test
    @DisplayName("deve retornar 400 quando originalTransactionId for enviado sem correctionReason")
    void rejectsCorrectionWithoutReasonBeforeCallingService() throws Exception {
        // when / then
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":25.00,"description":"Correção sem motivo",
                                 "originalTransactionId":42}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.correctionReason").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("deve retornar 400 quando correctionReason for enviado sem originalTransactionId")
    void rejectsCorrectionReasonWithoutOriginalIdBeforeCallingService() throws Exception {
        // when / then
        mvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":25.00,"description":"Depósito comum",
                                 "correctionReason":"Motivo órfão"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.originalTransactionId").exists());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("deve listar correções vinculadas a uma transação original do usuário autenticado")
    void listsCorrectionsForOriginalTransaction() throws Exception {
        // given
        Transaction correction = new Transaction(43L, TransactionType.DEPOSIT, new BigDecimal("25.00"),
                "Depósito", 1L, null, TransactionStatus.APPROVED, null, 42L,
                "Correção", Instant.parse("2026-11-06T12:30:00Z"),
                List.of(), "Valor lançado incorretamente");
        given(getTransactionService.getCorrections(42L, 1L)).willReturn(List.of(correction));

        // when / then
        mvc.perform(get("/transactions/42/corrections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(43))
                .andExpect(jsonPath("$[0].originalTransactionId").value(42))
                .andExpect(jsonPath("$[0].correctionReason").value("Valor lançado incorretamente"));

        then(getTransactionService).should().getCorrections(42L, 1L);
    }

    @Test
    void returnsPersistedRejectionWithReason() throws Exception {
        when(service.create(eq(1L), eq(TransactionType.WITHDRAW), cmpEq(new BigDecimal("25.00")),
                eq("Retirada de teste"), isNull(), isNull(), isNull(), isNull()))
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
    @DisplayName("deve ignorar header X-User-Id e usar o usuário autenticado no contexto")
    void ignoresForgedUserIdHeaderAndUsesAuthenticatedPrincipal() throws Exception {
        // given
        when(service.create(eq(1L), eq(TransactionType.DEPOSIT), cmpEq(new BigDecimal("25.00")),
                eq("Depósito de teste"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(transaction(TransactionType.DEPOSIT, TransactionStatus.APPROVED, null));

        // when / then
        mvc.perform(post("/transactions")
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEPOSIT","amount":25.00,"description":"Depósito de teste"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originUserId").value(1));

        verify(service).create(eq(1L), eq(TransactionType.DEPOSIT), cmpEq(new BigDecimal("25.00")),
                eq("Depósito de teste"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void createsTransferWithRecipient() throws Exception {
        when(service.create(eq(1L), eq(TransactionType.TRANSFER), cmpEq(new BigDecimal("25.00")),
                eq("Transferência de teste"), isNull(), eq(2L), isNull(), isNull()))
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
                eq("Compra de teste"), eq("Alimentação"), isNull(), isNull(), isNull()))
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
                eq("Compra internacional"), eq("Viagem"), isNull(), isNull(), isNull()))
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
                eq("Depósito sem câmbio"), isNull(), isNull(), isNull(), isNull()))
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
                                exchangeRates, repository), mock(GetTransactionService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
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

    @Test
    @DisplayName("deve listar transações do usuário autenticado filtrando por período e tipo")
    void listsTransactionsWithPeriodAndTypeFilters() throws Exception {
        // given
        LocalDate start = LocalDate.parse("2026-11-01");
        LocalDate end = LocalDate.parse("2026-11-06");
        Transaction purchase = new Transaction(42L, TransactionType.PURCHASE, new BigDecimal("50.00"),
                "Alimentação", 1L, null, TransactionStatus.APPROVED, null, null,
                "Almoço", Instant.parse("2026-11-06T15:00:00Z"));
        given(getTransactionService.list(1L, start, end, TransactionType.PURCHASE))
                .willReturn(List.of(purchase));

        // when / then
        mvc.perform(get("/transactions")
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-06")
                        .param("type", "PURCHASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$[0].category").value("Alimentação"))
                .andExpect(jsonPath("$[0].amount").value(50.00));

        then(getTransactionService).should().list(1L, start, end, TransactionType.PURCHASE);
    }

    @Test
    @DisplayName("deve retornar 400 ao listar transações com período inválido")
    void rejectsListWhenDateRangeIsInvalid() throws Exception {
        // given
        LocalDate start = LocalDate.parse("2026-11-10");
        LocalDate end = LocalDate.parse("2026-11-01");
        given(getTransactionService.list(1L, start, end, null))
                .willThrow(new com.beca.transactions.domain.exception.InvalidTransactionException(
                        "A data inicial deve ser anterior ou igual à data final."));

        // when / then
        mvc.perform(get("/transactions")
                        .param("startDate", "2026-11-10")
                        .param("endDate", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("A data inicial deve ser anterior ou igual à data final."));
    }

    private Transaction transaction(TransactionType type, TransactionStatus status, String reason) {
        String description = type == TransactionType.DEPOSIT
                ? "Depósito de teste" : "Retirada de teste";
        return new Transaction(42L, type, new BigDecimal("25.00"),
                null, 1L, null, status, reason, null, description,
                Instant.parse("2026-11-06T12:00:00Z"));
    }
}
