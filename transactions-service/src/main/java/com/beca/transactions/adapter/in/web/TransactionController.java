package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.ApiErrorResponse;
import com.beca.transactions.adapter.in.web.dto.CreateTransactionRequest;
import com.beca.transactions.adapter.in.web.dto.TransactionResponse;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.application.usecase.GetTransactionService;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.AuthenticatedUser;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Transações", description = "Operações de registro, consulta e correção de transações financeiras")
@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final CreateTransactionService createTransactionService;
    private final GetTransactionService getTransactionService;

    public TransactionController(CreateTransactionService createTransactionService,
                                 GetTransactionService getTransactionService) {
        this.createTransactionService = createTransactionService;
        this.getTransactionService = getTransactionService;
    }

    @Operation(
            summary = "Criar transação ou registrar correção",
            description = "Registra um depósito, despesa ou transferência entre contas. Permite também corrigir uma transação aprovada anterior informando originalTransactionId e correctionReason."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Transação registrada com sucesso",
            headers = @Header(
                    name = "Location",
                    description = "URI da transação criada",
                    schema = @Schema(type = "string", example = "/transactions/1")
            ),
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Dados de requisição inválidos, saldo insuficiente ou limites violados",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Credenciais HTTP Basic ausentes ou inválidas",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateTransactionRequest request
    ) {

        Transaction saved = createTransactionService.create(
                user.id(),
                request.type(),
                request.amount(),
                request.description(),
                request.category(),
                request.destinationUserId(),
                request.originalTransactionId(),
                request.correctionReason()
        );

        TransactionResponse response = TransactionResponse.from(saved);
        return ResponseEntity.created(URI.create("/transactions/" + saved.getId()))
                .body(response);
    }

    @Operation(
            summary = "Consultar transação por ID",
            description = "Recupera os detalhes de uma transação pertencente ao usuário autenticado."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Transação encontrada com sucesso",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "ID da transação inválido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = "Acesso proibido a transação de outro usuário",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Transação não encontrada",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(
            @Parameter(description = "Identificador único da transação", example = "1")
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (id <= 0) {
            throw new InvalidTransactionException("ID inválido");
        }
        return ResponseEntity.ok(TransactionResponse.from(getTransactionService.getById(id, user.id())));
    }

    @Operation(
            summary = "Consultar histórico de correções",
            description = "Retorna todas as tentativas e correções vinculadas a uma transação original."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Histórico de correções retornado",
            useReturnTypeSchema = true
    )
    @ApiResponse(
            responseCode = "400",
            description = "ID inválido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = "Acesso proibido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Transação original não encontrada",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @GetMapping("/{id}/corrections")
    public ResponseEntity<List<TransactionResponse>> getCorrections(
            @Parameter(description = "Identificador da transação original", example = "1")
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (id <= 0) {
            throw new InvalidTransactionException("ID inválido");
        }
        return ResponseEntity.ok(getTransactionService.getCorrections(id, user.id())
                .stream()
                .map(TransactionResponse::from)
                .toList());
    }

    @Operation(
            summary = "Listar transações",
            description = "Lista as transações do usuário autenticado com filtros opcionais por período e tipo."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Lista de transações retornada",
            useReturnTypeSchema = true
    )
    @ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Data inicial no formato ISO (YYYY-MM-DD)", example = "2026-11-01")
            @RequestParam(name="startDate", required = false)
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate startDate,
            @Parameter(description = "Data final no formato ISO (YYYY-MM-DD)", example = "2026-11-30")
            @RequestParam(name="endDate", required = false)
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate endDate,
            @Parameter(description = "Filtrar por tipo: DEPOSIT, EXPENSE ou TRANSFER", example = "EXPENSE")
            @RequestParam(name="type", required = false)TransactionType type
            ){
        List<TransactionResponse> response = getTransactionService.list(user.id(), startDate, endDate, type)
                .stream()
                .map(TransactionResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
