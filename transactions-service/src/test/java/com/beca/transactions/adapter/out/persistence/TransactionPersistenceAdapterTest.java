package com.beca.transactions.adapter.out.persistence;

import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(TransactionPersistenceAdapter.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
public class TransactionPersistenceAdapterTest {
    @Autowired
    private TransactionPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deveSalvarEBuscarTransacaoPorId() {
        Instant data = Instant.parse("2026-11-04T12:00:00Z");

        Transaction original = new Transaction(
                null,
                TransactionType.PURCHASE,
                new BigDecimal("25.50"),
                "Alimentação",
                1L,
                null,
                TransactionStatus.APPROVED,
                null,
                null,
                "Compra de teste",
                data
        );

        Transaction salva = adapter.save(original);
        assertNotNull(salva.getId());

        // Obriga a busca a carregar os dados novamente do banco.
        entityManager.clear();

        Transaction encontrada = adapter.findById(salva.getId()).orElseThrow();

        assertEquals(salva.getId(), encontrada.getId());
        assertEquals(TransactionType.PURCHASE, encontrada.getType());
        assertEquals(0, new BigDecimal("25.50").compareTo(encontrada.getAmount()));
        assertEquals("Alimentação", encontrada.getCategory());
        assertEquals(1L, encontrada.getOriginUserId());
        assertNull(encontrada.getDestinationUserId());
        assertEquals(TransactionStatus.APPROVED, encontrada.getStatus());
        assertNull(encontrada.getRejectionReason());
        assertNull(encontrada.getOriginalTransactionId());
        assertEquals("Compra de teste", encontrada.getDescription());
        assertEquals(data, encontrada.getCreationDate());
    }

    @Test
    void deveRetornarVazioQuandoIdNaoExiste() {
        assertTrue(adapter.findById(999999L).isEmpty());
    }
}
