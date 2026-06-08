package com.fsc.gateway.controller;

import com.fsc.gateway.service.TransactionRoutingService;
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

import java.util.concurrent.CompletableFuture;

/**
 * Ponto de entrada REST para processamento de transacoes financeiras.
 *
 * <p>Duas modalidades de roteamento:
 * <ul>
 *   <li><b>Realtime</b> – gRPC sincrono com mTLS para o Sec Engine (Circuit Breaker ativo).</li>
 *   <li><b>Batch</b>   – Kafka assíncrono para processamento em lote de alta throughput.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(
    name = "Transactions",
    description = "Processamento e roteamento de transacoes financeiras. " +
                  "Requer Bearer JWT valido emitido pelo Keycloak (scope fsc_user ou fsc_admin)."
)
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionRoutingService routingService;

    public TransactionController(TransactionRoutingService routingService) {
        this.routingService = routingService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/transactions/realtime
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Processar transacao em tempo real (gRPC)",
        description = """
            Envia a transacao diretamente ao **Sec Engine** via gRPC + mTLS para analise antifraude síncrona.

            **Circuit Breaker (Resilience4j):**
            - Janela deslizante: 50 chamadas | Limiar de falha: 60%.
            - Em estado OPEN, o fallback automatico enfileira a transacao no Kafka.

            **Time Limiter:** Timeout de 8 segundos. Excedido, aciona fallback.

            **Rate Limit (Redis):** Máximo de 10 req/s por IP. HTTP 429 se excedido.

            **Headers necessarios:**
            - `Authorization: Bearer <JWT>` — Token JWT RS256 do Keycloak.
            - `Content-Type: application/json`

            **Formato do payload (JSON recomendado):**
            ```json
            {
              "orderId": "ORD-20240601-001",
              "debitAcct": "ACC-001-BUYER",
              "creditAcct": "ACC-003-MERCHANT",
              "amount": 250.00,
              "currency": "BRL"
            }
            ```
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transacao processada com sucesso pelo Sec Engine.",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                examples = @ExampleObject(value = "AUDIT_SUCCESS_GRPC")
            )
        ),
        @ApiResponse(
            responseCode = "200",
            description = "Fallback acionado: transacao enfileirada no Kafka para processamento posterior.",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                examples = @ExampleObject(value = "QUEUED_FOR_BATCH_PROCESSING")
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Token JWT ausente, expirado ou com assinatura invalida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit excedido: mais de 10 requisicoes por segundo para este IP.",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                examples = @ExampleObject(value = "Too Many Requests - Rate Limit Exceeded"))
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Circuit Breaker em estado OPEN. Sec Engine indisponivel. Tente novamente em 5s.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping(value = "/realtime", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<String>> processRealtime(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Payload da transacao em formato JSON. O Sec Engine desserializa e aplica heuristicas.",
                required = true,
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(type = "string", format = "json"),
                    examples = @ExampleObject(
                        name = "Transacao padrao",
                        value = """
                            {
                              "orderId": "ORD-20240601-001",
                              "debitAcct": "ACC-001-BUYER",
                              "creditAcct": "ACC-003-MERCHANT",
                              "amount": 250.00,
                              "currency": "BRL"
                            }
                            """
                    )
                )
            )
            @RequestBody String payload) {
        return routingService.routeRealtime(payload)
                .thenApply(ResponseEntity::ok);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/transactions/batch
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Enfileirar transacao em lote (Kafka)",
        description = """
            Publica a transacao no topico Kafka **fsc.transactions.pending** para processamento assíncrono
            pelo Sec Engine.

            **Quando usar:**
            - Processamento massivo (bulk import, conciliacao noturna).
            - Fallback manual quando o endpoint realtime esta indisponivel.

            **Comportamento do producer Kafka:**
            - `acks=1` (ACK do lider): equilibrio entre velocidade e durabilidade.
            - Batch de 64KB com linger de 5ms para agrupamento eficiente.
            - Compressao LZ4 para reducao do trafego de rede.

            **Retorno imediato HTTP 202 Accepted** — o processamento ocorre de forma assíncrona.
            O status final pode ser consultado via SSE no Core Ledger (`/zosconnect/ledger-events`).
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "202",
            description = "Transacao aceita e enfileirada no Kafka com sucesso.",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                examples = @ExampleObject(value = "Transacao recebida para processamento em lote.")
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Token JWT ausente, expirado ou com assinatura invalida.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit excedido: mais de 10 requisicoes por segundo para este IP.",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE)
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Falha critica ao publicar no broker Kafka.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> processBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Payload da transacao em formato JSON. Publicado no topico fsc.transactions.pending.",
                required = true,
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(type = "string", format = "json"),
                    examples = @ExampleObject(
                        name = "Lote de transacao",
                        value = """
                            {
                              "orderId": "ORD-BATCH-20240601",
                              "debitAcct": "ACC-002-BUYER",
                              "creditAcct": "ACC-003-MERCHANT",
                              "amount": 89.90,
                              "currency": "BRL"
                            }
                            """
                    )
                )
            )
            @RequestBody String payload) {
        routingService.routeBatch(payload);
        return ResponseEntity.accepted().body("Transacao recebida para processamento em lote.");
    }
}
