package com.beca.transactions.config;

import com.beca.transactions.adapter.in.web.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.security.core.AuthenticationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final JsonMapper jsonMapper;

    public ApiAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException{
        boolean serviceUnavailable = exception instanceof AuthenticationServiceException;

        HttpStatus status = serviceUnavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED;

        String message = serviceUnavailable ? "Serviço de autenticação está indisponível no momento" : "Credenciais inválidas";

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        if(!serviceUnavailable){
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"transactions-service\"");
        }

        jsonMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                status.value(), status.getReasonPhrase(), message, Map.of()
        ));
    }
}
