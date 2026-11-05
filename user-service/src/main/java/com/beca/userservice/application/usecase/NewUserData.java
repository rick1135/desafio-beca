package com.beca.userservice.application.usecase;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NewUserData(
        @NotBlank @Size(max = 100) String name,
        @NotBlank
        @Pattern(regexp = "\\d{11}", message = "CPF deve ter 11 dígitos")
        String cpf,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String password
) {
}
