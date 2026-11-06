package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.CreateTransactionRequest;
import com.beca.transactions.adapter.in.web.dto.TransactionResponse;
import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.Transaction;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final CreateTransactionService createTransactionService;

    public TransactionController(CreateTransactionService createTransactionService) {
        this.createTransactionService = createTransactionService;
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

        TransactionResponse response = new TransactionResponse(
                saved.getId(),
                saved.getType(),
                saved.getAmount(),
                "BRL",
                saved.getCreationDate(),
                saved.getDescription(),
                saved.getCategory(),
                saved.getOriginUserId(),
                saved.getDestinationUserId(),
                saved.getStatus(),
                saved.getRejectionReason(),
                saved.getOriginalTransactionId()
        );
        return ResponseEntity.created(URI.create("/transactions/" + saved.getId()))
                .body(response);
    }
}
