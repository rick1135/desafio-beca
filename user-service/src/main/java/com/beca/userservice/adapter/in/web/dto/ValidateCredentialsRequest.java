package com.beca.userservice.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ValidateCredentialsRequest(
        @NotBlank @Email String email,
        @NotBlank String senha
) {
}
