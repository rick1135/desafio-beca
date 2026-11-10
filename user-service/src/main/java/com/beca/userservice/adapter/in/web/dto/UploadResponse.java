package com.beca.userservice.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record UploadResponse(
        @Schema(description = "Total de registros processados", example = "10")
        int total,

        @Schema(description = "Quantidade de usuários importados com sucesso", example = "8")
        int importados,

        @Schema(description = "Quantidade de linhas que falharam", example = "2")
        int falhas,

        @Schema(description = "Detalhamento linha a linha do processamento")
        List<UploadLineResult> resultados
) {}
