package com.beca.userservice.adapter.in.web.dto;

public record UserResponse(
        Long id,
        String name,
        String email
) {
}
