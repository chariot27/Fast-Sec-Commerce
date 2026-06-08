package com.fsc.gateway.config;

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
 * OpenAPI 3 / Swagger configuration for the FSC Gateway module.
 *
 * <p>Documenta dois tipos de interface:
 * <ul>
 *   <li>REST HTTP: {@code /api/v1/transactions/**} — endpoints de entrada (chamados por clientes externos).</li>
 *   <li>gRPC (documentacao): {@code /grpc-upstream/**} — chamadas que o Gateway faz ao Sec Engine internamente.</li>
 * </ul>
 *
 * Accessible at:
 *   - Swagger UI:   /swagger-ui/index.html  (porta 8082)
 *   - OpenAPI JSON: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String MTLS_SCHEME   = "mtlsCert";

    @Bean
    public OpenAPI fscGatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FSC Gateway API")
                        .version("1.0.0")
                        .description("""
                                API REST do modulo FSC-Gateway — ponto de entrada unico para \
                                processamento de transacoes financeiras.

                                **Fluxo realtime:**
                                `POST /realtime` → gRPC (mTLS) → Sec Engine → PostgreSQL/SQS/S3

                                **Fluxo batch (fallback):**
                                `POST /batch` → Kafka (`fsc.transactions.pending`) → Sec Engine

                                **Secao `gRPC — Gateway → Sec Engine`:** documenta as chamadas \
                                gRPC internas que o Gateway realiza ao Sec Engine. \
                                Canal: `grpc://sec-engine:9092` com mTLS.

                                **Rate Limit:** 10 req/s por JWT sub (HTTP 429 se excedido).

                                **Circuit Breaker:** Threshold 60% / janela 50 calls / timeout 8s. \
                                Fallback automatico para Kafka.
                                """)
                        .contact(new Contact()
                                .name("FSC Engineering")
                                .url("https://github.com/chariot27/Fast-Sec-Commerce"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("REST — Local Development"),
                        new Server().url("grpc://localhost:9092").description("gRPC Upstream — Sec Engine (mTLS)"),
                        new Server().url("https://api.fsc.internal").description("REST — Producao Interna")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("""
                                                JWT Bearer RS256 emitido pelo Keycloak.
                                                Realm: `fsc` | Client: `fsc-gateway`.
                                                Roles: `fsc_user` (transacoes) | `fsc_admin` (administracao).
                                                Endpoint de token: `POST http://localhost:8080/realms/fsc/protocol/openid-connect/token`
                                                """))
                        .addSecuritySchemes(MTLS_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.MUTUALTLS)
                                        .description("""
                                                Certificado de cliente mTLS.
                                                Arquivos: `certs/gateway.crt` + `certs/gateway.key` + `certs/ca.crt`.
                                                Gerar com: `bash scripts/generate-certs.sh`
                                                Usado pelo Gateway para autenticar no Sec Engine via gRPC.
                                                """))
                        .addSchemas("TransactionRequest", buildTransactionRequestSchema())
                        .addSchemas("AuditDecision",      buildAuditDecisionSchema()));
    }

    /**
     * Adiciona paths gRPC upstream ao Swagger do Gateway.
     * Documenta as chamadas internas que o Gateway faz ao Sec Engine.
     * Prefixo /grpc-upstream/ para distinguir de REST e de paths gRPC do Sec Engine.
     */
    @Bean
    public OpenApiCustomizer grpcUpstreamPathsCustomizer() {
        return openApi -> {

            // ── EvaluateTransaction (chamada feita pelo Gateway ao Sec Engine) ──
            openApi.path("/grpc-upstream/SecEngine/EvaluateTransaction",
                    new PathItem()
                            .post(new Operation()
                                    .addTagsItem("gRPC — Gateway para Sec Engine")
                                    .summary("EvaluateTransaction — chamada interna do Gateway ao Sec Engine")
                                    .description("""
                                            **Direcao:** Gateway (cliente) → Sec Engine (servidor)

                                            **Ativado por:** `POST /api/v1/transactions/realtime`

                                            **Tipo gRPC:** Unary RPC

                                            **Proto:**
                                            ```protobuf
                                            // Canal: grpc://sec-engine:9092 (mTLS)
                                            rpc EvaluateTransaction(TransactionRequest) returns (AuditDecision);
                                            ```

                                            **Configuracao no Gateway (`application.yml`):**
                                            ```yaml
                                            grpc:
                                              client:
                                                sec-engine:
                                                  address: static://localhost:9092
                                                  negotiation-type: TLS
                                                  security:
                                                    trust-cert-collection: file:../certs/ca.crt
                                                    client-cert-chain:     file:../certs/gateway.crt
                                                    client-private-key:    file:../certs/gateway.key
                                            ```

                                            **Resilience4j — Circuit Breaker (`secEngineCircuitBreaker`):**

                                            | Parametro | Valor |
                                            |-----------|-------|
                                            | Janela deslizante | 50 chamadas |
                                            | Limiar de falha | 60% |
                                            | Limiar de lentidao | 80% (> 3s) |
                                            | Tempo em OPEN | 5 segundos |
                                            | Calls em HALF-OPEN | 10 |

                                            **Resilience4j — Time Limiter (`secEngineTimeLimiter`):**

                                            | Parametro | Valor |
                                            |-----------|-------|
                                            | Timeout | 8 segundos |
                                            | Cancelar future | Sim |

                                            **Fallback automatico:** Se o Circuit Breaker estiver OPEN ou o timeout
                                            for excedido, a transacao e redirecionada para
                                            `POST /api/v1/transactions/batch` (Kafka).

                                            **Codigos gRPC mapeados para HTTP pelo Gateway:**

                                            | gRPC Status | HTTP Response | Descricao |
                                            |-------------|--------------|-----------|
                                            | OK (0) | 200 | Decisao retornada |
                                            | DEADLINE_EXCEEDED (4) | Fallback Kafka | Timeout → QUEUED_FOR_BATCH_PROCESSING |
                                            | UNAVAILABLE (14) | Fallback Kafka | CB OPEN → QUEUED_FOR_BATCH_PROCESSING |
                                            | INTERNAL (13) | 500 | Erro irrecuperavel |

                                            > **Nota de implementacao:** O stub gRPC real esta pendente
                                            > (`TODO` em `TransactionRoutingService.routeRealtime`).
                                            > Atualmente retorna mock `AUDIT_SUCCESS_GRPC`.
                                            > O contrato completo esta em `src/main/proto/transaction_audit.proto` (Sec Engine).
                                            """)
                                    .requestBody(new RequestBody()
                                            .description("TransactionRequest proto (enviado pelo Gateway ao Sec Engine)")
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
                                                                    "metadata",        Map.of("client_ip", "10.0.0.1")
                                                            )))))
                                    .responses(new ApiResponses()
                                            .addApiResponse("200", new ApiResponse()
                                                    .description("gRPC OK — AuditDecision recebida do Sec Engine.")
                                                    .content(new Content().addMediaType("application/json",
                                                            new MediaType()
                                                                    .schema(new Schema<>().$ref("#/components/schemas/AuditDecision"))
                                                                    .example(Map.of(
                                                                            "transaction_id",     "550e8400-e29b-41d4-a716-446655440000",
                                                                            "status",             "APPROVED",
                                                                            "risk_score",         0.08,
                                                                            "reason",             "HEURISTIC_PASS",
                                                                            "audit_record_id",    "8f14e45f-ceea-467a-a866-085cf4acb3cc",
                                                                            "processing_time_ms", 21
                                                                    )))))
                                            .addApiResponse("200 (fallback)", new ApiResponse()
                                                    .description("Circuit Breaker OPEN ou timeout: Gateway retorna HTTP 200 com `QUEUED_FOR_BATCH_PROCESSING`."))
                                            .addApiResponse("408", new ApiResponse()
                                                    .description("gRPC DEADLINE_EXCEEDED (4) — Timeout de 8s atingido. Fallback para Kafka ativado."))
                                            .addApiResponse("503", new ApiResponse()
                                                    .description("gRPC UNAVAILABLE (14) — Sec Engine indisponivel. Fallback para Kafka ativado.")))
                                    .addSecurityItem(new SecurityRequirement().addList(MTLS_SCHEME))));
        };
    }

    // ── Schema builders ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Schema<?> buildTransactionRequestSchema() {
        return new Schema<>()
                .type("object")
                .description("Mensagem Protocol Buffers: TransactionRequest. " +
                             "Contrato definido em fsc-sec-engine/src/main/proto/transaction_audit.proto")
                .addProperty("transaction_id",  strSchema("UUID da transacao"))
                .addProperty("order_id",        strSchema("ID do pedido no sistema parceiro"))
                .addProperty("debit_account",   strSchema("Account ID debitado. Formato: ACC-XXX-BUYER"))
                .addProperty("credit_account",  strSchema("Account ID creditado. Formato: ACC-XXX-MERCHANT"))
                .addProperty("amount_cents",    new Schema<>().type("integer").format("int64")
                        .description("Valor em centavos. R$250,00 = 25000"))
                .addProperty("currency",        strSchema("ISO 4217. Exemplo: BRL"))
                .addProperty("metadata",        new Schema<>().type("object")
                        .description("Metadados: client_ip, user_agent, device_fingerprint"))
                .required(List.of("transaction_id", "debit_account", "credit_account", "amount_cents", "currency"));
    }

    @SuppressWarnings("unchecked")
    private Schema<?> buildAuditDecisionSchema() {
        return new Schema<>()
                .type("object")
                .description("Mensagem Protocol Buffers: AuditDecision. Retornada pelo EvaluateTransaction RPC.")
                .addProperty("transaction_id",     strSchema("Echo do transaction_id do request"))
                .addProperty("status",             new Schema<>().type("string")
                        ._enum(List.of("APPROVED", "FRAUD_BLOCKED", "PENDING_REVIEW", "INSUFFICIENT_DATA")))
                .addProperty("risk_score",         new Schema<>().type("number").format("double")
                        .description("Score de risco 0.0 (seguro) a 1.0 (fraude certa)"))
                .addProperty("reason",             strSchema("Motivo da decisao"))
                .addProperty("audit_record_id",    strSchema("UUID do registro no PostgreSQL"))
                .addProperty("processing_time_ms", new Schema<>().type("integer").format("int64"));
    }

    private Schema<?> strSchema(String description) {
        return new Schema<>().type("string").description(description);
    }
}
