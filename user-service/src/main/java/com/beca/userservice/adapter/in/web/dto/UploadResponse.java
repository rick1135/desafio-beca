package com.beca.userservice.adapter.in.web.dto;

import java.util.List;

public record UploadResponse(
        int total,
        int importados,
        int falhas,
        List<UploadLineResult> resultados
) {}
