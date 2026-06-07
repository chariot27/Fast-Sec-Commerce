# Plano de Implementação: Fast Sec Commerce (FSC)

Este plano estrutura o desenvolvimento do motor de auditoria e processamento seguro (FSC) utilizando a arquitetura "Distributed Modulith" e as rigorosas regras de engenharia estabelecidas.

## Fase 1: Infraestrutura e Configuração Base (Foundation)
*Objetivo: Estabelecer os alicerces locais para desenvolvimento, incluindo banco de dados, mensageria e ferramentas de nuvem.*

- [ ] **1.1. Orquestração Local:** Criar o `docker-compose.yml` contendo PostgreSQL 16, Redis, Keycloak, Apache Kafka (com Zookeeper/KRaft) e LocalStack (simulando AWS SQS e S3).
- [ ] **1.2. Observabilidade Base:** Adicionar Jaeger, Prometheus e Grafana no compose.
- [ ] **1.3. Banco de Dados:** Configurar scripts de inicialização do PostgreSQL, estabelecendo as políticas de particionamento e suporte a UUIDv7.
- [ ] **1.4. Testcontainers:** Configurar a infraestrutura de testes de integração genérica no projeto pai para que ambos os nós (Gateway e Sec Engine) possam herdar.

## Fase 2: Segurança, Identidade e Observabilidade (Core Components)
*Objetivo: Garantir a Zero-Trust Architecture e a rastreabilidade total antes de codificar regras de negócio.*

- [ ] **2.1. Configuração do Keycloak:** Configurar Realm, Clients (para Vue e Gateway), roles e geração de token JWT (RS256).
- [ ] **2.2. mTLS Interno:** Gerar certificados e configurar a comunicação mutuamente autenticada entre Gateway e Sec Engine.
- [ ] **2.3. Tracing Distribuído:** Configurar as dependências do OpenTelemetry para propagação automática de `traceId` (MDC/Logback) em chamadas HTTP, gRPC e Kafka.

## Fase 3: Gateway Node (fsc-gateway)
*Objetivo: Construir a porta de entrada pública do sistema, focada em validação, rate limiting e roteamento.*

- [ ] **3.1. Setup do Projeto:** Iniciar projeto Spring Boot encapsulado como `.war` para deploy em servidor WildFly 30+.
- [ ] **3.2. Segurança e Rate Limiting:** Implementar validação do JWT via Keycloak e configurar o limitador de chamadas utilizando Redis.
- [ ] **3.3. API REST e Resiliência:** Expor endpoints para o frontend. Implementar Circuit Breaker e TimeLimiter (Resilience4j) para proteger as rotas internas.
- [ ] **3.4. Roteamento (gRPC e Kafka):** 
  - Criar o gRPC Client para comunicação síncrona (tempo real) com o Sec Engine.
  - Implementar o Kafka Producer publicando eventos de lote no tópico `fsc.transactions.pending` (usando CloudEvents).

## Fase 4: Sec Engine Node (fsc-sec-engine)
*Objetivo: O coração da auditoria. Operações de backend críticas sem exposição externa direta.*

- [ ] **4.1. Arquitetura:** Iniciar projeto Spring Boot standalone (`.jar`). Estruturar pacotes utilizando Arquitetura Hexagonal (Ports and Adapters) com Spring Modulith.
- [ ] **4.2. Barreiras de Arquitetura:** Implementar testes de `ArchUnit` para garantir que módulos não acessem repositórios uns dos outros (comunicação via `ApplicationEventPublisher`).
- [ ] **4.3. Servidor gRPC:** Implementar o gRPC Server para receber intenções de compra em tempo real do Gateway.
- [ ] **4.4. Consumo Assíncrono:** Criar o Kafka Listener para processamento em lote, garantindo propagação de traces e configuração de Dead Letter Queues (DLQ).
- [ ] **4.5. Persistência e Regras de Negócio:** Implementar a lógica de auditoria/heurística antifraude e persistência no PostgreSQL 16.
- [ ] **4.6. Integração AWS:** Desenvolver os adapters para envio de alertas na fila (SQS) e persistência de laudos mascarados (S3) via LocalStack.

## Fase 5: Frontend Vue.js (fsc-web)
*Objetivo: Desenvolver o painel tático com design premium e conectividade em tempo real.*

- [ ] **5.1. Scaffolding:** Inicializar projeto Vue 3 com Vite, Pinia, TypeScript rigoroso e Tailwind CSS.
- [ ] **5.2. Design System:** Implementar a paleta "Dark mode absolute + Neon Blue accents".
- [ ] **5.3. Integração em Tempo Real:** Criar client SSE (Server-Sent Events) ou WebSockets para visualização de alertas e transações auditadas em tempo real.
- [ ] **5.4. Autenticação:** Implementar fluxo de login integrado com o Keycloak.

## Fase 6: Deploy, CI/CD e Autoscaling
*Objetivo: Preparar o sistema para produção e escalabilidade automatizada.*

- [ ] **6.1. CI/CD:** Desenvolver workflows de GitHub Actions para build, execução de testes (com Testcontainers) e análise estática de código e arquitetura.
- [ ] **6.2. KEDA (Autoscaling):** Desenvolver manifests do Kubernetes para escalonamento automático baseado nas métricas do Kafka (Consumer Lag) ou CPU.
