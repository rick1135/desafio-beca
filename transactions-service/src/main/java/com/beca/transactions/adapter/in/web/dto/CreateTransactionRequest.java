package com.beca.transactions.adapter.in.web.dto;

import com.beca.transactions.domain.model.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @Schema(description = "Tipo da transação: DEPOSIT, WITHDRAW, TRANSFER ou PURCHASE", example = "PURCHASE")
        @NotNull(message = "Tipo é obrigatório")
        TransactionType type,

        @Schema(description = "Valor monetário da transação", example = "150.00")
        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.00", inclusive = false,
                message = "Valor deve ser maior que zero")
        @Digits(integer = 17, fraction = 2,
                message = "Valor deve ter até 17 dígitos inteiros e duas casas decimais")
        BigDecimal amount,

        @Schema(description = "Descrição sucinta da transação", example = "Almoço de negócios")
        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 255, message = "Descrição deve ter no máximo 255 caracteres")
        String description,

        @Schema(description = "Categoria da despesa (obrigatória quando o tipo for PURCHASE)", example = "ALIMENTACAO")
        @Size(max = 255, message = "Categoria deve ter no máximo 255 caracteres")
        String category,

        @Schema(description = "ID do usuário destinatário (obrigatório quando o tipo for TRANSFER)", example = "2")
        @Positive(message = "ID do usuário de destino deve ser maior que zero")
        Long destinationUserId,

        @Schema(description = "ID da transação original aprovada a ser corrigida", example = "10")
        @Positive(message = "ID da transação original deve ser maior que zero")
        Long originalTransactionId,

        @Schema(description = "Motivo da correção (obrigatório se originalTransactionId informado)", example = "Valor digitado incorretamente")
        @Size(max = 255, message = "Motivo da correção deve ter no máximo 255 caracteres")
        String correctionReason
) {
    public CreateTransactionRequest {
        if (amount != null) {
            amount = amount.stripTrailingZeros();
        }
    }

    public CreateTransactionRequest(
            TransactionType type, BigDecimal amount, String description, String category,
            Long destinationUserId, Long originalTransactionId
    ) {
        this(type, amount, description, category, destinationUserId, originalTransactionId, null);
    }

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

    @JsonIgnore
    @AssertTrue(message = "Usuário de destino é permitido apenas para transferências")
    public boolean isDestinationOnlyForTransfer() {
        return destinationUserId == null || type == TransactionType.TRANSFER;
    }

    @JsonIgnore
    @AssertTrue(message = "Motivo da correção é obrigatório")
    public boolean isCorrectionReasonValid() {
        return originalTransactionId == null || (correctionReason != null && !correctionReason.isBlank());
    }

    @JsonIgnore
    @AssertTrue(message = "ID da transação original é obrigatório quando há motivo da correção")
    public boolean isCorrectionReasonOnlyForCorrection() {
        return correctionReason == null || originalTransactionId != null;
    }
}
