package com.fsc.secengine.audit.application;

import com.fsc.secengine.audit.domain.AuditTransaction;
import com.fsc.secengine.audit.domain.AuditTransactionRepository;
import com.fsc.secengine.audit.infrastructure.aws.AwsAuditNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AuditUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuditUseCase.class);
    private final AuditTransactionRepository repository;
    private final AwsAuditNotifier notifier;

    public AuditUseCase(AuditTransactionRepository repository, AwsAuditNotifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    @Transactional
    public String evaluateTransaction(String payload) {
        log.info("Avaliando transação: {}", payload);
        
        // Mock Parse de Payload para fins de demonstração
        UUID transactionId = UUID.randomUUID(); // Geraria o UUIDv7 idealmente
        AuditTransaction tx = new AuditTransaction(
                transactionId, 
                UUID.randomUUID(), 
                new BigDecimal("500.00"), 
                "PENDING"
        );

        // Heurística Simples
        if (payload.contains("FRAUD_SIMULATE")) {
            tx.markAsFraud();
            notifier.notifyFraud(transactionId.toString(), "Heurística falhou");
        } else {
            tx.markAsApproved();
        }

        repository.save(tx);
        return tx.getStatus();
    }
}
