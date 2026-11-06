package com.beca.transactions.domain.model;

import com.beca.transactions.domain.exception.InvalidTransactionException;

import java.math.BigDecimal;
import java.time.Instant;

public class Transaction {
    private final Long id;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String category;
    private final Long originUserId;
    private final Long destinationUserId;
    private final TransactionStatus status;
    private final String rejectionReason;
    private final Long originalTransactionId;
    private final String description;
    private final Instant creationDate;

    public Transaction(Long id, TransactionType type, BigDecimal amount, String category,
                       Long originUserId, Long destinationUserId, TransactionStatus status,
                       String rejectionReason, Long originalTransactionId,
                       String description, Instant creationDate) {
        this.id = id;
        if(type==TransactionType.PURCHASE && category==null) {
            throw new InvalidTransactionException("Categoria não pode ser nula");
        }
        if(type==null) throw new InvalidTransactionException("Tipo da transação é obrigatório");
        if(amount==null || amount.signum()<=0){
            throw new InvalidTransactionException("Valor da transação deve ser maior que zero");
        }
        if(type==TransactionType.PURCHASE && category.isBlank()){
            throw new InvalidTransactionException("Categoria é obrigatório para compras");
        }
        if(originUserId==null) throw new InvalidTransactionException(
                "Usuário de origem é obrigatório"
        );
        if(type==TransactionType.TRANSFER && destinationUserId==null) throw new InvalidTransactionException(
                "Usuário de destino é obrigatório para transferências"
        );
        if(status==null) throw new InvalidTransactionException("Status da transação não pode ser null");
        if(status==TransactionStatus.REJECTED && (rejectionReason==null|| rejectionReason.isBlank())) throw new InvalidTransactionException(
                "O motivo da transação ter sido rejeitada é obrigatório"
        );
        if(type==TransactionType.TRANSFER && originUserId.equals(destinationUserId)) throw new InvalidTransactionException(
                "Não é possível realizar transferência para si mesmo"
        );
        if (description == null || description.isBlank()) {
            throw new InvalidTransactionException("Descrição da transação é obrigatória");
        }
        if (creationDate == null) {
            throw new InvalidTransactionException("Data da transação é obrigatória");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidTransactionException("Valor da transação deve ter no máximo duas casas decimais");
        }
        if (amount.setScale(2).precision() > 19) {
            throw new InvalidTransactionException("Valor da transação excede o limite permitido");
        }
        if (description.length() > 255) {
            throw new InvalidTransactionException(
                    "Descrição deve ter no máximo 255 caracteres"
            );
        }
        if (category != null && category.length() > 255) {
            throw new InvalidTransactionException(
                    "Categoria deve ter no máximo 255 caracteres"
            );
        }
        if (rejectionReason != null && rejectionReason.length() > 255) {
            throw new InvalidTransactionException(
                    "Motivo da rejeição deve ter no máximo 255 caracteres"
            );
        }
        this.type = type;
        this.amount = amount.setScale(2);
        this.category = category;
        this.originUserId = originUserId;
        this.destinationUserId = destinationUserId;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.originalTransactionId = originalTransactionId;
        this.description = description;
        this.creationDate = creationDate;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public Long getOriginUserId() {
        return originUserId;
    }

    public Long getDestinationUserId() {
        return destinationUserId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getOriginalTransactionId() {
        return originalTransactionId;
    }

    public Long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreationDate() {
        return creationDate;
    }
}
