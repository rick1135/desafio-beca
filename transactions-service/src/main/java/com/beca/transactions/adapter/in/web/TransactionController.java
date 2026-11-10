package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.CreateTransactionRequest;
import com.beca.transactions.adapter.in.web.dto.TransactionResponse;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.application.usecase.GetTransactionService;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.AuthenticatedUser;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionType;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

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

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable Long id,
                                                       @AuthenticationPrincipal AuthenticatedUser user) {
        if (id <= 0) {
            throw new InvalidTransactionException("ID inválido");
        }
        return ResponseEntity.ok(TransactionResponse.from(getTransactionService.getById(id, user.id())));
    }

    @GetMapping("/{id}/corrections")
    public ResponseEntity<List<TransactionResponse>> getCorrections(
            @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (id <= 0) {
            throw new InvalidTransactionException("ID inválido");
        }
        return ResponseEntity.ok(getTransactionService.getCorrections(id, user.id())
                .stream()
                .map(TransactionResponse::from)
                .toList());
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(name="startDate", required = false)
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate startDate,
            @RequestParam(name="endDate", required = false)
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate endDate,
            @RequestParam(name="type", required = false)TransactionType type
            ){
        List<TransactionResponse> response = getTransactionService.list(user.id(), startDate, endDate, type)
                .stream()
                .map(TransactionResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
