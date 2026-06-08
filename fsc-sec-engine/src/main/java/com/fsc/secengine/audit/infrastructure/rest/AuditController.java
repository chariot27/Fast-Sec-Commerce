package com.fsc.secengine.audit.infrastructure.rest;

import com.fsc.secengine.audit.application.AuditUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller do modulo de auditoria antifraude.
 *
 * <p>Em producao, a principal via de entrada de transacoes e via gRPC (porta 9092).
 * Este controller REST serve para:
 * <ul>
 *   <li>Injecao manual de payloads para teste e diagnostico.</li>
 *   <li>Exposicao de metricas de auditoria via Actuator/Prometheus.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(
    name = "Audit Engine",
    description = "Motor de auditoria antifraude. Avalia transacoes com heuristicas, persiste no PostgreSQL " +
                  "e emite alertas SQS para fraudes detectadas. Acesso restrito a role `fsc_admin`."
)
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditUseCase auditUseCase;

    public AuditController(AuditUseCase auditUseCase) {
        this.auditUseCase = auditUseCase;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/audit/evaluate
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Avaliar transacao manualmente (diagnostico)",
        description = """
            Submete um payload de transacao diretamente ao motor de heuristicas antifraude.

            **Uso tipico:** teste de regressao, validacao de novas regras de fraude, diagnostico em QA.

            **Heuristicas aplicadas (fase 1 — MVP):**
            - Deteccao de keyword `FRAUD_SIMULATE` no payload (simulacao de testes de pentest).
            - Validacao de UUID de transacao.
            - Persistencia do resultado no PostgreSQL (`audit_transactions`).

            **Fluxo em caso de fraude detectada:**
            1. Status definido como `FRAUD_BLOCKED`.
            2. Notificacao enviada ao AWS SQS (`fsc-fraud-alerts`).
            3. Laudo anonimizado salvo no S3 (`fsc-audit-reports`).

            **Nota:** Em producao, o fluxo normal chega via Kafka. Este endpoint e apenas para diagnostico.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Avaliacao concluida. Retorna o status final da transacao.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                examples = {
                    @ExampleObject(
                        name = "Transacao aprovada",
                        value = """
                            {
                              "transactionId": "550e8400-e29b-41d4-a716-446655440000",
                              "status": "APPROVED",
                              "message": "Transacao aprovada pelas heuristicas antifraude."
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Fraude detectada",
                        value = """
                            {
                              "transactionId": "550e8400-e29b-41d4-a716-446655440001",
                              "status": "FRAUD_BLOCKED",
                              "message": "Transacao bloqueada pelas heuristicas antifraude. Alerta SQS emitido."
                            }
                            """
                    )
                }
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Payload invalido ou malformado.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Token JWT ausente ou invalido.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Role insuficiente. Necessario fsc_admin.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Erro interno durante avaliacao. Verifique logs do PostgreSQL e SQS.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping(value = "/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> evaluate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Payload da transacao. O campo `FRAUD_SIMULATE` ativa a heuristica de teste.",
                required = true,
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(type = "string", format = "json"),
                    examples = {
                        @ExampleObject(
                            name = "Transacao normal",
                            value = """
                                {
                                  "orderId": "ORD-20240601-001",
                                  "debitAcct": "ACC-001-BUYER",
                                  "creditAcct": "ACC-003-MERCHANT",
                                  "amount": 500.00,
                                  "currency": "BRL"
                                }
                                """
                        ),
                        @ExampleObject(
                            name = "Simular fraude (pentest)",
                            value = """
                                {
                                  "orderId": "ORD-FRAUD-001",
                                  "debitAcct": "ACC-002-BUYER",
                                  "creditAcct": "ACC-003-MERCHANT",
                                  "amount": 9999.99,
                                  "currency": "BRL",
                                  "FRAUD_SIMULATE": true
                                }
                                """
                        )
                    }
                )
            )
            @RequestBody String payload) {

        if (payload == null || payload.isBlank()) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "INVALID_PAYLOAD", "message", "Payload nao pode ser vazio.")
            );
        }

        String status = auditUseCase.evaluateTransaction(payload);

        String message = switch (status) {
            case "APPROVED"      -> "Transacao aprovada pelas heuristicas antifraude.";
            case "FRAUD_BLOCKED" -> "Transacao bloqueada pelas heuristicas antifraude. Alerta SQS emitido.";
            default              -> "Status: " + status;
        };

        return ResponseEntity.ok(Map.of(
            "status", status,
            "message", message
        ));
    }
}
