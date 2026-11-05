package com.beca.userservice.adapter.in.web.dto;

public record UploadLineResult(
        int registro,
        String status,
        Long usuarioId,
        String motivo
) {
}
