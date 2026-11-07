package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.CreateTransactionRequest;
import com.beca.transactions.adapter.in.web.dto.TransactionResponse;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.application.usecase.GetTransactionService;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.Transaction;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

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

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        if (userId <= 0) {
            throw new InvalidTransactionException("ID do usuário inválido");
        }

        Transaction saved = createTransactionService.create(
                userId,
                request.type(),
                request.amount(),
                request.description(),
                request.category(),
                request.destinationUserId(),
                request.originalTransactionId()
        );

        TransactionResponse response = TransactionResponse.from(saved);
        return ResponseEntity.created(URI.create("/transactions/" + saved.getId()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable Long id,
                                                       @RequestHeader("X-User-Id") Long userId) {
        if (id <= 0 || userId <= 0) {
            throw new InvalidTransactionException("ID inválido");
        }
        return ResponseEntity.ok(TransactionResponse.from(getTransactionService.getById(id, userId)));
    }
}
