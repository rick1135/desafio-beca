package com.beca.transactions.domain.model;

import java.math.BigDecimal;

public class Transaction {
    private final TransactionType type;
    private final BigDecimal amount;
    private final String category;
    private final Long originUserId;
    private final Long destinationUserId;
    private final TransactionStatus status;
    private final String rejectionReason;
    private final Long originalTransactionId;

    public Transaction(TransactionType type, BigDecimal amount, String category,
                       Long originUserId, Long destinationUserId, TransactionStatus status,
                       String rejectionReason, Long originalTransactionId) {
        if(type==TransactionType.PURCHASE && category==null) {
            throw new IllegalArgumentException("Categoria não pode ser nula");
        }
        if(type==null) throw new IllegalArgumentException("Tipo da transação é obrigatório");
        if(amount==null || amount.signum()<=0){
            throw new IllegalArgumentException("Valor da transação deve ser maior que zero");
        }
        if(type==TransactionType.PURCHASE && category.isBlank()){
            throw new IllegalArgumentException("Categoria é obrigatório para compras");
        }
        if(originUserId==null) throw new IllegalArgumentException(
                "Usuário de origem é obrigatório"
        );
        if(type==TransactionType.TRANSFER && destinationUserId==null) throw new IllegalArgumentException(
                "Usuário de destino é obrigatório para transferências"
        );
        if(status==null) throw new IllegalArgumentException("Status da transação não pode ser null");
        if(status==TransactionStatus.REJECTED && (rejectionReason==null|| rejectionReason.isBlank())) throw new IllegalArgumentException(
                "O motivo da transação ter sido rejeitada é obrigatório"
        );
        if(type==TransactionType.TRANSFER && originUserId.equals(destinationUserId)) throw new IllegalArgumentException(
                "Não é possível realizar transferência para si mesmo"
        );
        this.type = type;
        this.amount = amount;
        this.category = category;
        this.originUserId = originUserId;
        this.destinationUserId = destinationUserId;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.originalTransactionId = originalTransactionId;
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
}
