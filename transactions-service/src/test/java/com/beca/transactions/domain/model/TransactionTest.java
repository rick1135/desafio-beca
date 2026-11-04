package com.beca.transactions.domain.model;

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
        assertEquals(amount, transaction.getAmount());
        assertEquals(1L, transaction.getOriginUserId());
        assertNull(transaction.getDestinationUserId());
        assertEquals(TransactionStatus.APPROVED, transaction.getStatus());
        assertNull(transaction.getId());
        assertEquals("Descrição de teste", transaction.getDescription());
        assertEquals(CREATION_DATE, transaction.getCreationDate());
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
    void testTransaction_WhenDescriptionIsMissing_ShouldThrowException() {
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(new BigDecimal("10.00"), null, CREATION_DATE));
        assertEquals("Descrição da transação é obrigatória", missing.getMessage());

        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(new BigDecimal("10.00"), "   ", CREATION_DATE));
        assertEquals("Descrição da transação é obrigatória", blank.getMessage());
    }

    @Test
    @DisplayName("rejeita transação sem data de criação")
    void testTransaction_WhenCreationDateIsMissing_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(new BigDecimal("10.00"), "Depósito de teste", null));
        assertEquals("Data da transação é obrigatória", exception.getMessage());
    }

    @Test
    @DisplayName("rejeita valor com mais de duas casas decimais")
    void testTransaction_WhenAmountHasExcessDecimalPlaces_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(new BigDecimal("10.123"), "Depósito de teste", CREATION_DATE));
        assertEquals("Valor da transação deve ter no máximo duas casas decimais", exception.getMessage());
    }

    @Test
    @DisplayName("rejeita valor maior que a precisão da coluna")
    void testTransaction_WhenAmountExceedsPrecision_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createDeposit(new BigDecimal("100000000000000000.00"), "Depósito de teste", CREATION_DATE));
        assertEquals("Valor da transação excede o limite permitido", exception.getMessage());
    }

    @Test
    @DisplayName("aceita zero excedente após a segunda casa decimal sem arredondar")
    void testTransaction_WhenAmountHasTrailingZero_ShouldPreserveAmount() {
        BigDecimal amount = new BigDecimal("10.120");

        Transaction transaction = createDeposit(amount, "Depósito de teste", CREATION_DATE);

        assertEquals(amount, transaction.getAmount());
        assertEquals(3, transaction.getAmount().scale());
    }

    private Transaction createDeposit(BigDecimal amount, String description, Instant creationDate) {
        return new Transaction(
                null, TransactionType.DEPOSIT, amount, null,
                1L, null, TransactionStatus.APPROVED, null, null,
                description, creationDate
        );
    }
}
