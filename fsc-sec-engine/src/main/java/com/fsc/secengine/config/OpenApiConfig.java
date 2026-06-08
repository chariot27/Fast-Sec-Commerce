package com.fsc.secengine.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * OpenAPI 3 / Swagger configuration for the FSC Sec Engine module.
 *
 * <p>Documenta dois tipos de interface:
 * <ul>
 *   <li>REST HTTP: {@code /api/v1/audit/**} — avaliacao manual e health.</li>
 *   <li>gRPC (documentacao): {@code /grpc/TransactionAuditService/**} — contrato proto documentado.</li>
 * </ul>
 *
 * Accessible at:
 *   - Swagger UI:   /swagger-ui/index.html  (porta 8083)
 *   - OpenAPI JSON: /v3/api-docs
 *   - Proto file:   src/main/proto/transaction_audit.proto
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String MTLS_SCHEME   = "mtlsCert";

    @Bean
    public OpenAPI fscSecEngineOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FSC Sec Engine API")
                        .version("1.0.0")
                        .description("""
                                API do modulo FSC-SecEngine — motor de auditoria antifraude.

                                **Interfaces expostas:**
                                - **REST HTTP** (`/api/v1/audit/**`): avaliacao manual, diagnostico e health.
                                - **gRPC** (porta `9092`, mTLS): interface primaria de producao. \
                                Documentada na secao `gRPC — TransactionAuditService` abaixo.

                                **Proto file:** `src/main/proto/transaction_audit.proto`

                                **Autenticacao gRPC:** mTLS com certificados gerados por \
                                `scripts/generate-certs.sh` (ca.crt, gateway.crt, gateway.key).
                                """)
                        .contact(new Contact()
                                .name("FSC Engineering")
                                .url("https://github.com/chariot27/Fast-Sec-Commerce"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8083").description("REST — Local Development"),
                        new Server().url("grpc://localhost:9092").description("gRPC — mTLS (interno)")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Bearer RS256 do Keycloak. Scope: `fsc_admin`."))
                        .addSecuritySchemes(MTLS_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.MUTUALTLS)
                                        .description("Certificado de cliente mTLS assinado pela CA do FSC. " +
                                                     "Arquivos: certs/gateway.crt + certs/gateway.key + certs/ca.crt. " +
                                                     "Gerado por: bash scripts/generate-certs.sh"))
                        .addSchemas("TransactionRequest", buildTransactionRequestSchema())
                        .addSchemas("AuditDecision",      buildAuditDecisionSchema())
                        .addSchemas("AuditStreamRequest", buildAuditStreamRequestSchema())
                        .addSchemas("AuditEvent",         buildAuditEventSchema()));
    }

    /**
     * Adiciona os paths gRPC como documentacao no Swagger.
     * Os paths usam prefixo /grpc/ para distinguir de REST.
     * O server gRPC (grpc://localhost:9092) e selecionado pelo usuario no dropdown de Servers.
     */
    @Bean
    public OpenApiCustomizer grpcPathsCustomizer() {
        return openApi -> {

            // ── EvaluateTransaction (unary) ───────────────────────────
            openApi.path("/grpc/TransactionAuditService/EvaluateTransaction",
                    new PathItem()
                            .post(new Operation()
                                    .addTagsItem("gRPC — TransactionAuditService")
                                    .summary("EvaluateTransaction — RPC unario sincrono")
                                    .description("""
                                            **Tipo:** Unary RPC

                                            **Proto:**
                                            ```protobuf
                                            rpc EvaluateTransaction(TransactionRequest) returns (AuditDecision);
                                            ```

                                            **Canal:** `grpc://sec-engine:9092` com mTLS obrigatorio.

                                            **Chamado por:** Gateway (`fsc-gateway`) via `grpc-client-spring-boot-starter`.
                                            Circuit Breaker ativo (threshold 60% / janela 50 calls / timeout 8s).

                                            **Heuristicas aplicadas:**
                                            1. Deteccao de keyword `FRAUD_SIMULATE` (testes de pentest).
                                            2. Validacao de formato dos Account IDs (ACC-XXX-TYPE).
                                            3. Verificacao de velocidade de transacoes por conta.
                                            4. Score de risco calculado (0.0 — 1.0).

                                            **Em caso de fraude:**
                                            - Alerta emitido para AWS SQS (`fsc-fraud-alerts-queue`).
                                            - Laudo anonimizado salvo no S3 (`fsc-audit-reports`).
                                            - Registro no PostgreSQL com `status = FRAUD_BLOCKED`.

                                            **Codigos de status gRPC:**

                                            | Codigo | Nome | Descricao |
                                            |--------|------|-----------|
                                            | 0 | OK | Avaliacao concluida. Ver `status` no response. |
                                            | 3 | INVALID_ARGUMENT | Payload invalido ou campos ausentes. |
                                            | 4 | DEADLINE_EXCEEDED | Timeout 8s. Gateway aciona fallback Kafka. |
                                            | 13 | INTERNAL | Falha ao persistir ou publicar no SQS. |
                                            | 14 | UNAVAILABLE | Sec Engine temporariamente indisponivel. |

                                            > **Nota:** Este path documenta o contrato gRPC. Para testar, use
                                            > `grpcurl` ou `Evans` CLI apontando para `localhost:9092`.
                                            """)
                                    .requestBody(new RequestBody()
                                            .description("TransactionRequest (mensagem Protocol Buffers serializada)")
                                            .required(true)
                                            .content(new Content().addMediaType("application/json",
                                                    new MediaType()
                                                            .schema(new Schema<>().$ref("#/components/schemas/TransactionRequest"))
                                                            .example(Map.of(
                                                                    "transaction_id",  "550e8400-e29b-41d4-a716-446655440000",
                                                                    "order_id",        "ORD-20240601-001",
                                                                    "debit_account",   "ACC-001-BUYER",
                                                                    "credit_account",  "ACC-003-MERCHANT",
                                                                    "amount_cents",    25000,
                                                                    "currency",        "BRL",
                                                                    "metadata",        Map.of("client_ip", "192.168.1.100", "user_agent", "FSC-Gateway/1.0")
                                                            )))))
                                    .responses(new ApiResponses()
                                            .addApiResponse("200", new ApiResponse()
                                                    .description("gRPC OK — AuditDecision retornada com sucesso.")
                                                    .content(new Content().addMediaType("application/json",
                                                            new MediaType()
                                                                    .schema(new Schema<>().$ref("#/components/schemas/AuditDecision"))
                                                                    .example(Map.of(
                                                                            "transaction_id",    "550e8400-e29b-41d4-a716-446655440000",
                                                                            "status",            "APPROVED",
                                                                            "risk_score",        0.12,
                                                                            "reason",            "HEURISTIC_PASS",
                                                                            "audit_record_id",   "8f14e45f-ceea-467a-a866-085cf4acb3cc",
                                                                            "processing_time_ms",  23
                                                                    )))))
                                            .addApiResponse("400", new ApiResponse()
                                                    .description("gRPC INVALID_ARGUMENT (3) — Payload invalido."))
                                            .addApiResponse("408", new ApiResponse()
                                                    .description("gRPC DEADLINE_EXCEEDED (4) — Timeout 8s. Gateway usa fallback Kafka."))
                                            .addApiResponse("500", new ApiResponse()
                                                    .description("gRPC INTERNAL (13) — Falha interna."))
                                            .addApiResponse("503", new ApiResponse()
                                                    .description("gRPC UNAVAILABLE (14) — Sec Engine indisponivel.")))
                                    .addSecurityItem(new SecurityRequirement().addList(MTLS_SCHEME))));

            // ── StreamAuditEvents (server-side streaming) ─────────────
            openApi.path("/grpc/TransactionAuditService/StreamAuditEvents",
                    new PathItem()
                            .post(new Operation()
                                    .addTagsItem("gRPC — TransactionAuditService")
                                    .summary("StreamAuditEvents — Server-side streaming")
                                    .description("""
                                            **Tipo:** Server-side Streaming RPC

                                            **Proto:**
                                            ```protobuf
                                            rpc StreamAuditEvents(AuditStreamRequest) returns (stream AuditEvent);
                                            ```

                                            **Canal:** `grpc://sec-engine:9092` com mTLS obrigatorio.

                                            **Descricao:**
                                            O cliente envia um `AuditStreamRequest` e o Sec Engine emite um stream
                                            continuo de `AuditEvent` a cada transacao avaliada.

                                            Util para monitoramento em tempo real sem polling REST.
                                            Alternativa ao SSE REST (`/zosconnect/ledger-events` do Mainframe).

                                            **Codigos de status gRPC:**

                                            | Codigo | Nome | Descricao |
                                            |--------|------|-----------|
                                            | 0 | OK | Stream encerrado normalmente. |
                                            | 1 | CANCELLED | Cliente cancelou o stream. |
                                            | 13 | INTERNAL | Falha ao acessar fonte de eventos. |

                                            **Formato do AuditEvent (stream item):**
                                            ```json
                                            {
                                              "event_type": "TRANSACTION_EVALUATED",
                                              "decision": { "transaction_id": "...", "status": "APPROVED", "risk_score": 0.1 },
                                              "timestamp": 1717200000
                                            }
                                            ```

                                            > **Nota:** Este path documenta o contrato gRPC streaming.
                                            > Use `grpcurl -d @ localhost:9092 com.fsc.secengine.grpc.TransactionAuditService/StreamAuditEvents`
                                            > para testar via CLI.
                                            """)
                                    .requestBody(new RequestBody()
                                            .description("AuditStreamRequest")
                                            .required(true)
                                            .content(new Content().addMediaType("application/json",
                                                    new MediaType()
                                                            .schema(new Schema<>().$ref("#/components/schemas/AuditStreamRequest"))
                                                            .example(Map.of("since_timestamp", 0)))))
                                    .responses(new ApiResponses()
                                            .addApiResponse("200", new ApiResponse()
                                                    .description("gRPC OK — Stream de AuditEvent iniciado. Cada item e um AuditEvent.")
                                                    .content(new Content().addMediaType("application/json",
                                                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/AuditEvent")))))
                                            .addApiResponse("500", new ApiResponse()
                                                    .description("gRPC INTERNAL (13) — Falha ao acessar fonte de eventos.")))
                                    .addSecurityItem(new SecurityRequirement().addList(MTLS_SCHEME))));
        };
    }

    // ── Schema builders ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Schema<?> buildTransactionRequestSchema() {
        return new Schema<>()
                .type("object")
                .description("Mensagem Protocol Buffers: TransactionRequest. " +
                             "Definida em src/main/proto/transaction_audit.proto")
                .addProperty("transaction_id",  strSchema("UUID da transacao. Exemplo: 550e8400-e29b-41d4-a716-446655440000"))
                .addProperty("order_id",        strSchema("ID do pedido no sistema parceiro. Exemplo: ORD-20240601-001"))
                .addProperty("debit_account",   strSchema("Account ID debitado. Formato: ACC-XXX-BUYER"))
                .addProperty("credit_account",  strSchema("Account ID creditado. Formato: ACC-XXX-MERCHANT"))
                .addProperty("amount_cents",    new Schema<>().type("integer").format("int64")
                        .description("Valor em centavos. R$250,00 = 25000"))
                .addProperty("currency",        strSchema("ISO 4217. Exemplo: BRL"))
                .addProperty("metadata",        new Schema<>().type("object")
                        .description("Metadados: client_ip, user_agent, device_fingerprint, etc."))
                .required(List.of("transaction_id", "debit_account", "credit_account", "amount_cents", "currency"));
    }

    @SuppressWarnings("unchecked")
    private Schema<?> buildAuditDecisionSchema() {
        return new Schema<>()
                .type("object")
                .description("Mensagem Protocol Buffers: AuditDecision. Retornada pelo EvaluateTransaction RPC.")
                .addProperty("transaction_id",    strSchema("Echo do transaction_id do request"))
                .addProperty("status",            new Schema<>().type("string")
                        ._enum(List.of("APPROVED", "FRAUD_BLOCKED", "PENDING_REVIEW", "INSUFFICIENT_DATA"))
                        .description("Resultado da avaliacao antifraude (enum AuditStatus)"))
                .addProperty("risk_score",        new Schema<>().type("number").format("double")
                        .description("Score de risco: 0.0 (sem risco) a 1.0 (fraude certa)"))
                .addProperty("reason",            strSchema("Motivo: HEURISTIC_PASS, HEURISTIC_FAIL, VELOCITY_CHECK..."))
                .addProperty("audit_record_id",   strSchema("UUID do registro no PostgreSQL (tabela audit_transactions)"))
                .addProperty("processing_time_ms", new Schema<>().type("integer").format("int64")
                        .description("Tempo de processamento em milissegundos"));
    }

    @SuppressWarnings("unchecked")
    private Schema<?> buildAuditStreamRequestSchema() {
        return new Schema<>()
                .type("object")
                .description("Mensagem Protocol Buffers: AuditStreamRequest.")
                .addProperty("since_timestamp", new Schema<>().type("integer").format("int64")
                        .description("Unix timestamp. 0 = eventos a partir de agora."));
    }

    @SuppressWarnings("unchecked")
    private Schema<?> buildAuditEventSchema() {
        return new Schema<>()
                .type("object")
                .description("Mensagem Protocol Buffers: AuditEvent. Item do stream StreamAuditEvents.")
                .addProperty("event_type", new Schema<>().type("string")
                        ._enum(List.of("TRANSACTION_EVALUATED", "FRAUD_ALERT_EMITTED", "REPORT_STORED"))
                        .description("Tipo do evento (enum AuditEventType)"))
                .addProperty("decision",   new Schema<>().$ref("#/components/schemas/AuditDecision")
                        .description("Decisao associada ao evento"))
                .addProperty("timestamp",  new Schema<>().type("integer").format("int64")
                        .description("Unix timestamp do evento"));
    }

    private Schema<?> strSchema(String description) {
        return new Schema<>().type("string").description(description);
    }
}
