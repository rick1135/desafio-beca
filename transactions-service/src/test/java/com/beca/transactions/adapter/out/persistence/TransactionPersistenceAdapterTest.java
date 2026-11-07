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
import java.util.Set;

import static java.util.stream.Collectors.toSet;
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

    @Test
    void deveSalvarEBuscarTransacaoRejeitadaComMotivo() {
        Transaction salva = adapter.save(new Transaction(
                null, TransactionType.WITHDRAW, new BigDecimal("50.00"),
                null, 1L, null, TransactionStatus.REJECTED,
                "Saldo insuficiente", null, "Retirada rejeitada",
                Instant.parse("2026-11-04T12:00:00Z")
        ));

        entityManager.clear();
        Transaction encontrada = adapter.findById(salva.getId()).orElseThrow();

        assertEquals(TransactionType.WITHDRAW, encontrada.getType());
        assertEquals(TransactionStatus.REJECTED, encontrada.getStatus());
        assertEquals("Saldo insuficiente", encontrada.getRejectionReason());
    }

    @Test
    void deveSalvarEBuscarTransferenciaComDestinatario() {
        Transaction salva = adapter.save(new Transaction(
                null, TransactionType.TRANSFER, new BigDecimal("75.00"),
                null, 1L, 2L, TransactionStatus.APPROVED,
                null, null, "Transferência de teste",
                Instant.parse("2026-11-04T12:00:00Z")
        ));

        entityManager.clear();
        Transaction encontrada = adapter.findById(salva.getId()).orElseThrow();

        assertEquals(TransactionType.TRANSFER, encontrada.getType());
        assertEquals(1L, encontrada.getOriginUserId());
        assertEquals(2L, encontrada.getDestinationUserId());
    }

    @Test
    void devePreservarVinculoDaCorrecaoETransacaoOriginal() {
        Instant data = Instant.parse("2026-11-04T12:00:00Z");
        Transaction original = adapter.save(new Transaction(
                null, TransactionType.DEPOSIT, new BigDecimal("25.00"),
                null, 1L, null, TransactionStatus.APPROVED,
                null, null, "Depósito original", data
        ));
        Transaction correcao = adapter.save(new Transaction(
                null, TransactionType.DEPOSIT, new BigDecimal("30.00"),
                null, 1L, null, TransactionStatus.APPROVED,
                null, original.getId(), "Correção do depósito", data.plusSeconds(60)
        ));

        entityManager.clear();
        Transaction originalEncontrada = adapter.findById(original.getId()).orElseThrow();
        Transaction correcaoEncontrada = adapter.findById(correcao.getId()).orElseThrow();

        assertNotEquals(originalEncontrada.getId(), correcaoEncontrada.getId());
        assertEquals(original.getId(), correcaoEncontrada.getOriginalTransactionId());
        assertEquals(new BigDecimal("30.00"), correcaoEncontrada.getAmount());
        assertEquals("Correção do depósito", correcaoEncontrada.getDescription());
        assertEquals(data.plusSeconds(60), correcaoEncontrada.getCreationDate());
        assertNull(originalEncontrada.getOriginalTransactionId());
        assertEquals(new BigDecimal("25.00"), originalEncontrada.getAmount());
        assertEquals("Depósito original", originalEncontrada.getDescription());
        assertEquals(data, originalEncontrada.getCreationDate());
    }

    @Test
    void devePersistirValorComDuasCasasDecimais() {
        Transaction salva = adapter.save(new Transaction(
                null, TransactionType.DEPOSIT, new BigDecimal("10.12"),
                null, 1L, null, TransactionStatus.APPROVED,
                null, null, "Depósito com duas casas decimais",
                Instant.parse("2026-11-04T12:00:00Z")
        ));

        entityManager.clear();
        Transaction encontrada = adapter.findById(salva.getId()).orElseThrow();

        assertEquals(new BigDecimal("10.12"), encontrada.getAmount());
        assertEquals(2, encontrada.getAmount().scale());
    }

    @Test
    void naoDeveSobrescreverTransacaoExistente() {
        Instant data = Instant.parse("2026-11-04T12:00:00Z");

        Transaction original = new Transaction(
                null, TransactionType.PURCHASE, new BigDecimal("25.50"),
                "Alimentação", 1L, null, TransactionStatus.APPROVED,
                null, null, "Compra original", data
        );

        Transaction salva = adapter.save(original);
        entityManager.clear();

        Transaction tentativa = new Transaction(
                salva.getId(), TransactionType.PURCHASE, new BigDecimal("99.00"),
                "Alimentação", 1L, null, TransactionStatus.APPROVED,
                null, null, "Valor alterado", data
        );

        assertThrows(IllegalArgumentException.class, () -> adapter.save(tentativa));

        entityManager.clear();
        Transaction encontrada = adapter.findById(salva.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("25.50").compareTo(encontrada.getAmount()));
        assertEquals("Compra original", encontrada.getDescription());
    }

    @Test
    void deveBuscarApenasTransacoesAprovadasDoUsuarioDeOrigem() {
        Instant data = Instant.parse("2026-11-06T12:00:00Z");

        Transaction deposito = salvar(TransactionType.DEPOSIT,
                TransactionStatus.APPROVED, 1L, data);
        Transaction compra = salvar(TransactionType.PURCHASE,
                TransactionStatus.APPROVED, 1L, data);
        salvar(TransactionType.WITHDRAW,
                TransactionStatus.REJECTED, 1L, data);
        salvar(TransactionType.DEPOSIT,
                TransactionStatus.APPROVED, 2L, data);

        entityManager.clear();

        Set<Long> idsEncontrados = adapter.findApprovedByOriginUserId(1L)
                .stream()
                .map(Transaction::getId)
                .collect(toSet());

        assertEquals(Set.of(deposito.getId(), compra.getId()), idsEncontrados);
    }

    @Test
    void deveBuscarApenasSaidasAprovadasNoIntervaloDiario() {
        // meia noite de 06/11 em America/Sao_Paulo equivale a 03:00Z.
        Instant inicio = Instant.parse("2026-11-06T03:00:00Z");
        Instant fim = Instant.parse("2026-11-07T03:00:00Z");

        Transaction retiradaNoInicio = salvar(TransactionType.WITHDRAW,
                TransactionStatus.APPROVED, 1L, inicio);
        Transaction transferencia = salvar(TransactionType.TRANSFER,
                TransactionStatus.APPROVED, 1L, inicio.plusSeconds(60));
        Transaction compra = salvar(TransactionType.PURCHASE,
                TransactionStatus.APPROVED, 1L, fim.minusSeconds(1));

        salvar(TransactionType.WITHDRAW,
                TransactionStatus.APPROVED, 1L, inicio.minusSeconds(1));
        salvar(TransactionType.WITHDRAW,
                TransactionStatus.APPROVED, 1L, fim);
        salvar(TransactionType.DEPOSIT,
                TransactionStatus.APPROVED, 1L, inicio.plusSeconds(60));
        salvar(TransactionType.PURCHASE,
                TransactionStatus.REJECTED, 1L, inicio.plusSeconds(60));
        salvar(TransactionType.WITHDRAW,
                TransactionStatus.APPROVED, 2L, inicio.plusSeconds(60));

        entityManager.clear();

        Set<Long> idsEncontrados =
                adapter.findApprovedOutflowsByOriginUserIdBetween(1L, inicio, fim)
                        .stream()
                        .map(Transaction::getId)
                        .collect(toSet());

        assertEquals(Set.of(
                retiradaNoInicio.getId(),
                transferencia.getId(),
                compra.getId()
        ), idsEncontrados);
    }

    @Test
    void deveBuscarApenasTransferenciasAprovadasRecebidasPeloDestinatario() {
        Instant data = Instant.parse("2026-11-06T12:00:00Z");
        Transaction primeira = salvar(TransactionType.TRANSFER, TransactionStatus.APPROVED, 1L, 2L, data);
        Transaction segunda = salvar(TransactionType.TRANSFER, TransactionStatus.APPROVED, 3L, 2L,
                data.minusSeconds(86400));
        salvar(TransactionType.TRANSFER, TransactionStatus.REJECTED, 1L, 2L, data);
        salvar(TransactionType.TRANSFER, TransactionStatus.APPROVED, 1L, 3L, data);
        salvar(TransactionType.TRANSFER, TransactionStatus.APPROVED, 2L, 1L, data);
        entityManager.clear();

        var recebidas = adapter.findApprovedIncomingTransfersByUserId(2L);

        assertEquals(2, recebidas.size());
        assertEquals(Set.of(primeira.getId(), segunda.getId()),
                recebidas.stream().map(Transaction::getId).collect(toSet()));
        assertTrue(recebidas.stream().allMatch(t -> t.getDestinationUserId().equals(2L)
                && t.getType() == TransactionType.TRANSFER
                && t.getStatus() == TransactionStatus.APPROVED));
    }

    @Test
    void deveRetornarVazioQuandoUsuarioNaoTemTransferenciasRecebidas() {
        salvar(TransactionType.TRANSFER, TransactionStatus.APPROVED, 1L, 2L,
                Instant.parse("2026-11-06T12:00:00Z"));
        entityManager.clear();

        assertTrue(adapter.findApprovedIncomingTransfersByUserId(3L).isEmpty());
    }

    private Transaction salvar(
            TransactionType type,
            TransactionStatus status,
            Long originUserId,
            Instant date
    ) {
        return salvar(type, status, originUserId, type == TransactionType.TRANSFER ? 2L : null, date);
    }

    private Transaction salvar(
            TransactionType type,
            TransactionStatus status,
            Long originUserId,
            Long destinationUserId,
            Instant date
    ) {
        return adapter.save(new Transaction(
                null,
                type,
                new BigDecimal("10.00"),
                type == TransactionType.PURCHASE ? "Alimentação" : null,
                originUserId,
                destinationUserId,
                status,
                status == TransactionStatus.REJECTED ? "Teste de rejeição" : null,
                null,
                "Transação de teste",
                date
        ));
    }
}
