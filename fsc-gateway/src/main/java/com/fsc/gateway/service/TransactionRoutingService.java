package com.fsc.gateway.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class TransactionRoutingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionRoutingService.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    // gRPC Client será injetado aqui posteriormente

    public TransactionRoutingService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Rota de tempo real via gRPC. Protegida por Circuit Breaker e Time Limiter.
     * Caso o Sec Engine falhe (ou gRPC dê timeout), o fallback é acionado.
     */
    @CircuitBreaker(name = "secEngineCircuitBreaker", fallbackMethod = "fallbackRealtimeRouting")
    @TimeLimiter(name = "secEngineTimeLimiter")
    public CompletableFuture<String> routeRealtime(String payload) {
        log.info("Roteando transação em tempo real (gRPC). Payload: {}", payload);
        // TODO: Invocar gRPC Sec Engine stub. Para agora, retornamos um mock future.
        return CompletableFuture.completedFuture("AUDIT_SUCCESS_GRPC");
    }

    /**
     * Fallback: Em caso de falha no gRPC, encaminha a transação para a fila Kafka (DLQ de processamento pendente)
     */
    public CompletableFuture<String> fallbackRealtimeRouting(String payload, Throwable ex) {
        log.warn("Falha no Sec Engine via gRPC. Acionando Fallback para Kafka. Motivo: {}", ex.getMessage());
        routeBatch(payload);
        return CompletableFuture.completedFuture("QUEUED_FOR_BATCH_PROCESSING");
    }

    /**
     * Rota de Lote (Massiva) via Kafka (Tópico: fsc.transactions.pending).
     */
    public void routeBatch(String payload) {
        log.info("Publicando transação em lote no Kafka...");
        kafkaTemplate.send("fsc.transactions.pending", payload)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Mensagem enviada com sucesso ao Kafka: offset {}", result.getRecordMetadata().offset());
                } else {
                    log.error("Erro crítico ao publicar no Kafka", ex);
                }
            });
    }
}
