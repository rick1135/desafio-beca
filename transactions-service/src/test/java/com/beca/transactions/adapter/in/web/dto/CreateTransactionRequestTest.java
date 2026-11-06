package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.domain.model.TransactionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class CreateTransactionRequestTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciarValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidador() {
        factory.close();
    }

    @Test
    void aceitaOsQuatroTiposComDadosValidos() {
        for (TransactionType type : TransactionType.values()) {
            CreateTransactionRequest request = new CreateTransactionRequest(
                    type,
                    new BigDecimal("25.50"),
                    "Transação de teste",
                    type == TransactionType.PURCHASE ? "Alimentação" : null,
                    type == TransactionType.TRANSFER ? 2L : null,
                    null
            );

            assertTrue(validator.validate(request).isEmpty(), type.toString());
        }
    }

    @Test
    void exigeCamposBasicos() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                null, null, " ", null, null, null
        );

        assertEquals(
                Set.of("type", "amount", "description"),
                camposInvalidos(request)
        );
    }

    @Test
    void exigeCategoriaNaCompraEDestinoNaTransferencia() {
        assertTrue(camposInvalidos(new CreateTransactionRequest(
                TransactionType.PURCHASE, new BigDecimal("10.00"),
                "Compra", null, null, null
        )).contains("categoryValid"));

        assertTrue(camposInvalidos(new CreateTransactionRequest(
                TransactionType.TRANSFER, new BigDecimal("10.00"),
                "Transferência", null, null, null
        )).contains("destinationValid"));
    }

    @Test
    void informaEmPortuguesQuandoIdsSaoInvalidos() {
        CreateTransactionRequest transfer = new CreateTransactionRequest(
                TransactionType.TRANSFER, new BigDecimal("10.00"),
                "Transferência", null, 0L, null
        );
        CreateTransactionRequest correction = new CreateTransactionRequest(
                TransactionType.DEPOSIT, new BigDecimal("10.00"),
                "Correção", null, null, 0L
        );

        assertEquals("ID do usuário de destino deve ser maior que zero",
                validator.validate(transfer).stream()
                        .filter(error -> error.getPropertyPath().toString().equals("destinationUserId"))
                        .findFirst().orElseThrow().getMessage());
        assertEquals("ID da transação original deve ser maior que zero",
                validator.validate(correction).stream()
                        .filter(error -> error.getPropertyPath().toString().equals("originalTransactionId"))
                        .findFirst().orElseThrow().getMessage());
    }

    @Test
    void rejeitaValoresInvalidos() {
        for (String amount : Set.of("0.00", "-1.00", "10.123", "10.1230",
                "100000000000000000.00")) {
            CreateTransactionRequest request = new CreateTransactionRequest(
                    TransactionType.DEPOSIT, new BigDecimal(amount),
                    "Depósito", null, null, null
            );

            assertTrue(camposInvalidos(request).contains("amount"), amount);
        }
    }

    @Test
    void aceitaZerosExcedentesNoFimDoValor() {
        for (String amount : Set.of("10.120", "10.1200", "10.000", "1000.000",
                "99999999999999999.9900")) {
            CreateTransactionRequest request = new CreateTransactionRequest(
                    TransactionType.DEPOSIT, new BigDecimal(amount),
                    "Depósito", null, null, null
            );

            assertTrue(validator.validate(request).isEmpty(), amount);
        }
    }

    @Test
    void rejeitaTextosAcimaDe255Caracteres() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                TransactionType.PURCHASE, new BigDecimal("10.00"),
                "d".repeat(256), "c".repeat(256), null, null
        );

        assertEquals(
                Set.of("description", "category"),
                camposInvalidos(request)
        );
    }

    private Set<String> camposInvalidos(CreateTransactionRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
