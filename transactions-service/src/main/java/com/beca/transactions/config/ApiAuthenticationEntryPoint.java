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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(ApiAuthenticationEntryPoint.class);
    private final JsonMapper jsonMapper;

    public ApiAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException{
        boolean serviceUnavailable = exception instanceof AuthenticationServiceException;
        if (serviceUnavailable) {
            log.warn("Falha ao autenticar no MS1", exception);
        }

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
