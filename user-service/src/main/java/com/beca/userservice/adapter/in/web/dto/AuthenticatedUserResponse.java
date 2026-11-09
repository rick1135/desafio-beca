package com.beca.userservice.adapter.in.web.dto;

public record AuthenticatedUserResponse(
        Long id,
        String nome,
        String email
) {
}
