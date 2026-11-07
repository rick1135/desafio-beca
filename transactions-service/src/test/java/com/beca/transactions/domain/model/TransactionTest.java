package com.beca.transactions.domain.model;

import com.beca.transactions.domain.exception.InvalidTransactionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionTest {
    private static final Instant CREATION_DATE = Instant.parse("2026-11-04T12:00:00Z");
    @Test
    @DisplayName("deve rejeitar compra sem categoria")
    void testPurchase_WhenCategoryIsMissing_ShouldThrowException() {
        //given
        TransactionType type =  TransactionType.PURCHASE;
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                null,
                type, amount, null, 1L, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        ));
    }

    @Test
    @DisplayName("deve rejeitar transação sem tipo")
    void testTransaction_WhenTypeIsMissing_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                null,
                null, amount, null,  1L, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        ));
    }

    @Test
    @DisplayName("deve rejeitar transação sem valor")
    void testTransaction_WhenAmountIsMissing_ShouldThrowException() {
        //given
        TransactionType type = TransactionType.DEPOSIT;
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                null,
                type, null, null,  1L, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        ));
    }

    @Test
    @DisplayName("deve rejeitar transação com valor<=0")
    void testTransaction_WhenAmountIsNotPositive_ShouldThrowException() {
        //given
        TransactionType type = TransactionType.DEPOSIT;
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                null,
                type, BigDecimal.ZERO, null, 1L, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        ));
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                null,
                type, new BigDecimal("-5.00"), null, 1L, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        ));
    }

    @Test
    @DisplayName("deve rejeitar transação sem usuário de origem")
    void testTransaction_WhenOriginIsMissing_ShouldThrowException() {
        //given
        TransactionType type = TransactionType.DEPOSIT;
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                null,
                type, amount, null, null, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        ));
    }

    @Test
    @DisplayName("deve rejeitar transferência sem usuário de destino")
    void testTransaction_WhenDestinationIsMissing_ShouldThrowException() {
        //given
        Long originId = 1L;
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, ()-> new Transaction(
                null,
                TransactionType.TRANSFER,
                amount,
                null,
                originId,
                null,
                TransactionStatus.APPROVED,
                null,
                null, "Descrição de teste", CREATION_DATE
        ));
    }

    @Test
    @DisplayName("rejeita transferência com ID de destino não positivo")
    void testTransfer_WhenDestinationIdIsNotPositive_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");

        //when then
        for (Long destinationId : new Long[]{0L, -1L}) {
            assertThrows(InvalidTransactionException.class, () -> new Transaction(
                    null, TransactionType.TRANSFER, amount, null,
                    1L, destinationId, TransactionStatus.APPROVED,
                    null, null, "Transferência de teste", CREATION_DATE
            ));
        }
    }

    @Test
    @DisplayName("rejeita destinatário em tipos diferentes de transferência")
    void testTransaction_WhenNonTransferHasDestination_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");

        //when then
        for (TransactionType type : new TransactionType[]{
                TransactionType.DEPOSIT, TransactionType.WITHDRAW, TransactionType.PURCHASE
        }) {
            assertThrows(InvalidTransactionException.class, () -> new Transaction(
                    null, type, amount,
                    type == TransactionType.PURCHASE ? "Alimentação" : null,
                    1L, 2L, TransactionStatus.APPROVED, null, null,
                    "Transação de teste", CREATION_DATE
            ));
        }
    }

    @Test
    @DisplayName("rejeita transação com ID de origem não positivo")
    void testTransaction_WhenOriginIdIsNotPositive_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");

        //when then
        for (Long originId : new Long[]{0L, -1L}) {
            assertThrows(InvalidTransactionException.class, () -> new Transaction(
                    null, TransactionType.PURCHASE, amount, "Alimentação",
                    originId, null, TransactionStatus.APPROVED,
                    null, null, "Compra de teste", CREATION_DATE
            ));
        }
    }

    @Test
    @DisplayName("deve guardar origem e destino da transferencia")
    void testTransfer_WhenUsersAreValid_ShouldKeepIds() {
        //given
        Long originId = 1L;
        Long destId = 2L;
        BigDecimal amount = new BigDecimal("25.00");
        //when
        Transaction transaction = new Transaction(
                null,
                TransactionType.TRANSFER, amount,
                null, originId, destId,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        );
        //then
        assertEquals(originId, transaction.getOriginUserId());
        assertEquals(destId, transaction.getDestinationUserId());
    }

    @Test
    @DisplayName("deve criar depósito aprovado")
    void testDeposit_WhenDataIsValid_ShouldCreateTransaction() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when
        Transaction transaction = new Transaction(
                null,
                TransactionType.DEPOSIT, amount, null,
                1L, null, TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        );
        //then
        assertEquals(TransactionType.DEPOSIT, transaction.getType());
        assertEquals("Depósito", transaction.getCategory());
        assertEquals(amount, transaction.getAmount());
        assertEquals(1L, transaction.getOriginUserId());
        assertNull(transaction.getDestinationUserId());
        assertEquals(TransactionStatus.APPROVED, transaction.getStatus());
        assertNull(transaction.getId());
        assertEquals("Descrição de teste", transaction.getDescription());
        assertEquals(CREATION_DATE, transaction.getCreationDate());
    }

    @Test
    @DisplayName("usa categoria padrão para depósito com categoria vazia ou em branco")
    void testDeposit_WhenCategoryIsBlank_ShouldUseDefaultCategory() {
        //given
        BigDecimal amount = new BigDecimal("25.00");

        for (String category : new String[]{"", "   "}) {
            //when
            Transaction transaction = new Transaction(
                    null, TransactionType.DEPOSIT, amount, category,
                    1L, null, TransactionStatus.APPROVED, null, null,
                    "Depósito de teste", CREATION_DATE
            );

            //then
            assertEquals("Depósito", transaction.getCategory());
        }
    }

    @Test
    @DisplayName("cria compra com categoria")
    void testPurchase_WhenCategoryIsValid_ShouldCreateTransaction() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when
        Transaction transaction = new Transaction(
                null,
                TransactionType.PURCHASE, amount,
                "Alimentação", 1L, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        );
        //then
        assertEquals("Alimentação",  transaction.getCategory());
    }

    @Test
    @DisplayName("usa categoria padrão para retirada sem categoria")
    void testWithdrawal_WhenCategoryIsMissingOrBlank_ShouldUseDefaultCategory() {
        //given
        BigDecimal amount = new BigDecimal("25.00");

        for (String category : new String[]{"", "   "}) {
            //when
            Transaction transaction = new Transaction(
                    null, TransactionType.WITHDRAW, amount, category,
                    1L, null, TransactionStatus.APPROVED, null, null,
                    "Retirada de teste", CREATION_DATE
            );
            //then
            assertEquals("Retirada", transaction.getCategory());
        }

        //when
        Transaction withoutCategory = new Transaction(
                null, TransactionType.WITHDRAW, amount, null,
                1L, null, TransactionStatus.REJECTED, "Saldo insuficiente", null,
                "Retirada de teste", CREATION_DATE
        );
        //then
        assertEquals("Retirada", withoutCategory.getCategory());
    }

    @Test
    @DisplayName("mantém categoria informada para depósito e retirada")
    void testDepositAndWithdrawal_WhenCategoryIsProvided_ShouldKeepCategory() {
        //given
        String category = "Ajuste";

        for (TransactionType type : new TransactionType[]{TransactionType.DEPOSIT, TransactionType.WITHDRAW}) {
            //when
            Transaction transaction = new Transaction(
                    null, type, new BigDecimal("25.00"), category,
                    1L, null, TransactionStatus.APPROVED, null, null,
                    "Lançamento de teste", CREATION_DATE
            );
            //then
            assertEquals(category, transaction.getCategory());
        }
    }

    @Test
    @DisplayName("não aplica categoria padrão ao reconstruir transação persistida sem categoria")
    void testDepositAndWithdrawal_WhenPersistedWithoutCategory_ShouldKeepNullCategory() {
        //given
        BigDecimal amount = new BigDecimal("25.00");

        for (TransactionType type : new TransactionType[]{TransactionType.DEPOSIT, TransactionType.WITHDRAW}) {
            //when
            Transaction persisted = new Transaction(
                    10L, type, amount, null,
                    1L, null, TransactionStatus.APPROVED, null, null,
                    "Registro antigo do banco", CREATION_DATE
            );
            //then
            assertNull(persisted.getCategory());
        }
    }

    @Test
    @DisplayName("rejeita compra com categoria vazia ou em branco")
    void testPurchase_WhenCategoryIsBlank_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                null,
                TransactionType.PURCHASE, amount,
                "", 1L, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
        ));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                null,
                TransactionType.PURCHASE, amount,
                "   ", 1L, null,
                TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
                )
        );
    }

    @Test
    @DisplayName("rejeita transferência para o próprio usuário")
    void testTransfer_WhenOriginEqualsDestination_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                null,
                TransactionType.TRANSFER, amount, null,
                1L, 1L, TransactionStatus.APPROVED, null, null, "Descrição de teste", CREATION_DATE
                )
        );
    }

    @Test
    @DisplayName("exige motivo para a transação rejeitada")
    void testRejectedTransaction_WhenReasonIsMissing_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                null,
                TransactionType.WITHDRAW, amount, null,
                1L, null, TransactionStatus.REJECTED, null, null, "Descrição de teste", CREATION_DATE
                )
        );
    }

    @Test
    @DisplayName("deve guardar o motivo da rejeição")
    void testRejectedTransaction_WhenReasonIsValid_ShouldKeepReason() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when
        Transaction transaction = new Transaction(
                null,
                TransactionType.WITHDRAW, amount, null,
                1L, null, TransactionStatus.REJECTED,
                "Saldo insuficiente", null, "Descrição de teste", CREATION_DATE
        );
        //then
        assertEquals(TransactionStatus.REJECTED, transaction.getStatus());
        assertEquals("Saldo insuficiente", transaction.getRejectionReason());
    }

    @Test
    @DisplayName("rejeita o candidato preservando os dados da transferência")
    void testTransaction_WhenRejected_ShouldPreserveCandidateData() {
        // given
        Transaction candidate = new Transaction(
                null, TransactionType.TRANSFER, new BigDecimal("25.00"), "Ajuste",
                1L, 2L, TransactionStatus.APPROVED, null, 10L,
                "Transferência de teste", CREATION_DATE
        );

        // when
        Transaction rejected = candidate.reject("Usuário de destino não encontrado");

        // then
        assertNotSame(candidate, rejected);
        assertEquals(TransactionStatus.APPROVED, candidate.getStatus());
        assertNull(candidate.getRejectionReason());
        assertEquals(TransactionStatus.REJECTED, rejected.getStatus());
        assertEquals("Usuário de destino não encontrado", rejected.getRejectionReason());
        assertEquals(candidate.getId(), rejected.getId());
        assertEquals(candidate.getType(), rejected.getType());
        assertEquals(candidate.getAmount(), rejected.getAmount());
        assertEquals(candidate.getCategory(), rejected.getCategory());
        assertEquals(candidate.getOriginUserId(), rejected.getOriginUserId());
        assertEquals(candidate.getDestinationUserId(), rejected.getDestinationUserId());
        assertEquals(candidate.getOriginalTransactionId(), rejected.getOriginalTransactionId());
        assertEquals(candidate.getDescription(), rejected.getDescription());
        assertEquals(candidate.getCreationDate(), rejected.getCreationDate());
    }

    @Test
    @DisplayName("exige um motivo válido ao rejeitar o candidato")
    void testTransaction_WhenRejectionReasonIsBlank_ShouldThrowException() {
        // given
        Transaction candidate = createDeposit(
                new BigDecimal("25.00"), "Depósito de teste", CREATION_DATE);

        // when then
        assertThrows(InvalidTransactionException.class, () -> candidate.reject(null));
        assertThrows(InvalidTransactionException.class, () -> candidate.reject("   "));
    }

    @Test
    @DisplayName("vincula correção a transação original")
    void testCorrection_WhenOriginalIdIsProvided_ShouldKeepId() {
        //given
        Long originalId = 10L;
        BigDecimal amount = new BigDecimal("25.00");
        //when
        Transaction correction = new Transaction(
                null,
                TransactionType.DEPOSIT, amount, null,
                1L, null, TransactionStatus.APPROVED, null, originalId, "Descrição de teste", CREATION_DATE
        );

        //then
        assertEquals(originalId, correction.getOriginalTransactionId());
    }

    @Test
    @DisplayName("deve rejeitar transação sem status")
    void testTransaction_WhenStatusIsMissing_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                null,
                TransactionType.DEPOSIT,
                amount, null, 1L, null,
                null, null, null, "Descrição de teste", CREATION_DATE
        ));
    }

    @Test
    @DisplayName("deve exigir motivo não vazio para transação rejeitada")
    void testRejectedTransaction_WhenReasonIsBlank_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                null,
                TransactionType.WITHDRAW, amount, null, 1L,
                null, TransactionStatus.REJECTED, "   ", null, "Descrição de teste", CREATION_DATE
        ));
    }

    @Test
    @DisplayName("rejeita transação sem descrição ou com descrição em branco")
    void testTransaction_WhenDescriptionIsMissingOrBlank_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("10.00");

        //when
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(amount, null, CREATION_DATE));
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(amount, "   ", CREATION_DATE));

        //then
        assertEquals("Descrição da transação é obrigatória", missing.getMessage());
        assertEquals("Descrição da transação é obrigatória", blank.getMessage());
    }

    @Test
    @DisplayName("rejeita transação sem data de criação")
    void testTransaction_WhenCreationDateIsMissing_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("10.00");

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(amount, "Depósito de teste", null));

        //then
        assertEquals("Data da transação é obrigatória", exception.getMessage());
    }

    @Test
    @DisplayName("rejeita valor com mais de duas casas decimais")
    void testTransaction_WhenAmountHasExcessDecimalPlaces_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("10.123");

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(amount, "Depósito de teste", CREATION_DATE));

        //then
        assertEquals("Valor da transação deve ter no máximo duas casas decimais", exception.getMessage());
    }

    @Test
    @DisplayName("rejeita valor maior que a precisão da coluna")
    void testTransaction_WhenAmountExceedsPrecision_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("100000000000000000.00");

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(amount, "Depósito de teste", CREATION_DATE));

        //then
        assertEquals("Valor da transação excede o limite permitido", exception.getMessage());
    }

    @Test
    @DisplayName("aceita zeros excedentes no fim e mantém o valor com duas casas")
    void testTransaction_WhenAmountHasTrailingZero_ShouldCreateTransaction() {
        //given
        for (String value : new String[]{"10.120", "10.1200", "10.000", "1000.000",
                "99999999999999999.9900"}) {
            BigDecimal amount = new BigDecimal(value);

            //when
            Transaction transaction = createDeposit(amount, "Depósito de teste", CREATION_DATE);

            //then
            assertEquals(amount.setScale(2), transaction.getAmount());
        }
    }

    @Test
    @DisplayName("aceita descrição no limite de 255 caracteres")
    void testTransaction_WhenDescriptionIsAtMaximumLength_ShouldKeepDescription() {
        //given
        String description = "a".repeat(255);

        //when
        Transaction transaction = createDeposit(new BigDecimal("10.00"), description, CREATION_DATE);

        //then
        assertEquals(description, transaction.getDescription());
    }

    @Test
    @DisplayName("rejeita descrição acima de 255 caracteres")
    void testTransaction_WhenDescriptionExceedsMaximumLength_ShouldThrowException() {
        //given
        String description = "a".repeat(256);

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(new BigDecimal("10.00"), description, CREATION_DATE));

        //then
        assertEquals("Descrição deve ter no máximo 255 caracteres", exception.getMessage());
    }

    @Test
    @DisplayName("aceita categoria no limite de 255 caracteres")
    void testPurchase_WhenCategoryIsAtMaximumLength_ShouldKeepCategory() {
        //given
        String category = "a".repeat(255);

        //when
        Transaction transaction = new Transaction(
                null, TransactionType.PURCHASE, new BigDecimal("10.00"), category,
                1L, null, TransactionStatus.APPROVED, null, null,
                "Compra de teste", CREATION_DATE
        );

        //then
        assertEquals(category, transaction.getCategory());
    }

    @Test
    @DisplayName("rejeita categoria acima de 255 caracteres")
    void testPurchase_WhenCategoryExceedsMaximumLength_ShouldThrowException() {
        //given
        String category = "a".repeat(256);

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Transaction(
                        null, TransactionType.PURCHASE, new BigDecimal("10.00"), category,
                        1L, null, TransactionStatus.APPROVED, null, null,
                        "Compra de teste", CREATION_DATE
                ));

        //then
        assertEquals("Categoria deve ter no máximo 255 caracteres", exception.getMessage());
    }

    @Test
    @DisplayName("aceita motivo de rejeição no limite de 255 caracteres")
    void testRejectedTransaction_WhenReasonIsAtMaximumLength_ShouldKeepReason() {
        //given
        String reason = "a".repeat(255);

        //when
        Transaction transaction = new Transaction(
                null, TransactionType.WITHDRAW, new BigDecimal("10.00"), null,
                1L, null, TransactionStatus.REJECTED, reason, null,
                "Retirada de teste", CREATION_DATE
        );

        //then
        assertEquals(reason, transaction.getRejectionReason());
    }

    @Test
    @DisplayName("rejeita motivo de rejeição acima de 255 caracteres")
    void testRejectedTransaction_WhenReasonExceedsMaximumLength_ShouldThrowException() {
        //given
        String reason = "a".repeat(256);

        //when
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Transaction(
                        null, TransactionType.WITHDRAW, new BigDecimal("10.00"), null,
                        1L, null, TransactionStatus.REJECTED, reason, null,
                        "Retirada de teste", CREATION_DATE
                ));

        //then
        assertEquals("Motivo da rejeição deve ter no máximo 255 caracteres", exception.getMessage());
    }

    private Transaction createDeposit(BigDecimal amount, String description, Instant creationDate) {
        return new Transaction(
                null, TransactionType.DEPOSIT, amount, null,
                1L, null, TransactionStatus.APPROVED, null, null,
                description, creationDate
        );
    }
}
