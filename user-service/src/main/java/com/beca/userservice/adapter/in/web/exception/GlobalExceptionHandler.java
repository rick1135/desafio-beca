package com.beca.userservice.adapter.in.web.exception;

import com.beca.userservice.adapter.in.web.dto.ApiErrorResponse;
import com.beca.userservice.application.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage(),
                        (first, duplicate) -> first
                ));

        ApiErrorResponse body = new ApiErrorResponse(
                400,
                "Bad Request",
                "Um ou mais campos inválidos.",
                details
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        ApiErrorResponse body = new ApiErrorResponse(
                404,
                "Not Found",
                ex.getMessage(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
