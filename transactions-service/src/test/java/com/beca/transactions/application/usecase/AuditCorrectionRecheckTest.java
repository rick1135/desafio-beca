package com.beca.transactions.application.usecase;

import com.beca.transactions.adapter.out.persistence.PostgresAccountOperationLockAdapter;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.*;
import com.beca.transactions.domain.port.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16:///correction_recheck",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuditCorrectionRecheckTest {
    @Autowired CreateTransactionService create;
    @Autowired GetBalanceService balances;
    @Autowired TransactionRepositoryPort transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AccountBalancePort accounts;
    @MockitoBean UserLookupPort users;
    @MockitoBean ExchangeRatePort rates;
    @MockitoSpyBean PostgresAccountOperationLockAdapter locks;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM transactions");
        when(users.existsById(anyLong())).thenReturn(true);
        when(accounts.findByUserId(anyLong())).thenAnswer(i -> {
            long id = i.getArgument(0);
            return Optional.of(account(id, id == 1 ? "1000" : "0"));
        });
    }

    private AccountBalance account(long id, String initial) {
        return new AccountBalance(id, new BigDecimal(initial), new BigDecimal("10000"), new BigDecimal("10000"));
    }

    private Transaction transfer(long sender, long recipient, String amount) {
        return create.create(sender, TransactionType.TRANSFER, new BigDecimal(amount), "Audit transfer", null, recipient, null);
    }

    private Transaction correct(Transaction original, long recipient, String amount) {
        return create.create(original.getOriginUserId(), TransactionType.TRANSFER, new BigDecimal(amount),
                "Audit correction", null, recipient, original.getId(), "Amount or recipient correction");
    }

    @ParameterizedTest
    @CsvSource({
            "100, 2, 10, REJECTED, 0",
            "10, 2, 10, APPROVED, 0",
            "10.01, 2, 10, REJECTED, 89.99",
            "100, 2, 110, APPROVED, 10",
            "100, 3, 10, REJECTED, 0",
            "0, 3, 10, APPROVED, 0"
    })
    @DisplayName("preserva fundos do destinatário nos limites (suficiente, insuficiente e exato)")
    void preservesRecipientFunds(String spent, long recipient, String corrected, TransactionStatus expected, String finalBalance) {
        var original = transfer(1, 2, "100");
        if (new BigDecimal(spent).signum() > 0) {
            var purchase = create.create(2L, TransactionType.PURCHASE, new BigDecimal(spent), "Audit purchase", "Food", null, null);
            assertEquals(TransactionStatus.APPROVED, purchase.getStatus());
        }
        var result = correct(original, recipient, corrected);
        assertEquals(expected, result.getStatus());
        assertEquals(0, new BigDecimal(finalBalance).compareTo(balances.getBalance(2L).effectiveBalance()));
        assertEquals(TransactionStatus.APPROVED, transactions.findById(original.getId()).orElseThrow().getStatus());
        assertEquals(expected, transactions.findById(result.getId()).orElseThrow().getStatus());
        if (expected == TransactionStatus.REJECTED) {
            assertFalse(result.getRejectionReason().isBlank());
        }
    }

    @Test
    @DisplayName("rejeita correção quando MockAPI do destinatário está indisponível")
    void rejectsWhenRecipientMockIsUnavailable() {
        var original = transfer(1, 2, "100");
        when(accounts.findByUserId(2L)).thenThrow(new ExternalServiceUnavailableException("Mock audit", new RuntimeException("offline")));
        var result = correct(original, 2, "10");
        assertEquals(TransactionStatus.REJECTED, result.getStatus());
        assertTrue(result.getRejectionReason().contains("indisponível"));
    }

    @Test
    @DisplayName("rejeita correção quando conta do destinatário não é encontrada")
    void rejectsWhenRecipientAccountIsMissing() {
        var original = transfer(1, 2, "100");
        when(accounts.findByUserId(2L)).thenReturn(Optional.empty());
        assertEquals(TransactionStatus.REJECTED, correct(original, 2, "10").getStatus());
    }

    @Test
    @DisplayName("correções simultâneas em sentidos opostos A -> B e B -> A completam sem deadlock")
    void oppositeCorrectionsMustCompleteWithoutDeadlock() throws Exception {
        when(accounts.findByUserId(2L)).thenReturn(Optional.of(account(2, "1000")));
        var one = transfer(1, 2, "100");
        var two = transfer(2, 1, "100");
        CountDownLatch firstLocks = new CountDownLatch(2);
        Set<Thread> seen = ConcurrentHashMap.newKeySet();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (seen.add(Thread.currentThread())) {
                firstLocks.countDown();
                firstLocks.await(2, TimeUnit.SECONDS);
            }
            return null;
        }).when(locks).lockForUser(anyLong());

        List<Throwable> failures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var f1 = executor.submit(() -> correct(one, 2, "90"));
            var f2 = executor.submit(() -> correct(two, 1, "90"));
            for (var future : List.of(f1, f2)) {
                try {
                    assertEquals(TransactionStatus.APPROVED, future.get(15, TimeUnit.SECONDS).getStatus());
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
        }
        assertTrue(failures.isEmpty(), "Opposite corrections must not abort: " + failures);
    }

    @Test
    @DisplayName("duas correções simultâneas da mesma transação original nunca são ambas aprovadas")
    void concurrentCorrectionsOfSameOriginalMustNotBothBeApproved() throws Exception {
        var original = transfer(1, 2, "100");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var f1 = executor.submit(() -> {
                start.await();
                return correct(original, 2, "90");
            });
            var f2 = executor.submit(() -> {
                start.await();
                return correct(original, 2, "80");
            });

            start.countDown();
            Transaction t1 = null;
            Transaction t2 = null;
            int approvedCount = 0;

            try {
                t1 = f1.get(10, TimeUnit.SECONDS);
                if (t1.getStatus() == TransactionStatus.APPROVED) approvedCount++;
            } catch (ExecutionException ex) {
                assertInstanceOf(InvalidTransactionException.class, ex.getCause());
            }

            try {
                t2 = f2.get(10, TimeUnit.SECONDS);
                if (t2.getStatus() == TransactionStatus.APPROVED) approvedCount++;
            } catch (ExecutionException ex) {
                assertInstanceOf(InvalidTransactionException.class, ex.getCause());
            }

            assertEquals(1, approvedCount, "Exatamente uma correção da mesma transação original pode ser aprovada");
        }
    }

    @Test
    @DisplayName("troca de destinatário bloqueia todas as contas em ordem estritamente crescente")
    void changingRecipientLocksAllAccountsInAscendingOrder() {
        when(accounts.findByUserId(3L)).thenReturn(Optional.of(account(3, "1000")));
        var original = transfer(1, 3, "100");
        reset(locks);

        var result = correct(original, 2, "100");
        assertEquals(TransactionStatus.APPROVED, result.getStatus());

        InOrder inOrder = inOrder(locks);
        inOrder.verify(locks).lockForUser(1L);
        inOrder.verify(locks).lockForUser(2L);
        inOrder.verify(locks).lockForUser(3L);
    }
}
