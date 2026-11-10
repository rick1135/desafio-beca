package com.beca.userservice.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UploadLineResult(
        @Schema(description = "Número da linha no arquivo CSV", example = "1")
        int registro,

        @Schema(description = "Resultado do processamento da linha: SUCESSO ou ERRO", example = "SUCESSO")
        String status,

        @Schema(description = "ID do usuário gerado quando cadastrado com sucesso", example = "10")
        Long usuarioId,

        @Schema(description = "Motivo da falha caso a linha tenha sido rejeitada", example = "E-mail já cadastrado")
        String motivo
) {
}
