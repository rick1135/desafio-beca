package com.beca.transactions.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public record ApiErrorResponse(
        @Schema(description = "Código de status HTTP", example = "400")
        int status,

        @Schema(description = "Nome do erro HTTP", example = "Bad Request")
        String error,

        @Schema(description = "Mensagem explicativa do erro", example = "Saldo insuficiente")
        String message,

        @Schema(description = "Detalhes de campos específicos", example = "{\"amount\": \"Valor deve ser maior que zero\"}")
        Map<String, String> details
) {
}
