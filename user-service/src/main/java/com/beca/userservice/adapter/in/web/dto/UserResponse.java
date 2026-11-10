package com.beca.userservice.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
        @Schema(description = "Identificador único do usuário", example = "1")
        Long id,

        @Schema(description = "Nome do usuário", example = "João da Silva")
        String name,

        @Schema(description = "E-mail cadastrado", example = "joao.silva@email.com")
        String email
) {
}
