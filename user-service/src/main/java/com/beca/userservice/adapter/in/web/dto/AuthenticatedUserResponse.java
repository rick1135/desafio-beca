package com.beca.userservice.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthenticatedUserResponse(
        @Schema(description = "Identificador único do usuário", example = "1")
        Long id,

        @Schema(description = "Nome do usuário", example = "João da Silva")
        String nome,

        @Schema(description = "E-mail do usuário", example = "joao.silva@email.com")
        String email
) {
}
