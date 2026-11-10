package com.beca.userservice.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Schema(description = "Nome atualizado do usuário", example = "João da Silva Sauro")
        @NotBlank
        @Size(max = 100)
        String name,

        @Schema(description = "E-mail atualizado do usuário", example = "joao.sauro@email.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email
) {
}
