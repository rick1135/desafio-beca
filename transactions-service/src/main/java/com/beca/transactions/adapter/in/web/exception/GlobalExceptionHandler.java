package com.beca.transactions.adapter.in.web.exception;

import com.beca.transactions.adapter.in.web.dto.ApiErrorResponse;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.application.exception.AccountBalanceNotFoundException;
import com.beca.transactions.domain.model.TransactionType;
import tools.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransaction(
            InvalidTransactionException ex
    ) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex){
        Map<String, String> details = new LinkedHashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String field = switch (error.getField()) {
                case "categoryValid" -> "category";
                case "destinationValid" -> "destinationUserId";
                case "destinationOnlyForTransfer" -> "destinationUserId";
                default -> error.getField();
            };
            details.putIfAbsent(field, error.getDefaultMessage());
        });

        ex.getBindingResult().getGlobalErrors().forEach(error ->
                details.putIfAbsent("_global", error.getDefaultMessage())
        );

        return response(
                HttpStatus.BAD_REQUEST,
                "Um ou mais campos inválidos",
                details
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex
    ) {
        Throwable cause = ex.getCause();

        while (cause != null) {
            if (cause instanceof InvalidFormatException invalid
                    && invalid.getTargetType() == TransactionType.class) {
                String field = invalid.getPath().stream()
                        .map(reference -> reference.getPropertyName())
                        .filter(name -> name != null)
                        .findFirst()
                        .orElse("type");

                return response(
                        HttpStatus.BAD_REQUEST,
                        "Tipo de transação inválido.",
                        Map.of(field, "Valores aceitos: "
                                + Arrays.toString(TransactionType.values()))
                );
            }
            cause = cause.getCause();
        }

        return response(
                HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido ou malformado.",
                Map.of()
        );
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            TransactionNotFoundException ex
    ) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(AccountBalanceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountBalanceNotFound(
            AccountBalanceNotFoundException ex
    ) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleExternalService(
            ExternalServiceUnavailableException ex
    ) {
        log.warn("Falha em integração externa", ex);
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Serviço externo indisponível no momento.",
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "Parâmetro inválido.",
                Map.of(ex.getName(), "Valor incompatível com o tipo esperado")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse springError) {
            String detail = springError.getBody().getDetail();
            String message = detail == null || detail.isBlank()
                    ? "Erro na requisição."
                    : detail;

            var status = springError.getStatusCode();
            var knownStatus = HttpStatus.resolve(status.value());
            String error = knownStatus != null
                    ? knownStatus.getReasonPhrase()
                    : "HTTP " + status.value();

            return ResponseEntity.status(status)
                    .headers(springError.getHeaders())
                    .body(new ApiErrorResponse(
                            status.value(),
                            error,
                            message,
                            Map.of()
                    ));
        }
        log.error("Erro inesperado ao processar requisição", ex);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno ao processar a requisição.",
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String message,
            Map<String, String> details
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                details
        ));
    }
}
