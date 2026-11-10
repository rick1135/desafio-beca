package com.beca.userservice.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public record ApiErrorResponse(
        @Schema(description = "Código de status HTTP", example = "400")
        int status,

        @Schema(description = "Nome do erro HTTP", example = "Bad Request")
        String error,

        @Schema(description = "Mensagem descritiva do erro", example = "Dados inválidos.")
        String message,

        @Schema(description = "Detalhes de campos específicos", example = "{\"email\": \"deve ser um endereço de e-mail bem formado\"}")
        Map<String, String> details
) {
}
