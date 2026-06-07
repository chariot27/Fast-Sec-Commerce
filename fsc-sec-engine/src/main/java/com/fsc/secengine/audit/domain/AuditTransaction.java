package com.fsc.secengine.audit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class AuditTransaction {

    @Id
    private UUID id;
    
    private UUID customerId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;

    protected AuditTransaction() {}

    public AuditTransaction(UUID id, UUID customerId, BigDecimal amount, String status) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsFraud() {
        this.status = "FRAUD_DETECTED";
    }

    public void markAsApproved() {
        this.status = "APPROVED";
    }

    // Getters
    public UUID getId() { return id; }
    public String getStatus() { return status; }
}
