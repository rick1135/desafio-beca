package com.beca.transactions.domain.model;

import com.beca.transactions.domain.exception.InvalidTransactionException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class Transaction {
    private static final String DEPOSIT_CATEGORY = "Depósito";
    private static final String WITHDRAW_CATEGORY = "Retirada";

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
    private final List<ExchangeRate> exchangeRates;
    private final String correctionReason;

    public Transaction(Long id, TransactionType type, BigDecimal amount, String category,
                       Long originUserId, Long destinationUserId, TransactionStatus status,
                       String rejectionReason, Long originalTransactionId,
                       String description, Instant creationDate) {
        this(id, type, amount, category, originUserId, destinationUserId,
                status, rejectionReason, originalTransactionId,
                description, creationDate, List.of(), null);
    }

    public Transaction(Long id, TransactionType type, BigDecimal amount, String category,
                       Long originUserId, Long destinationUserId, TransactionStatus status,
                       String rejectionReason, Long originalTransactionId,
                       String description, Instant creationDate, List<ExchangeRate> exchangeRates) {
        this(id, type, amount, category, originUserId, destinationUserId,
                status, rejectionReason, originalTransactionId,
                description, creationDate, exchangeRates, null);
    }

    public Transaction(Long id, TransactionType type, BigDecimal amount, String category,
                       Long originUserId, Long destinationUserId, TransactionStatus status,
                       String rejectionReason, Long originalTransactionId,
                       String description, Instant creationDate, List<ExchangeRate> exchangeRates, String correctionReason) {
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
        if (originUserId == null || originUserId <= 0) {
            throw new InvalidTransactionException("Usuário de origem deve ter ID maior que zero");
        }
        if (type == TransactionType.TRANSFER) {
            if (destinationUserId == null || destinationUserId <= 0) {
                throw new InvalidTransactionException("Usuário de destino deve ter ID maior que zero");
            }
            if (originUserId.equals(destinationUserId)) {
                throw new InvalidTransactionException("Não é possível realizar transferência para si mesmo");
            }
        } else if (destinationUserId != null) {
            throw new InvalidTransactionException("Usuário de destino é permitido apenas para transferências");
        }
        if(status==null) throw new InvalidTransactionException("Status da transação não pode ser null");
        if(status==TransactionStatus.REJECTED && (rejectionReason==null|| rejectionReason.isBlank())) throw new InvalidTransactionException(
                "O motivo da transação ter sido rejeitada é obrigatório"
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
        if (originalTransactionId != null && originalTransactionId <= 0) {
            throw new InvalidTransactionException(
                    "Transação original deve ter ID maior que zero"
            );
        }
        if (originalTransactionId == null && correctionReason != null) {
            throw new InvalidTransactionException(
                    "ID da transação original é obrigatório quando há motivo da correção"
            );
        }
        if (correctionReason != null && (correctionReason.isBlank() || correctionReason.length() > 255)) {
            throw new InvalidTransactionException(
                    "Motivo da correção deve ter entre 1 e 255 caracteres"
            );
        }
        if (exchangeRates == null || exchangeRates.stream().anyMatch(Objects::isNull)) {
            throw new InvalidTransactionException(
                    "Cotações de câmbio não podem ser nulas"
            );
        }
        this.type = type;
        this.amount = amount.setScale(2);
        this.category = id == null ? defaultCategory(type, category) : category;
        this.originUserId = originUserId;
        this.destinationUserId = destinationUserId;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.originalTransactionId = originalTransactionId;
        this.description = description;
        this.creationDate = creationDate;
        this.exchangeRates = List.copyOf(exchangeRates);
        this.correctionReason = correctionReason;
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

    public boolean isCorrection() {
        return originalTransactionId != null;
    }

    public void validateCorrectionOf(Transaction original) {
        if (!isCorrection() || original == null || !Objects.equals(originalTransactionId, original.getId())) {
            throw new InvalidTransactionException("A correção deve referenciar a transação original");
        }
        if (original.getStatus() != TransactionStatus.APPROVED) {
            throw new InvalidTransactionException("Apenas transações aprovadas podem ser corrigidas");
        }
        if (original.isCorrection()) {
            throw new InvalidTransactionException("Não é permitido corrigir uma transação que já é uma correção");
        }
        if (type != original.getType()) {
            throw new InvalidTransactionException("A correção deve ter o mesmo tipo da transação original");
        }
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

    public List<ExchangeRate> getExchangeRates() {
        return exchangeRates;
    }

    public String getCorrectionReason() {
        return correctionReason;
    }

    public Transaction withExchangeRates(List<ExchangeRate> rates) {
        return new Transaction(id, type, amount, category, originUserId, destinationUserId,
                status, rejectionReason, originalTransactionId, description, creationDate, rates,
                correctionReason);
    }

    public Transaction reject(String reason) {
        return new Transaction(
                id,
                type,
                amount,
                category,
                originUserId,
                destinationUserId,
                TransactionStatus.REJECTED,
                reason,
                originalTransactionId,
                description,
                creationDate,
                exchangeRates,
                correctionReason
        );
    }

    private static String defaultCategory(TransactionType type, String category) {
        if (category != null && !category.isBlank()) {
            return category;
        }
        return switch (type) {
            case DEPOSIT -> DEPOSIT_CATEGORY;
            case WITHDRAW -> WITHDRAW_CATEGORY;
            default -> category;
        };
    }
}
