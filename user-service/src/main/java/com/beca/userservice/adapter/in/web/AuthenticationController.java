package com.beca.userservice.adapter.in.web;

import com.beca.userservice.adapter.in.web.dto.ApiErrorResponse;
import com.beca.userservice.adapter.in.web.dto.AuthenticatedUserResponse;
import com.beca.userservice.adapter.in.web.dto.ValidateCredentialsRequest;
import com.beca.userservice.application.usecase.AuthenticateUserService;
import com.beca.userservice.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação Interna", description = "Validação de credenciais entre microsserviços")
@RestController
@RequestMapping("/internal/auth")
public class AuthenticationController {
    private final AuthenticateUserService authenticateUserService;

    public AuthenticationController(AuthenticateUserService authenticateUserService) {
        this.authenticateUserService = authenticateUserService;
    }

    @Operation(
            summary = "Validar credenciais de usuário",
            description = "Endpoint interno utilizado pelo transactions-service (MS2) para autenticação HTTP Basic. Exige o cabeçalho X-Internal-Auth-Key.",
            security = @SecurityRequirement(name = OpenApiConfig.INTERNAL_AUTH_SCHEME)
    )
    @Parameter(
            name = "X-Internal-Auth-Key",
            in = ParameterIn.HEADER,
            description = "Chave secreta para comunicação interna entre microsserviços",
            required = true,
            schema = @Schema(type = "string", example = "chave-secreta-interna")
    )
    @ApiResponse(
            responseCode = "200",
            description = "Credenciais válidas",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AuthenticatedUserResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Dados de requisição inválidos",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "E-mail ou senha incorretos",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = "Chave interna ausente ou inválida",
            content = @Content
    )
    @PostMapping("/validate")
    public ResponseEntity<AuthenticatedUserResponse> validate(@Valid @RequestBody ValidateCredentialsRequest request) {
        var user = authenticateUserService.authenticate(request.email(), request.senha());
        return ResponseEntity.ok(new AuthenticatedUserResponse(user.getId(), user.getName(), user.getEmail()));
    }
}
