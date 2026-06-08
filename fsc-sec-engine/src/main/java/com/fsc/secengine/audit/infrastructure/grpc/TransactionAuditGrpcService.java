package com.fsc.secengine.audit.infrastructure.grpc;

import com.fsc.secengine.audit.application.AuditUseCase;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementacao do servico gRPC TransactionAuditService.
 *
 * <p>Canal: grpc://sec-engine:9092 (mTLS obrigatorio via gateway.crt + ca.crt).
 *
 * <p>Este servico e chamado pelo Gateway via {@code grpc-client-spring-boot-starter}
 * com Circuit Breaker (Resilience4j) e Time Limiter (8s timeout).
 *
 * <p>O contrato completo esta definido em:
 * {@code src/main/proto/transaction_audit.proto}
 *
 * <p>Metodos expostos:
 * <ul>
 *   <li>{@code EvaluateTransaction} — Avaliacao sincrona unaria de transacao.</li>
 *   <li>{@code StreamAuditEvents}  — Server-side streaming de eventos de auditoria.</li>
 * </ul>
 *
 * <p><strong>Seguranca:</strong>
 * <ul>
 *   <li>mTLS: certificado de cliente validado antes de aceitar qualquer chamada.</li>
 *   <li>Sem autenticacao Bearer JWT nesta camada — o Gateway ja valida o JWT do cliente final.</li>
 *   <li>Comunicacao exclusivamente na rede interna ({@code fsc-network}).</li>
 * </ul>
 */
@GrpcService
public class TransactionAuditGrpcService {

    private static final Logger log = LoggerFactory.getLogger(TransactionAuditGrpcService.class);

    private final AuditUseCase auditUseCase;

    public TransactionAuditGrpcService(AuditUseCase auditUseCase) {
        this.auditUseCase = auditUseCase;
    }

    /**
     * RPC unario: EvaluateTransaction
     *
     * <p>Avalia uma transacao de forma sincrona e retorna a decisao antifraude.
     * Este metodo e o ponto de entrada principal do fluxo realtime.
     *
     * <p><strong>Contrato proto:</strong>
     * <pre>
     * rpc EvaluateTransaction(TransactionRequest) returns (AuditDecision);
     * </pre>
     *
     * <p><strong>Campos do request (TransactionRequest):</strong>
     * <ul>
     *   <li>{@code transaction_id} (string) — UUID da transacao</li>
     *   <li>{@code order_id} (string) — ID do pedido do sistema parceiro</li>
     *   <li>{@code debit_account} (string) — Account ID debitado</li>
     *   <li>{@code credit_account} (string) — Account ID creditado</li>
     *   <li>{@code amount_cents} (int64) — Valor em centavos (ex: R$250,00 = 25000)</li>
     *   <li>{@code currency} (string) — ISO 4217 (ex: BRL)</li>
     *   <li>{@code metadata} (map) — IP, user-agent, etc.</li>
     * </ul>
     *
     * <p><strong>Campos do response (AuditDecision):</strong>
     * <ul>
     *   <li>{@code transaction_id} (string) — Echo do request</li>
     *   <li>{@code status} (AuditStatus) — APPROVED | FRAUD_BLOCKED | PENDING_REVIEW</li>
     *   <li>{@code risk_score} (double) — Score 0.0 a 1.0</li>
     *   <li>{@code reason} (string) — Motivo da decisao</li>
     *   <li>{@code audit_record_id} (string) — ID no PostgreSQL</li>
     *   <li>{@code processing_time_ms} (int64) — Tempo de processamento</li>
     * </ul>
     *
     * <p><strong>Heuristicas aplicadas (fase 1):</strong>
     * <ul>
     *   <li>Deteccao de keyword {@code FRAUD_SIMULATE} (testes de pentest)</li>
     *   <li>Validacao de formato dos Account IDs</li>
     *   <li>Persistencia do resultado no PostgreSQL (tabela {@code audit_transactions})</li>
     *   <li>Notificacao SQS em caso de fraude ({@code fsc-fraud-alerts-queue})</li>
     *   <li>Laudo mascarado salvo no S3 ({@code fsc-audit-reports})</li>
     * </ul>
     *
     * <p><strong>Codigos de status gRPC possiveis:</strong>
     * <ul>
     *   <li>{@code OK (0)} — Avaliacao concluida (status no response indica APPROVED ou FRAUD_BLOCKED)</li>
     *   <li>{@code INVALID_ARGUMENT (3)} — Payload invalido ou campos obrigatorios ausentes</li>
     *   <li>{@code INTERNAL (13)} — Falha ao persistir no PostgreSQL ou publicar no SQS</li>
     *   <li>{@code UNAVAILABLE (14)} — Sec Engine temporariamente indisponivel</li>
     *   <li>{@code DEADLINE_EXCEEDED (4)} — Timeout de 8s excedido (Gateway aciona fallback Kafka)</li>
     * </ul>
     */
    public void evaluateTransaction(Object request, StreamObserver<Object> responseObserver) {
        long startTime = System.currentTimeMillis();
        log.info("[gRPC] EvaluateTransaction recebido.");

        try {
            // Em implementacao completa: deserializar TransactionRequest do proto
            // e chamar auditUseCase.evaluateTransaction(payload)
            String payload = request != null ? request.toString() : "";
            String status = auditUseCase.evaluateTransaction(payload);

            log.info("[gRPC] EvaluateTransaction concluido. Status: {} | {}ms",
                    status, System.currentTimeMillis() - startTime);

            // Em implementacao completa: construir AuditDecision proto response
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("[gRPC] Erro ao processar EvaluateTransaction", e);
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Erro interno ao avaliar transacao: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    /**
     * RPC server-side streaming: StreamAuditEvents
     *
     * <p>Emite eventos de auditoria continuamente para o cliente (Gateway).
     * Util para monitoramento em tempo real sem polling.
     *
     * <p><strong>Contrato proto:</strong>
     * <pre>
     * rpc StreamAuditEvents(AuditStreamRequest) returns (stream AuditEvent);
     * </pre>
     *
     * <p><strong>Campos do request (AuditStreamRequest):</strong>
     * <ul>
     *   <li>{@code since_timestamp} (int64) — Unix timestamp. 0 = eventos a partir de agora.</li>
     * </ul>
     *
     * <p><strong>Campos do AuditEvent (stream):</strong>
     * <ul>
     *   <li>{@code event_type} (AuditEventType) — TRANSACTION_EVALUATED | FRAUD_ALERT_EMITTED | REPORT_STORED</li>
     *   <li>{@code decision} (AuditDecision) — Decisao associada</li>
     *   <li>{@code timestamp} (int64) — Unix timestamp do evento</li>
     * </ul>
     *
     * <p><strong>Codigos de status gRPC possiveis:</strong>
     * <ul>
     *   <li>{@code OK (0)} — Stream encerrado normalmente (improvavel — stream e continuo)</li>
     *   <li>{@code CANCELLED (1)} — Cliente cancelou o stream</li>
     *   <li>{@code INTERNAL (13)} — Falha ao acessar a fonte de eventos</li>
     * </ul>
     */
    public void streamAuditEvents(Object request, StreamObserver<Object> responseObserver) {
        log.info("[gRPC] StreamAuditEvents subscricao iniciada.");
        // Implementacao completa: subscribe a evento do Spring ApplicationEventPublisher
        // e emitir AuditEvent proto via responseObserver.onNext() a cada avaliacao.
    }
}
