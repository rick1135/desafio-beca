package com.beca.userservice.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ValidateCredentialsRequest(
        @Schema(description = "E-mail do usuário cadastrado", example = "joao.silva@email.com")
        @NotBlank @Email String email,

        @Schema(description = "Senha do usuário", example = "Senha@123")
        @NotBlank String senha
) {
}
