package com.beca.userservice.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest (
        @Schema(description = "Nome completo do usuário", example = "João da Silva")
        @NotBlank
        @Size(max = 100)
        String name,

        @Schema(description = "CPF com 11 dígitos numéricos", example = "12345678901")
        @NotBlank
        @Pattern(regexp = "\\d{11}", message = "CPF deve conter 11 dígitos")
        String cpf,

        @Schema(description = "E-mail de acesso", example = "joao.silva@email.com")
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Schema(description = "Senha de acesso", example = "Senha@123")
        @NotBlank
        @Size(max = 255)
        String password
) { }
