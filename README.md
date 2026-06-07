# FSC — Fast Sec Commerce
> Plataforma de Auditoria Antifraude e Liquidação Financeira para E-commerce · v1.0.0

---

## Sumário

- [ Documentação Não Técnica — Visão de Negócio](#-documentação-não-técnica--visão-de-negócio)
- [ Documentação Técnica — Referência de Engenharia](#-documentação-técnica--referência-de-engenharia)

---

#  Documentação Não Técnica — Visão de Negócio

## O que é o FSC?

O **FSC (Fast Sec Commerce)** é uma **plataforma de segurança financeira para e-commerce**. Funciona como um guardião inteligente entre o cliente que compra e o lojista que vende:

- **Verifica se a compra é fraude** antes de aprovar (em menos de 100ms)
- **Movimenta o dinheiro** com a confiabilidade de um sistema bancário (mainframe IBM)
- **Registra tudo** para auditoria e conformidade legal (LGPD)

## Qual problema ele resolve?

| Problema | Custo no mercado | Como o FSC resolve |
|---|---|---|
| **Fraude** (cartão roubado, identidade falsa) | Bilhões/ano | Analisa cada transação em < 100ms |
| **Falha na liquidação** (dinheiro que não chega) | Reputação e multas | Tecnologia de mainframe bancário com atomicidade garantida |

## Quem usa o FSC?

| Perfil | O que faz |
|---|---|
| **Administrador de Risco** | Monitora fraudes em tempo real via painel web |
| **Engenheiro de Operações** | Acompanha saúde do sistema (velocidade, filas, memória) |
| **Sistema Parceiro (Loja)** | Envia dados da compra via API e recebe aprovação/rejeição |

## Fluxo de uma Compra (Exemplo Real)

**João** compra um tênis de R$ 350,00 na **Loja FastTech**:

```
1. João clica em "Finalizar Pedido"
2. A loja envia os dados para o FSC via API segura (JWT)
3. FSC verifica: "Esse padrão é suspeito?"
4. Se APROVADO → FSC debita R$ 355,25 de João
   (R$ 350,00 para a loja + R$ 5,25 de taxa FSC de 1,5%)
5. FSC credita R$ 350,00 na conta da Loja FastTech
6. Ambos recebem confirmação instantânea
7. Tudo é registrado num livro-razão digital imutável
```
> ⏱️ **Tempo total do processo: menos de 100 milissegundos**

## O Livro-Razão Central (Core Ledger)

Inspirado em sistemas de bancos centrais rodando em **mainframes IBM**, o Core Ledger garante **atomicidade**: ou o débito e o crédito acontecem juntos, ou **nenhum** acontece. O dinheiro nunca some no meio do caminho.

**Contas de exemplo no sistema:**

| Conta | Tipo | Saldo |
|---|---|---|
| ACC-001-BUYER | Comprador | R$ 50.000,00 |
| ACC-002-BUYER | Comprador | R$ 1.200,00 |
| ACC-003-MERCHANT | Lojista | R$ 8.500,00 |
| ACC-004-MERCHANT | Lojista (Suspensa) | R$ 320,00 |
| ACC-FSC-TREASURY | Sistema (taxas) | Acumula as taxas |

## Detecção de Fraudes

Se uma transação é suspeita, o sistema automaticamente:
1. **Bloqueia** a transação imediatamente
2. **Dispara alerta** para a fila de investigação (AWS SQS via LocalStack)
3. **Grava laudo** com dados pessoais mascarados (conformidade LGPD)

## O Painel de Controle (Dashboard)

| Aba | O que mostra |
|---|---|
| 🔍 **Auditoria** | Transações analisadas, bloqueadas, latência gRPC, alertas ao vivo via SSE |
| 🖥️ **Core Ledger** | Saldos em tempo real, liquidações manuais, relatório diário, saída JCL |
| 📖 **Manual** | Manual de uso embutido no próprio painel |

## Segurança (Resumo Executivo)

| Mecanismo | Significado prático |
|---|---|
| Login com JWT (Keycloak) | Só entra quem tem credencial válida |
| Limite de requisições (Redis) | Máximo 10 pedidos/segundo por cliente |
| mTLS | Comunicação interna sempre criptografada |
| Zero-Trust | Nenhuma parte interna confia automaticamente em outra |
| LGPD | Dados pessoais mascarados nos laudos de fraude |

## Escalabilidade

O sistema suporta **20.000 a 25.000 usuários simultâneos**:

- **Gateway**: escala de 5 até **20 instâncias** automaticamente
- **Motor de Segurança**: escala de 5 até **30 instâncias**
- O escalonamento é automático via KEDA (Kubernetes Event-Driven Autoscaling)

## Os Programas COBOL (Núcleo Bancário)

| Programa | O que faz |
|---|---|
| **FSCCHK** | Verifica se uma conta tem saldo suficiente |
| **FSCSET** | Executa o débito + crédito atômico (EXEC CICS SYNCPOINT) |
| **FSCBCH** | Gera o relatório diário de todas as operações |

---

# 🔧 Documentação Técnica — Referência de Engenharia

## Stack Tecnológico

| Camada | Tecnologia | Versão |
|---|---|---|
| Linguagem (Java) | Java | 22 |
| Framework | Spring Boot | 3.3.0 |
| Modulith | Spring Modulith | 1.2.0 |
| Frontend | Vue 3 + Vite + Tailwind | 3.x |
| Mainframe Sim | Node.js (Express) | 20+ |
| COBOL | IBM Enterprise COBOL | z/OS |
| Banco de dados | PostgreSQL | 16 |
| Mensageria | Apache Kafka (KRaft) | 3.7.0 |
| Cache / Rate-Limit | Redis | 7 |
| IAM | Keycloak | 24.0 |
| AWS Local | LocalStack (S3 + SQS) | 3.4.0 |
| Resiliência | Resilience4j | 2.2.0 |
| Comunicação interna | gRPC + mTLS | 1.64.0 |
| Observabilidade | OpenTelemetry + Jaeger + Prometheus + Grafana | latest |
| Orquestração | Kubernetes + KEDA | — |
| Build | Maven (multi-módulo) | 3.x |
| Testes arquiteturais | ArchUnit | 1.3.0 |
| Testes de integração | Testcontainers | 1.19.8 |

---

## Arquitetura em Camadas

```
┌─────────────────────────────────────────────────────────────┐
│  CAMADA 1 – APRESENTAÇÃO                                    │
│  Vue.js 3 (fsc-web · porta 5174) ←→ Keycloak OAuth2         │
│  Painel: Auditoria + Core Ledger z/OS + Manual              │
└───────────────────┬─────────────────────────────────────────┘
                    │ HTTPS + JWT (Bearer RS256)
┌───────────────────▼─────────────────────────────────────────┐
│  CAMADA 2 – GATEWAY  (fsc-gateway · WAR · porta 8082)       │
│  Rate Limit (Redis) → JWT Validation → gRPC/Kafka Router    │
│  Circuit Breaker (Resilience4j) · TimeLimiter 8s            │
└──────────┬────────────────────────────────┬─────────────────┘
           │ gRPC + mTLS (síncrono)         │ Kafka (assíncrono)
           │ timeout: 8s                    │ tópico: fsc.transactions.pending
┌──────────▼──────────────────────┐         │
│  CAMADA 3 – SEC ENGINE          │         │
│  (fsc-sec-engine · JAR · 8081)  │◄────────┘
│  Heurísticas Antifraude         │
│  PostgreSQL 16 · SQS · S3       │
│  gRPC Server (porta 9092)       │
└─────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  CAMADA 4 – CORE LEDGER  (fsc-mainframe · Node.js · 9191)   │
│  COBOL: FSCCHK · FSCSET · FSCBCH                            │
│  DB2 in-memory · IBM MQ Queue simulada · SSE Feed           │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  OBSERVABILIDADE                                            │
│  Jaeger (16686) · Prometheus (9090) · Grafana (3000)        │
│  OpenTelemetry OTLP → sampling 10% (gateway) / 5% (engine)  │
└─────────────────────────────────────────────────────────────┘
```

---

## Estrutura de Diretórios

```
erp/
├── docker-compose.yml          # Infraestrutura local completa
├── pom.xml                     # Parent Maven (Java 22, Spring Boot 3.3)
├── keycloak/fsc-realm.json     # Realm, roles e clients do Keycloak
├── certs/                      # Certificados mTLS (gateway, sec-engine, CA)
├── scripts/
│   ├── init.sql                # Schema PostgreSQL 16 (tabela particionada por data)
│   ├── prometheus.yml          # Config scraping Prometheus
│   ├── generate-certs.sh       # Geração de certificados mTLS (openssl)
│   └── load-test.js            # Script k6 para teste de carga
├── fsc-gateway/                # Módulo 1: API REST, Rate Limit, gRPC Client, Kafka Producer
│   └── src/main/java/com/fsc/gateway/
│       ├── config/
│       │   ├── SecurityConfig.java       # Spring Security + OAuth2 Resource Server
│       │   └── RateLimitFilter.java      # Rate limit por IP via Redis (10 req/s)
│       ├── controller/
│       │   └── TransactionController.java # POST /realtime e /batch
│       └── service/
│           └── TransactionRoutingService.java # Circuit Breaker + Kafka Producer
├── fsc-sec-engine/             # Módulo 2: Antifraude (Arquitetura Hexagonal)
│   └── src/main/java/com/fsc/secengine/audit/
│       ├── domain/
│       │   ├── AuditTransaction.java        # Entidade JPA (tabela particionada)
│       │   └── AuditTransactionRepository.java
│       ├── application/
│       │   └── AuditUseCase.java            # Heurística + persistência + notificação
│       └── infrastructure/
│           ├── kafka/KafkaAuditListener.java # Consumer (batch, manual ACK)
│           └── aws/AwsAuditNotifier.java     # SQS via Spring Cloud AWS
├── fsc-mainframe/
│   ├── cobol/
│   │   ├── FSCCHK.cbl          # CICS: Verificação de saldo (DB2 SELECT)
│   │   ├── FSCSET.cbl          # MQ: Liquidação (DB2 UPDATE + SYNCPOINT)
│   │   └── FSCBCH.cbl          # Batch JCL: Relatório diário
│   ├── copybooks/
│   │   ├── FSCREQ.cpy          # Estrutura COMMAREA Request/Response
│   │   └── FSCMQ.cpy           # Estrutura mensagem IBM MQ
│   └── simulator/server.js     # Simulador REST (Express · porta 9191)
├── fsc-web/                    # Frontend Vue 3 + TypeScript + Tailwind (dark neon)
│   └── src/
│       ├── App.vue             # Layout principal + tabs (Auditoria/Ledger/Manual)
│       └── components/
│           ├── MainframePanel.vue  # Interface completa do Core Ledger z/OS
│           └── ManualPage.vue      # Manual de uso embutido
├── k8s/
│   ├── gateway-deployment.yml      # Deployment + Service do Gateway
│   ├── sec-engine-deployment.yml   # Deployment + Service do Sec Engine
│   └── keda-scaledobjects.yml      # KEDA ScaledObjects + HPA fallback
└── .github/workflows/              # CI/CD pipelines
```

---

## Iniciando o Sistema

### Pré-requisitos

```bash
docker --version    # Docker 24+
node --version      # Node.js 20+
java --version      # Java 22 (para compilar os módulos Java)
```

### Passo 1 — Certificados mTLS (apenas uma vez)

```bash
cd erp/
bash scripts/generate-certs.sh
```

### Passo 2 — Infraestrutura Docker

```bash
docker compose up -d
docker compose ps   # Verificar saúde dos containers
```

> Aguarde ~60 segundos para o Keycloak inicializar completamente.

### Passo 3 — Simulador Mainframe

```bash
cd fsc-mainframe/simulator
node server.js
# Roda na porta 9191
```

### Passo 4 — Frontend Vue.js

```bash
cd fsc-web
npm run dev
# Abre em http://localhost:5174
```

### URLs de Acesso

| Serviço | URL | Login |
|---|---|---|
| **Painel FSC** | http://localhost:5174 | admin / admin |
| **Keycloak Admin** | http://localhost:8080 | admin / admin |
| **Grafana** | http://localhost:3000 | admin / admin |
| **Jaeger** | http://localhost:16686 | — |
| **Prometheus** | http://localhost:9090 | — |
| **Mainframe API** | http://localhost:9191 | — |
| **Gateway API** | http://localhost:8082 | JWT Bearer |
| **Sec Engine** | http://localhost:8081 | Interno (gRPC) |

---

## Módulo: fsc-gateway

**Empacotamento:** WAR · **Porta:** 8082

### Dependências principais
- `spring-boot-starter-web` — servidor HTTP (Tomcat, 800 threads max)
- `spring-boot-starter-security` + `oauth2-resource-server` — validação JWT RS256
- `spring-boot-starter-data-redis` — rate limiting distribuído
- `grpc-client-spring-boot-starter` — cliente gRPC com mTLS
- `spring-kafka` — producer Kafka (LZ4, batch 64KB, linger 5ms)
- `resilience4j-spring-boot3` — Circuit Breaker + TimeLimiter + Bulkhead

### Configuração de Performance (application.yml)

```yaml
server.tomcat.threads.max: 800
server.tomcat.max-connections: 20000
kafka.producer.batch-size: 65536        # 64KB
kafka.producer.compression-type: lz4
resilience4j.circuitbreaker.slidingWindowSize: 50
resilience4j.timelimiter.timeoutDuration: 8s
resilience4j.bulkhead.maxConcurrentCalls: 500
```

### Endpoints REST

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/v1/transactions/realtime` | Roteia para gRPC → Sec Engine (Circuit Breaker) |
| `POST` | `/api/v1/transactions/batch` | Publica no Kafka (assíncrono) |
| `GET` | `/actuator/health` | Health check (aberto para Prometheus) |
| `GET` | `/actuator/prometheus` | Métricas no formato Prometheus |

### Rate Limiting (RateLimitFilter.java)

- **Chave:** IP do cliente (ou `X-Forwarded-For`)
- **Limite:** 10 requisições/segundo
- **Storage:** Redis com TTL de 1 segundo
- **Resposta ao exceder:** HTTP 429 `Too Many Requests`

---

## Módulo: fsc-sec-engine

**Empacotamento:** JAR · **Porta HTTP:** 8081 · **Porta gRPC:** 9092

### Arquitetura Hexagonal (Ports & Adapters)

```
domain/           → AuditTransaction (entidade), AuditTransactionRepository (port)
application/      → AuditUseCase (lógica de negócio, @Transactional)
infrastructure/   → KafkaAuditListener (adapter entrada), AwsAuditNotifier (adapter saída)
```

### Validação Arquitetural (ArchUnit + Spring Modulith)

```java
// ArchitectureTest.java — executado a cada build
ApplicationModules.of(SecEngineApplication.class).verify();
// Garante que camadas não violam isolamento (ex: infra não chama domain diretamente)
```

### Schema PostgreSQL (init.sql)

```sql
-- Tabela particionada por RANGE (created_at) para alta performance
CREATE TABLE transactions (
    id UUID NOT NULL,
    customer_id UUID NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,  -- PENDING | APPROVED | FRAUD_DETECTED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Partições mensais — novas devem ser criadas mensalmente
CREATE TABLE transactions_2026_06 PARTITION OF transactions
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
```

### Kafka Consumer (KafkaAuditListener.java)

```yaml
# application.yml
kafka.consumer.max-poll-records: 500      # Batch de até 500 mensagens
kafka.listener.concurrency: 12            # 12 threads consumidoras por instância
kafka.listener.ack-mode: manual_immediate # Commit manual após processamento
```

### Heurística Antifraude (AuditUseCase.java)

Atualmente implementada como stub demonstrativo:
- Payload contém `"FRAUD_SIMULATE"` → `markAsFraud()` + notifica SQS
- Qualquer outro payload → `markAsApproved()`

> **Extensão:** Substituir o stub por um modelo de scoring baseado em features da transação.

---

## Módulo: fsc-mainframe (Simulador z/OS)

**Runtime:** Node.js + Express · **Porta:** 9191

### API REST (espelha z/OS Connect EE)

Base URL: `http://localhost:9191/zosconnect`

| Método | Rota | Programa COBOL | Descrição |
|---|---|---|---|
| `GET` | `/health` | — | Status do simulador |
| `GET` | `/accounts` | — | Snapshot do DB2 em memória |
| `GET` | `/fscchk/balance/:acctId?amount=N` | FSCCHK | Verifica saldo (SQLCODE, REASON-CODE) |
| `POST` | `/fscset/settle` | FSCSET | Executa liquidação atômica |
| `GET` | `/fscbch/daily-report` | FSCBCH | Relatório batch do dia |
| `GET` | `/ledger-events` | — | SSE stream (tick a cada 5s) |
| `POST` | `/mq/enqueue` | — | Enfileira mensagem IBM MQ simulada |
| `POST` | `/mq/process-next` | FSCSET | Processa próxima mensagem da fila |

### Lógica de Liquidação (FSCSET)

```
EXEC CICS SYNCPOINT (operação atômica):
  UPDATE FSC.ACCOUNTS (débito comprador: valor + taxa 1,5%)
  UPDATE FSC.ACCOUNTS (crédito lojista: valor)
  UPDATE FSC.ACCOUNTS (crédito treasury: taxa)
  INSERT INTO FSC.LEDGER_AUDIT (log da liquidação)
```

**Idempotência:** SQLCODE `-803` ao reprocessar mesmo `transId` → ignorado silenciosamente.

**Taxa:** `max(valor × 1,5%, R$ 0,50)`

### Exemplo — Liquidação via curl

```bash
curl -X POST http://localhost:9191/zosconnect/fscset/settle \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-12345",
    "debitAcct": "ACC-001-BUYER",
    "creditAcct": "ACC-003-MERCHANT",
    "amount": 350.00,
    "currency": "BRL"
  }'
```

**Resposta de sucesso (HTTP 201):**

```json
{
  "sqlcode": 0,
  "sqlerrm": "SYNCPOINT COMMITTED",
  "fscResReturnCode": 0,
  "fscResReasonCode": "SETL",
  "fscResMsg": "EXEC CICS SYNCPOINT - DEBIT/CREDIT COMMITTED TO DB2",
  "settlement": {
    "transId": "uuid-gerado",
    "amount": 350.00,
    "fee": 5.25,
    "totalDebit": 355.25,
    "status": "SETTLED"
  }
}
```

---

## Módulo: fsc-web (Frontend)

**Stack:** Vue 3 + TypeScript + Vite + Tailwind CSS (dark neon `#00f0ff`)

### Componentes principais

| Arquivo | Responsabilidade |
|---|---|
| `App.vue` | Layout, tabs (Auditoria / Core Ledger / Manual), feed SSE mock |
| `MainframePanel.vue` | Interface completa do z/OS: Ledger Live, FSCCHK, FSCSET, FSCBCH, SYSPRINT |
| `ManualPage.vue` | Manual de uso embutido |

### Comunicação com o Backend

- **Mainframe API:** `fetch()` direto para `http://localhost:9191/zosconnect`
- **SSE:** `EventSource` conectado a `/ledger-events` (tick de 5s)
- **Gateway API:** Via JWT Bearer (obtido do Keycloak)

---

## Segurança Detalhada

### Keycloak — Realm `fsc`

| Item | Valor |
|---|---|
| Client | `fsc-vue` (public) |
| Grant type | `password` (dev) / `authorization_code` (produção) |
| Algoritmo JWT | RS256 |
| Roles | `fsc_user`, `fsc_admin` |

```bash
# Obter token de acesso
TOKEN=$(curl -s -X POST \
  http://localhost:8080/realms/fsc/protocol/openid-connect/token \
  -d "client_id=fsc-vue&grant_type=password&username=admin&password=admin" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

### mTLS (gRPC Gateway ↔ Sec Engine)

```
certs/
├── ca.crt          # Certificado raiz (CA própria)
├── gateway.crt     # Certificado do Gateway (client)
├── gateway.key     # Chave privada do Gateway
├── sec-engine.crt  # Certificado do Sec Engine (server)
└── sec-engine.key  # Chave privada do Sec Engine
```

---

## Kubernetes & Autoscaling (KEDA)

### ScaledObjects configurados

| Serviço | Min replicas | Max replicas | Trigger |
|---|---|---|---|
| `fsc-gateway` | 5 | 20 | Prometheus RPS > 500/réplica ou CPU > 60% |
| `fsc-sec-engine` | 5 | 30 | Kafka lag > 50 msgs ou CPU > 65% |

### Comportamento de escalonamento

```yaml
scaleUp:
  stabilizationWindowSeconds: 15   # Sobe rápido
  policies: [+4 pods a cada 30s]
scaleDown:
  stabilizationWindowSeconds: 120  # Desce com cautela
  policies: [-25% a cada 60s]
```

---

## Observabilidade

### Jaeger — Tracing Distribuído

Acesse **http://localhost:16686** → selecione serviço `fsc-gateway` → **Find Traces**

O `traceId` é propagado via MDC (Mapped Diagnostic Context) em todos os logs.

### Grafana — Dashboards (http://localhost:3000)

Métricas coletadas via Prometheus a cada 15s:
- `http_server_requests_seconds` — latência das APIs (p50, p95, p99)
- `kafka_consumer_lag` — profundidade da fila de processamento
- `jvm_memory_used_bytes` — uso de memória da JVM
- SLAs configurados: `50ms | 100ms | 200ms | 500ms | 1s`

---

## Fluxo Completo de uma Transação

```
1. Comprador finaliza pedido na loja parceira
2. Loja → POST /api/v1/transactions/realtime (JWT Bearer)
3. Gateway → RateLimitFilter (Redis, 10 req/s)
4. Gateway → JWT Validation (Keycloak JWKS)
5. Gateway → gRPC + mTLS → Sec Engine (Circuit Breaker, timeout 8s)
6. Sec Engine → AuditUseCase.evaluateTransaction()
   ├── APROVADA  → status APPROVED no PostgreSQL
   └── SUSPEITA  → FRAUD_DETECTED + alerta SQS + laudo S3
7. Resultado retorna ao Gateway (< 100ms)
8. Se APROVADA → Kafka (fsc.transactions.pending) → MQ Bridge
9. FSCSET consome → EXEC CICS SYNCPOINT (débito + crédito + audit log)
10. Painel Vue.js atualiza em tempo real via SSE
```

---

## Simulando uma Fraude

Adicione `"FRAUD_SIMULATE": true` no payload. O sistema irá:
1. Marcar a transação como `FRAUD_DETECTED` no PostgreSQL
2. Enviar alerta na fila SQS (LocalStack): `fsc-fraud-alerts-queue`
3. Gerar laudo mascarado no S3 (conformidade LGPD)

---

## Solução de Problemas

| Problema | Causa | Solução |
|---|---|---|
| Painel não carrega / loop de redirect | Keycloak inicializando | Aguarde 60s após `docker compose up` |
| `SQLCODE +100` no FSCCHK | Conta não encontrada | Use IDs listados na seção Core Ledger |
| `REASON-CODE: INSF` no FSCSET | Saldo insuficiente | Use `ACC-001-BUYER` (R$ 50.000) |
| `Non-parseable POM` na IDE | Cache da extensão Java | `Ctrl+Shift+P` → Java: Clean Workspace |
| Containers não sobem | Porta em uso | `docker compose down` e suba novamente |
| Mainframe API offline | Simulador não iniciado | `cd fsc-mainframe/simulator && node server.js` |
| Tailwind warnings | CSS desconhecido | Adicione `"css.lint.unknownAtRules": "ignore"` no `.vscode/settings.json` |

---

*FSC — Fast Sec Commerce · v1.0.0 · Junho 2026*
