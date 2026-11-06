package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.domain.model.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull(message = "Tipo é obrigatório")
        TransactionType type,

        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.00", inclusive = false,
                message = "Valor deve ser maior que zero")
        @Digits(integer = 17, fraction = 2,
                message = "Valor deve ter até 17 dígitos inteiros e duas casas decimais")
        BigDecimal amount,

        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres")
        String description,

        @Size(max = 255, message = "Categoria deve ter no máximo 255 caracteres")
        String category,

        @Positive(message = "ID do usuário de destino deve ser maior que zero")
        Long destinationUserId,

        @Positive(message = "ID da transação original deve ser maior que zero")
        Long originalTransactionId
) {
    @JsonIgnore
    @AssertTrue(message = "Categoria é obrigatório para compras")
    public boolean isCategoryValid(){
        return type!= TransactionType.PURCHASE || (category != null && !category.isBlank());
    }

    @JsonIgnore
    @AssertTrue(message = "Usuário de destino é obrigatório para transferências")
    public boolean isDestinationValid(){
        return type!= TransactionType.TRANSFER || destinationUserId!=null;
    }
}
