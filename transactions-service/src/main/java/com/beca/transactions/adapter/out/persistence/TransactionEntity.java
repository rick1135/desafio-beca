package com.beca.transactions.adapter.out.persistence;

import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.beca.transactions.domain.model.ExchangeRate;

@Entity
@Table(name = "transactions")
public class TransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    private String category;

    @Column(nullable = false)
    private Long originUserId;

    private Long destinationUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String rejectionReason;
    private Long originalTransactionId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Instant creationDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "transaction_exchange_rates", joinColumns = @JoinColumn(name = "transaction_id"))
    private List<ExchangeRateEmbeddable> exchangeRates = new ArrayList<>();

    public TransactionEntity() {
    }

    public TransactionEntity(Long id, TransactionType type, BigDecimal amount, String category, Long originUserId, Long destinationUserId,
                             TransactionStatus status, String rejectionReason, Long originalTransactionId, String description, Instant creationDate
    ) {
        this(id, type, amount, category, originUserId, destinationUserId,
                status, rejectionReason, originalTransactionId, description, creationDate, List.of());
    }

    public TransactionEntity(Long id, TransactionType type, BigDecimal amount, String category, Long originUserId, Long destinationUserId,
                             TransactionStatus status, String rejectionReason, Long originalTransactionId, String description, Instant creationDate,
                             List<ExchangeRate> exchangeRates) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.category = category;
        this.originUserId = originUserId;
        this.destinationUserId = destinationUserId;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.originalTransactionId = originalTransactionId;
        this.description = description;
        this.creationDate = creationDate;
        this.exchangeRates = exchangeRates.stream().map(ExchangeRateEmbeddable::new).toList();
    }

    public Long getId() {
        return id;
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

    public String getDescription() {
        return description;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public List<ExchangeRate> getExchangeRates() {
        return exchangeRates.stream().map(ExchangeRateEmbeddable::toDomain).toList();
    }
}
