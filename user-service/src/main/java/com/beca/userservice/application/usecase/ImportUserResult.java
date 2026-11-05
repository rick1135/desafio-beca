package com.beca.userservice.application.usecase;

public record ImportUserResult(
        int registro,
        String status,
        Long userId,
        String reason
) {}