<script setup lang="ts">
const sections = [
  {
    id: 'intro', label: 'Visao Geral', title: 'O que e o FSC?',
    content: `O <strong>Fast Sec Commerce (FSC)</strong> e uma plataforma de auditoria antifraude e liquidacao financeira
              para e-commerce de alta performance. Atua como middleware seguro entre a loja parceira e o gateway de
              pagamento, verificando cada transacao antes de sua liquidacao final no Core Ledger.`
  },
  {
    id: 'arch', label: 'Arquitetura', title: 'Arquitetura do Sistema',
    content: `O FSC opera em 4 camadas:<br><br>
    <strong>Camada 1 — Frontend:</strong> Vue.js 3 com painel tatical em tempo real (SSE + REST). Sem estado persistente no cliente.<br>
    <strong>Camada 2 — Gateway:</strong> Spring Boot 3 / Java 22. API REST com OAuth2 JWT (Keycloak), Rate Limit Redis (10 req/s), Circuit Breaker Resilience4j, roteamento gRPC+mTLS para Sec Engine e Kafka para batch.<br>
    <strong>Camada 3 — Sec Engine:</strong> Spring Modulith. Heuristicas antifraude, persistencia PostgreSQL, alertas SQS, laudos S3 (dados mascarados — LGPD).<br>
    <strong>Camada 4 — Core Ledger:</strong> Simulador Node.js de Mainframe IBM z/OS. Programas COBOL FSCCHK (saldo), FSCSET (liquidacao), FSCBCH (relatorio batch). DB2 em memoria.`
  },
  {
    id: 'auth', label: 'Autenticacao', title: 'Autenticacao OAuth2 / JWT',
    content: `Todas as rotas sensíveis do sistema exigem <strong>JWT Bearer RS256</strong> emitido pelo Keycloak.<br><br>
    <strong>Como obter o token:</strong><br>
    Realm: <code>fsc</code> — Client: <code>fsc-gateway</code><br><br>
    <strong>Roles disponíveis:</strong><br>
    <code>fsc_user</code> — Envio de transacoes e consulta de saldo.<br>
    <code>fsc_admin</code> — Acesso completo incluindo avaliacao manual no Sec Engine.<br><br>
    <strong>Configurar token no painel:</strong><br>
    Abra o console do navegador e execute: <code>localStorage.setItem('fsc_token', 'SEU_TOKEN_AQUI')</code>`
  },
  {
    id: 'start', label: 'Inicializacao', title: 'Iniciando o Sistema',
    steps: [
      { cmd: 'bash scripts/generate-certs.sh', desc: 'Gera certificados mTLS para comunicacao interna (apenas uma vez)' },
      { cmd: 'docker compose up -d', desc: 'Sobe infraestrutura: PostgreSQL, Redis, Kafka, Keycloak, LocalStack, Jaeger, Prometheus, Grafana' },
      { cmd: 'cd fsc-mainframe/simulator && node server.js', desc: 'Inicia simulador do Mainframe na porta 9191' },
      { cmd: 'cd fsc-gateway && mvn spring-boot:run', desc: 'Inicia o Gateway na porta 8082' },
      { cmd: 'cd fsc-sec-engine && mvn spring-boot:run', desc: 'Inicia o Sec Engine na porta 8083' },
      { cmd: 'cd fsc-web && npm run dev', desc: 'Inicia o painel de controle na porta 5173' },
    ]
  },
  {
    id: 'panel-audit', label: 'Auditoria', title: 'Painel — Auditoria Antifraude',
    content: `A aba de <strong>Auditoria</strong> monitora em tempo real via SSE:<br><br>
    - <strong>Total Auditadas:</strong> transacoes avaliadas pelo Sec Engine no dia.<br>
    - <strong>Bloqueadas:</strong> identificadas como fraude pelas heuristicas.<br>
    - <strong>Latencia gRPC:</strong> tempo medio de resposta da rota sincrona Gateway &rarr; Sec Engine.<br>
    - <strong>Feed SSE:</strong> eventos ao vivo de aprovacao ou bloqueio de transacoes.`
  },
  {
    id: 'panel-mainframe', label: 'Core Ledger', title: 'Painel — Core Ledger (z/OS)',
    subsections: [
      { name: 'Ledger Live', desc: 'Snapshot ao vivo do DB2 em memoria com saldos de todas as contas. Atualiza via SSE a cada 5 segundos.' },
      { name: 'FSCCHK — Saldo', desc: 'Selecione uma conta e um valor para verificar saldo disponivel. Retorna SQLCODE, REASON-CODE e disponibilidade. Requer autenticacao JWT.' },
      { name: 'FSCSET — Liquidar', desc: 'Executa debito/credito atomico entre contas. Simula EXEC CICS SYNCPOINT com calculo automatico de taxa de 1,5%. Requer autenticacao JWT.' },
      { name: 'FSCBCH — Relatorio', desc: 'Consolidado diario: total de transacoes, volume, taxas coletadas e saldo do treasury. Tabela da LEDGER_AUDIT. Requer autenticacao JWT.' },
      { name: 'SYSPRINT JCL', desc: 'Output de impressora que o programa COBOL FSCBCH geraria no z/OS real. Mostra o relatorio formatado em estilo mainframe.' },
    ]
  },
  {
    id: 'flow', label: 'Fluxo', title: 'Fluxo Completo de uma Transacao',
    flow: [
      'Comprador finaliza pedido na loja parceira',
      'Sistema parceiro envia POST /api/v1/transactions/realtime com Bearer JWT (Keycloak)',
      'Gateway valida JWT via JWKS do Keycloak (RS256) e aplica Rate Limit (Redis, 10 req/s)',
      'Gateway chama Sec Engine via gRPC + mTLS (Circuit Breaker e Time Limiter ativos)',
      'Sec Engine avalia heuristicas antifraude — persiste resultado no PostgreSQL',
      'Se APROVADA: publica evento no Kafka (fsc.transactions.pending)',
      'Bridge Kafka → IBM MQ: FSCSET consome da fila e executa EXEC CICS SYNCPOINT (debito + credito + log)',
      'Resultado gravado em FSC.LEDGER_AUDIT. Painel atualiza via SSE em tempo real.',
      'Se FRAUDE: notificacao emitida ao SQS. Laudo mascarado salvo no S3. Alerta visivel no painel.',
    ]
  },
  {
    id: 'accounts', label: 'Contas', title: 'Contas do Core Ledger',
    accounts: [
      { id: 'ACC-001-BUYER', type: 'BUYER', balance: 'R$ 50.000,00', owner: 'Joao Silva' },
      { id: 'ACC-002-BUYER', type: 'BUYER', balance: 'R$ 1.200,00', owner: 'Maria Souza' },
      { id: 'ACC-003-MERCHANT', type: 'MERCHANT', balance: 'R$ 8.500,00', owner: 'Loja FastTech' },
      { id: 'ACC-004-MERCHANT', type: 'MERCHANT', balance: 'R$ 320,00', owner: 'Loja XYZ (Suspensa)' },
      { id: 'ACC-FSC-TREASURY', type: 'SYSTEM', balance: 'R$ 0,00', owner: 'FSC Fee Treasury' },
    ]
  },
  {
    id: 'api', label: 'API Reference', title: 'API Reference Completa',
    apis: [
      // Gateway
      { service: 'Gateway (8082)', method: 'POST', route: '/api/v1/transactions/realtime', auth: 'JWT', desc: 'Transacao em tempo real via gRPC + Circuit Breaker. Fallback automatico para Kafka.' },
      { service: 'Gateway (8082)', method: 'POST', route: '/api/v1/transactions/batch',    auth: 'JWT', desc: 'Enfileira transacao no Kafka. HTTP 202 Accepted imediato.' },
      { service: 'Gateway (8082)', method: 'GET',  route: '/actuator/health',              auth: 'Pub', desc: 'Health check para Prometheus e Docker healthcheck.' },
      { service: 'Gateway (8082)', method: 'GET',  route: '/v3/api-docs',                  auth: 'Pub', desc: 'OpenAPI 3 JSON spec.' },
      { service: 'Gateway (8082)', method: 'GET',  route: '/swagger-ui/index.html',        auth: 'Pub', desc: 'Swagger UI interativo.' },
      // Sec Engine
      { service: 'Sec Engine (8083)', method: 'POST', route: '/api/v1/audit/evaluate',    auth: 'JWT', desc: 'Avaliacao manual de transacao pelo motor antifraude.' },
      { service: 'Sec Engine (8083)', method: 'GET',  route: '/actuator/health',           auth: 'Pub', desc: 'Health check do Sec Engine.' },
      { service: 'Sec Engine (8083)', method: 'GET',  route: '/v3/api-docs',               auth: 'Pub', desc: 'OpenAPI 3 JSON spec do Sec Engine.' },
      // Mainframe
      { service: 'Mainframe (9191)', method: 'GET',  route: '/zosconnect/health',              auth: 'Pub', desc: 'Status do simulador z/OS.' },
      { service: 'Mainframe (9191)', method: 'GET',  route: '/zosconnect/accounts',            auth: 'JWT', desc: 'DB2 snapshot: lista todas as contas.' },
      { service: 'Mainframe (9191)', method: 'GET',  route: '/zosconnect/fscchk/balance/:id', auth: 'JWT', desc: 'FSCCHK: verificar saldo e disponibilidade.' },
      { service: 'Mainframe (9191)', method: 'POST', route: '/zosconnect/fscset/settle',       auth: 'JWT', desc: 'FSCSET: liquidacao atomica CICS SYNCPOINT.' },
      { service: 'Mainframe (9191)', method: 'GET',  route: '/zosconnect/fscbch/daily-report', auth: 'JWT', desc: 'FSCBCH: relatorio batch diario consolidado.' },
      { service: 'Mainframe (9191)', method: 'GET',  route: '/zosconnect/ledger-events',       auth: 'JWT', desc: 'SSE stream: eventos do ledger a cada 5s.' },
      { service: 'Mainframe (9191)', method: 'POST', route: '/zosconnect/mq/enqueue',           auth: 'JWT', desc: 'Enfileira mensagem na fila IBM MQ simulada.' },
      { service: 'Mainframe (9191)', method: 'POST', route: '/zosconnect/mq/process-next',      auth: 'JWT', desc: 'Processa proxima mensagem da fila MQ.' },
    ]
  },
  {
    id: 'security', label: 'Seguranca', title: 'Controles de Seguranca',
    items: [
      { label: 'Keycloak OAuth2 / JWT', desc: 'Login com JWT RS256. Roles: fsc_user e fsc_admin. JWKS endpoint para validacao de assinatura.' },
      { label: 'Rate Limit Redis', desc: 'Maximo 10 req/s por IP. HTTP 429 se excedido. Implementado via RateLimitFilter (OncePerRequestFilter).' },
      { label: 'mTLS gRPC', desc: 'Comunicacao interna Gateway e SecEngine com autenticacao mutua de certificados (certs/ gerados por scripts/generate-certs.sh).' },
      { label: 'Zero-Trust', desc: 'anyRequest().authenticated() no Gateway. Nenhuma rota anonima alem de health, Swagger e Prometheus.' },
      { label: 'Input Sanitization', desc: 'Mainframe server.js sanitiza todos os inputs removendo caracteres HTML/script e limitando tamanho de campos.' },
      { label: 'Headers HTTP Seguranca', desc: 'HSTS, X-Frame-Options: DENY, CSP: default-src self, Referrer-Policy: no-referrer, Permissions-Policy.' },
      { label: 'Idempotencia', desc: 'FSCSET verifica duplicate transId antes de executar SYNCPOINT. Retorna HTTP 200 com DUPL para reprocessamentos.' },
      { label: 'LGPD', desc: 'Laudos de fraude salvos no S3 com dados pessoais mascarados. Audit trail completo no PostgreSQL.' },
      { label: 'Circuit Breaker', desc: 'Resilience4j protege o Gateway de falhas em cascata no Sec Engine (janela 50 calls, threshold 60%).' },
      { label: 'Credenciais por env', desc: 'Todas as senhas e chaves em variaveis de ambiente (.env). Nunca hardcoded. .env listado no .gitignore.' },
    ]
  },
  {
    id: 'observability', label: 'Observabilidade', title: 'Observabilidade e Monitoramento',
    items: [
      { label: 'Prometheus', desc: 'Scraping de metricas via /actuator/prometheus. Disponivel em http://localhost:9090.' },
      { label: 'Grafana', desc: 'Dashboards pre-configurados em http://localhost:3000. Usuario/senha definidos no .env.' },
      { label: 'Jaeger (OpenTelemetry)', desc: 'Tracing distribuido em http://localhost:16686. Sampling de 10% em producao.' },
      { label: 'Spring Actuator', desc: 'Endpoints: /health, /prometheus, /info, /metrics expostos para monitoramento.' },
    ]
  },
  {
    id: 'troubleshoot', label: 'Problemas', title: 'Solucao de Problemas',
    issues: [
      { problem: 'Loop de redirecionamento no login', solution: 'Aguarde 60s apos docker compose up para o Keycloak inicializar completamente.' },
      { problem: 'SQLCODE +100 no FSCCHK', solution: 'Use um dos Account IDs da secao "Contas do Core Ledger" (ex: ACC-001-BUYER).' },
      { problem: 'REASON-CODE INSF no FSCSET', solution: 'Use ACC-001-BUYER como debito (saldo inicial R$ 50.000). Outros compradores podem ter saldo insuficiente.' },
      { problem: 'Mainframe API offline (contas nao carregam)', solution: 'Execute: cd fsc-mainframe/simulator && node server.js' },
      { problem: 'HTTP 401 Unauthorized no Core Ledger', solution: 'Configure o token no navegador: localStorage.setItem(\'fsc_token\', \'SEU_JWT\')' },
      { problem: 'Kafka timeout ao publicar transacao batch', solution: 'Verifique se o container fsc-kafka esta rodando: docker ps | grep kafka' },
      { problem: 'Redis connection refused', solution: 'Verifique REDIS_PASSWORD no .env e se o container fsc-redis esta saudavel: docker ps' },
      { problem: 'Warnings Tailwind na IDE', solution: 'Adicione "css.lint.unknownAtRules": "ignore" no .vscode/settings.json' },
    ]
  },
]
</script>

<template>
  <div class="text-white">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h2 class="text-lg font-semibold text-white">Manual de Uso</h2>
        <p class="text-xs text-gray-500 mt-0.5">Documentacao completa do sistema FSC</p>
      </div>
      <span class="text-[10px] text-gray-600 border border-white/5 rounded px-2 py-1">v1.0.0</span>
    </div>

    <!-- Indice rapido -->
    <div class="flex flex-wrap gap-2 mb-8">
      <a v-for="s in sections" :key="s.id" :href="`#${s.id}`"
         class="text-xs text-gray-400 hover:text-[#00f0ff] border border-white/5 hover:border-[#00f0ff]/30 rounded-lg px-3 py-1.5 transition-all">
        {{ s.label }}
      </a>
    </div>

    <div class="space-y-6">

      <template v-for="s in sections" :key="s.id">
        <section :id="s.id" class="bg-[#0d0d0d] border border-white/5 rounded-xl p-6 scroll-mt-20">
          <h3 class="text-sm font-semibold text-white mb-4">{{ s.title }}</h3>

          <!-- Content HTML -->
          <p v-if="s.content" class="text-sm text-gray-400 leading-7" v-html="s.content"></p>

          <!-- Steps (comandos) -->
          <div v-if="s.steps" class="space-y-3">
            <div v-for="(step, i) in s.steps" :key="i" class="flex items-start gap-4">
              <span class="w-6 h-6 rounded-full bg-[#00f0ff]/10 border border-[#00f0ff]/20 text-[#00f0ff] text-xs flex items-center justify-center shrink-0 mt-0.5">{{ i+1 }}</span>
              <div>
                <code class="text-xs bg-[#0a0a0a] border border-white/5 rounded px-3 py-1.5 text-[#00f0ff] font-mono block mb-1">{{ step.cmd }}</code>
                <p class="text-xs text-gray-500">{{ step.desc }}</p>
              </div>
            </div>
          </div>

          <!-- Subsections -->
          <div v-if="s.subsections" class="space-y-3">
            <div v-for="sub in s.subsections" :key="sub.name" class="flex gap-3 bg-[#0a0a0a] border border-white/5 rounded-lg p-4">
              <span class="text-xs font-medium text-white shrink-0 w-48">{{ sub.name }}</span>
              <p class="text-xs text-gray-500 leading-6">{{ sub.desc }}</p>
            </div>
          </div>

          <!-- Flow -->
          <ol v-if="s.flow" class="space-y-2">
            <li v-for="(step, i) in s.flow" :key="i" class="flex items-start gap-3 text-sm text-gray-400">
              <span class="text-[#00f0ff] font-mono text-xs mt-0.5 shrink-0">{{ String(i+1).padStart(2,'0') }}</span>
              {{ step }}
            </li>
          </ol>

          <!-- Accounts table -->
          <div v-if="s.accounts" class="overflow-x-auto">
            <table class="w-full text-xs">
              <thead><tr class="text-gray-600 border-b border-white/5">
                <th class="text-left pb-2 font-normal">Account ID</th>
                <th class="text-left pb-2 font-normal">Tipo</th>
                <th class="text-right pb-2 font-normal">Saldo Inicial</th>
                <th class="text-left pb-2 font-normal">Proprietario</th>
              </tr></thead>
              <tbody class="divide-y divide-white/5">
                <tr v-for="a in s.accounts" :key="a.id" class="text-gray-400">
                  <td class="py-2 font-mono text-[#00f0ff] text-[10px]">{{ a.id }}</td>
                  <td class="py-2"><span class="border border-white/10 rounded px-1.5 text-[10px]">{{ a.type }}</span></td>
                  <td class="py-2 text-right text-white">{{ a.balance }}</td>
                  <td class="py-2">{{ a.owner }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- APIs table -->
          <div v-if="s.apis" class="overflow-x-auto">
            <table class="w-full text-xs">
              <thead><tr class="text-gray-600 border-b border-white/5">
                <th class="text-left pb-2 font-normal">Servico</th>
                <th class="text-left pb-2 font-normal w-16">Metodo</th>
                <th class="text-left pb-2 font-normal">Rota</th>
                <th class="text-left pb-2 font-normal">Auth</th>
                <th class="text-left pb-2 font-normal">Descricao</th>
              </tr></thead>
              <tbody class="divide-y divide-white/5">
                <tr v-for="a in s.apis" :key="a.service + a.route" class="text-gray-400">
                  <td class="py-2 text-gray-500 text-[10px] font-mono whitespace-nowrap">{{ a.service }}</td>
                  <td class="py-2">
                    <span :class="{
                      'text-green-400 border-green-400/20': a.method === 'GET',
                      'text-[#00f0ff] border-[#00f0ff]/20': a.method === 'POST',
                    }" class="border rounded px-1.5 font-mono text-[10px]">{{ a.method }}</span>
                  </td>
                  <td class="py-2 font-mono text-[10px] text-gray-300">{{ a.route }}</td>
                  <td class="py-2">
                    <span :class="a.auth === 'JWT' ? 'text-[#00f0ff] border-[#00f0ff]/20' : 'text-gray-600 border-white/10'"
                          class="border rounded px-1.5 text-[10px]">{{ a.auth }}</span>
                  </td>
                  <td class="py-2">{{ a.desc }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Security / Observability items -->
          <div v-if="s.items" class="space-y-2">
            <div v-for="item in s.items" :key="item.label" class="flex gap-4 bg-[#0a0a0a] border border-white/5 rounded-lg px-4 py-3 text-xs">
              <span class="text-white font-medium w-48 shrink-0">{{ item.label }}</span>
              <span class="text-gray-500">{{ item.desc }}</span>
            </div>
          </div>

          <!-- Troubleshoot -->
          <div v-if="s.issues" class="space-y-2">
            <div v-for="item in s.issues" :key="item.problem" class="bg-[#0a0a0a] border border-white/5 rounded-lg px-4 py-3 text-xs">
              <p class="text-yellow-400/80 mb-1 font-medium">{{ item.problem }}</p>
              <p class="text-gray-400">{{ item.solution }}</p>
            </div>
          </div>

        </section>
      </template>

    </div>
  </div>
</template>
