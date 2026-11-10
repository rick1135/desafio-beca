package com.beca.transactions.application.usecase;

import com.beca.transactions.adapter.out.persistence.TransactionPersistenceAdapter;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.ExchangeRatePort;
import com.beca.transactions.domain.port.UserLookupPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16:///transactions_concurrency_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CreateTransactionConcurrencyTest {
    private static final Long USER_ID = 1L;

    @Autowired private CreateTransactionService service;
    @Autowired private GetBalanceService balanceService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private AccountBalancePort accounts;
    @MockitoBean private UserLookupPort users;
    @MockitoBean private ExchangeRatePort exchangeRates;
    @MockitoSpyBean private TransactionPersistenceAdapter transactions;

    @Test
    void deveAprovarApenasUmaDeDuasRetiradasSimultaneasQueExcedemOSaldo() throws Exception {
        jdbcTemplate.update("DELETE FROM transactions");
        when(accounts.findByUserId(USER_ID)).thenReturn(Optional.of(new AccountBalance(
                USER_ID, new BigDecimal("100.00"),
                new BigDecimal("1000.00"), new BigDecimal("500.00")
        )));

        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch ambasConsultaramSaldo = new CountDownLatch(2);

        // faz as duas chamadas observarem o mesmo histórico antes de continuar
        // após a correção, a primeira espera até o prazo e a segunda lê após o commit
        doAnswer(invocation -> {
            Object resultado = invocation.callRealMethod();
            ambasConsultaramSaldo.countDown();
            ambasConsultaramSaldo.await(2, TimeUnit.SECONDS);
            return resultado;
        }).when(transactions).findApprovedByOriginUserId(USER_ID);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeira = executor.submit(() -> {
                inicio.await();
                return retirar();
            });
            var segunda = executor.submit(() -> {
                inicio.await();
                return retirar();
            });

            inicio.countDown();
            Transaction resultado1 = primeira.get(10, TimeUnit.SECONDS);
            Transaction resultado2 = segunda.get(10, TimeUnit.SECONDS);

            assertEquals(1, java.util.stream.Stream.of(resultado1, resultado2)
                    .filter(t -> t.getStatus() == TransactionStatus.APPROVED).count());
            assertEquals(1, java.util.stream.Stream.of(resultado1, resultado2)
                    .filter(t -> t.getStatus() == TransactionStatus.REJECTED
                            && "Saldo insuficiente".equals(t.getRejectionReason())).count());
        } finally {
            jdbcTemplate.update("DELETE FROM transactions");
        }
    }

    @Test
    void deveSerializarCorrecaoDeTransferenciaEGastoConcorrenteSemSaldoNegativo() throws Exception {
        jdbcTemplate.update("DELETE FROM transactions");
        Long senderId = 1L;
        Long recipientId = 2L;

        when(users.existsById(recipientId)).thenReturn(true);
        when(accounts.findByUserId(senderId)).thenReturn(Optional.of(new AccountBalance(
                senderId, new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00")
        )));
        when(accounts.findByUserId(recipientId)).thenReturn(Optional.of(new AccountBalance(
                recipientId, new BigDecimal("0.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00")
        )));

        // 1. Remetente transfere 100 para destinatário (que parte do saldo 0)
        Transaction transfer = service.create(senderId, TransactionType.TRANSFER, new BigDecimal("100.00"),
                "Transferência inicial", null, recipientId, null);
        assertEquals(TransactionStatus.APPROVED, transfer.getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(balanceService.getBalance(recipientId).effectiveBalance()));

        CountDownLatch inicio = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            // Thread A: Destinatário tenta gastar os 100
            var gasto = executor.submit(() -> {
                inicio.await();
                return service.create(recipientId, TransactionType.PURCHASE, new BigDecimal("100.00"),
                        "Compra concorrente", "Alimentação", null, null);
            });

            // Thread B: Remetente tenta corrigir a transferência para 10 (reduzindo 90 do destinatário)
            var correcao = executor.submit(() -> {
                inicio.await();
                return service.create(senderId, TransactionType.TRANSFER, new BigDecimal("10.00"),
                        "Correção concorrente", null, recipientId, transfer.getId(), "Errei valor");
            });

            inicio.countDown();
            Transaction resGasto = gasto.get(10, TimeUnit.SECONDS);
            Transaction resCorrecao = correcao.get(10, TimeUnit.SECONDS);

            // Exatamente uma é aprovada e a outra é rejeitada por saldo insuficiente
            assertEquals(1, java.util.stream.Stream.of(resGasto, resCorrecao)
                    .filter(t -> t.getStatus() == TransactionStatus.APPROVED).count());
            assertEquals(1, java.util.stream.Stream.of(resGasto, resCorrecao)
                    .filter(t -> t.getStatus() == TransactionStatus.REJECTED).count());

            // INVARIANTE CRÍTICA: o saldo do destinatário NUNCA é negativo
            BigDecimal saldoFinal = balanceService.getBalance(recipientId).effectiveBalance();
            assertTrue(saldoFinal.signum() >= 0, "Saldo do destinatário não pode ficar negativo, atual: " + saldoFinal);
        } finally {
            jdbcTemplate.update("DELETE FROM transactions");
        }
    }

    private Transaction retirar() {
        return service.create(USER_ID, TransactionType.WITHDRAW,
                new BigDecimal("70.00"), "Retirada simultânea", null, null, null);
    }
}
