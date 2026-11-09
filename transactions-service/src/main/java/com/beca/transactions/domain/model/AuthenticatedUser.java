package com.beca.transactions.domain.model;

public record AuthenticatedUser(
        Long id,
        String nome,
        String email
) {
}
