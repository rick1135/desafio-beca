package com.beca.userservice.adapter.in.web.dto;

import java.util.Map;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> details
) {
}
