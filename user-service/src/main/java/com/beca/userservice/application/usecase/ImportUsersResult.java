package com.beca.userservice.application.usecase;

import java.util.List;

public record ImportUsersResult(
        int total,
        int importados,
        int falhas,
        List<ImportUserResult> resultados
) {}