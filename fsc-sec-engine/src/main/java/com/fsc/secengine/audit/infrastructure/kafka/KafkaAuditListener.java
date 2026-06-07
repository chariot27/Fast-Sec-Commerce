package com.fsc.secengine.audit.infrastructure.kafka;

import com.fsc.secengine.audit.application.AuditUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class KafkaAuditListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditListener.class);
    private final AuditUseCase auditUseCase;

    public KafkaAuditListener(AuditUseCase auditUseCase) {
        this.auditUseCase = auditUseCase;
    }

    @KafkaListener(topics = "fsc.transactions.pending", groupId = "sec-engine-group")
    public void consumePendingTransactionBatch(String payload, Acknowledgment acknowledgment) {
        log.info("Lote recebido do Kafka para auditoria. TraceId propagado via OpenTelemetry. Payload: {}", payload);
        try {
            String result = auditUseCase.evaluateTransaction(payload);
            log.info("Processamento concluído. Status: {}", result);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Erro processando lote. Enviando para DLQ...", e);
        }
    }
}
