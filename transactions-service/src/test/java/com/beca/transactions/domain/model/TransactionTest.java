package com.beca.transactions.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionTest {
    @Test
    @DisplayName("deve rejeitar compra sem categoria")
    void testPurchase_WhenCategoryIsMissing_ShouldThrowException() {
        //given
        TransactionType type =  TransactionType.PURCHASE;
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                type, amount, null, 1L, null,
                TransactionStatus.APPROVED, null, null
        ));
    }

    @Test
    @DisplayName("deve rejeitar transação sem tipo")
    void testTransaction_WhenTypeIsMissing_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                null, amount, null,  1L, null,
                TransactionStatus.APPROVED, null, null
        ));
    }

    @Test
    @DisplayName("deve rejeitar transação sem valor")
    void testTransaction_WhenAmountIsMissing_ShouldThrowException() {
        //given
        TransactionType type = TransactionType.DEPOSIT;
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                type, null, null,  1L, null,
                TransactionStatus.APPROVED, null, null
        ));
    }

    @Test
    @DisplayName("deve rejeitar transação com valor<=0")
    void testTransaction_WhenAmountIsNotPositive_ShouldThrowException() {
        //given
        TransactionType type = TransactionType.DEPOSIT;
        //when then
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                type, BigDecimal.ZERO, null, 1L, null,
                TransactionStatus.APPROVED, null, null
        ));
        assertThrows(IllegalArgumentException.class, ()->new Transaction(
                type, new BigDecimal("-5.00"), null, 1L, null,
                TransactionStatus.APPROVED, null, null
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
                type, amount, null, null, null,
                TransactionStatus.APPROVED, null, null
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
                TransactionType.TRANSFER,
                amount,
                null,
                originId,
                null,
                TransactionStatus.APPROVED,
                null,
                null
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
                TransactionType.TRANSFER, amount,
                null, originId, destId,
                TransactionStatus.APPROVED, null, null
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
                TransactionType.DEPOSIT, amount, null,
                1L, null, TransactionStatus.APPROVED, null, null
        );
        //then
        assertEquals(TransactionType.DEPOSIT, transaction.getType());
        assertEquals(amount, transaction.getAmount());
        assertEquals(1L, transaction.getOriginUserId());
        assertNull(transaction.getDestinationUserId());
        assertEquals(TransactionStatus.APPROVED, transaction.getStatus());
    }

    @Test
    @DisplayName("cria compra com categoria")
    void testPurchase_WhenCategoryIsValid_ShouldCreateTransaction() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when
        Transaction transaction = new Transaction(
                TransactionType.PURCHASE, amount,
                "Alimentação", 1L, null,
                TransactionStatus.APPROVED, null, null
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
                TransactionType.PURCHASE, amount,
                "", 1L, null,
                TransactionStatus.APPROVED, null, null
        ));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                TransactionType.PURCHASE, amount,
                "   ", 1L, null,
                TransactionStatus.APPROVED, null, null
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
                TransactionType.TRANSFER, amount, null,
                1L, 1L, TransactionStatus.APPROVED, null, null
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
                TransactionType.WITHDRAW, amount, null,
                1L, null, TransactionStatus.REJECTED, null, null
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
                TransactionType.WITHDRAW, amount, null,
                1L, null, TransactionStatus.REJECTED,
                "Saldo insuficiente", null
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
                TransactionType.DEPOSIT, amount, null,
                1L, null, TransactionStatus.APPROVED, null, originalId
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
                TransactionType.DEPOSIT,
                amount, null, 1L, null,
                null, null, null
        ));
    }

    @Test
    @DisplayName("deve exigir motivo não vazio para transação rejeitada")
    void testRejectedTransaction_WhenReasonIsBlank_ShouldThrowException() {
        //given
        BigDecimal amount = new BigDecimal("25.00");
        //when then
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                TransactionType.WITHDRAW, amount, null, 1L,
                null, TransactionStatus.REJECTED, "   ", null
        ));
    }
}
