<script setup lang="ts">
const sections = [
  {
    id: 'intro', icon: '🚀', title: 'O que é o FSC?',
    content: `O <strong>Fast Sec Commerce (FSC)</strong> é uma plataforma de auditoria antifraude e liquidação financeira para e-commerce. Atua como middleware de alta performance entre a loja e o gateway de pagamento, verificando cada transação antes que seja processada.`
  },
  {
    id: 'arch', icon: '🏗️', title: 'Arquitetura',
    content: `O FSC opera em 4 camadas:<br><br>
    <strong>Camada 1 – Frontend:</strong> Vue.js 3 com painel tático em tempo real.<br>
    <strong>Camada 2 – Gateway Node:</strong> API REST, Rate Limit Redis, Circuit Breaker, roteamento gRPC e Kafka.<br>
    <strong>Camada 3 – Sec Engine:</strong> Heurísticas antifraude, persistência no PostgreSQL, alertas SQS e laudos S3.<br>
    <strong>Camada 4 – Core Ledger:</strong> Simulador de Mainframe IBM z/OS com programas COBOL FSCCHK, FSCSET e FSCBCH.`
  },
  {
    id: 'start', icon: '⚡', title: 'Iniciando o Sistema',
    steps: [
      { cmd: 'bash scripts/generate-certs.sh', desc: 'Gera certificados mTLS (apenas uma vez)' },
      { cmd: 'docker compose up -d', desc: 'Sobe toda a infraestrutura (PG, Redis, Kafka, Keycloak...)' },
      { cmd: 'cd fsc-mainframe/simulator && node server.js', desc: 'Inicia simulador do Mainframe na porta 9191' },
      { cmd: 'cd fsc-web && npm run dev', desc: 'Inicia o painel na porta 5173/5174' },
    ]
  },
  {
    id: 'panel-audit', icon: '🔍', title: 'Painel – Auditoria Antifraude',
    content: `A aba de <strong>Auditoria</strong> monitora em tempo real:<br><br>
    • <strong>Total Auditadas:</strong> transações avaliadas pelo Sec Engine no dia.<br>
    • <strong>Bloqueadas:</strong> identificadas como fraude.<br>
    • <strong>Latência gRPC:</strong> tempo médio de resposta da rota síncrona.<br>
    • <strong>Feed SSE:</strong> eventos ao vivo de aprovação ou bloqueio de transações.`
  },
  {
    id: 'panel-mainframe', icon: '🖥️', title: 'Painel – Core Ledger (z/OS)',
    subsections: [
      { name: '📊 Ledger Live', desc: 'Snapshot ao vivo do DB2 em memória com saldos de todas as contas. Atualiza via SSE a cada 5 segundos.' },
      { name: '🔎 FSCCHK – Saldo', desc: 'Selecione uma conta e um valor para verificar saldo disponível. Retorna SQLCODE, REASON-CODE e disponibilidade.' },
      { name: '💳 FSCSET – Liquidar', desc: 'Executa débito/crédito atômico entre contas. Simula EXEC CICS SYNCPOINT com cálculo automático de taxa de 1,5%.' },
      { name: '📈 FSCBCH – Relatório', desc: 'Consolidado diário: total de transações, volume, taxas coletadas e saldo do treasury. Tabela da LEDGER_AUDIT.' },
      { name: '🖨️ SYSPRINT JCL', desc: 'Output de impressora que o programa COBOL FSCBCH geraria no z/OS.' },
    ]
  },
  {
    id: 'flow', icon: '🔄', title: 'Fluxo de uma Transação',
    flow: [
      'Comprador finaliza pedido na loja parceira',
      'Sistema parceiro → POST /api/v1/transactions/realtime com Bearer JWT',
      'Gateway valida JWT (Keycloak) e aplica Rate Limit (Redis)',
      'Gateway chama Sec Engine via gRPC + mTLS (Circuit Breaker ativo)',
      'Sec Engine avalia heurísticas antifraude',
      'Se APROVADA → publica no Kafka → IBM MQ Bridge',
      'FSCSET consome da fila → EXEC CICS SYNCPOINT (débito + crédito + log)',
      'Painel atualiza via SSE em tempo real',
    ]
  },
  {
    id: 'accounts', icon: '🏦', title: 'Contas do Core Ledger',
    accounts: [
      { id: 'ACC-001-BUYER', type: 'BUYER', balance: 'R$ 50.000,00', owner: 'João Silva' },
      { id: 'ACC-002-BUYER', type: 'BUYER', balance: 'R$ 1.200,00', owner: 'Maria Souza' },
      { id: 'ACC-003-MERCHANT', type: 'MERCHANT', balance: 'R$ 8.500,00', owner: 'Loja FastTech' },
      { id: 'ACC-004-MERCHANT', type: 'MERCHANT', balance: 'R$ 320,00', owner: 'Loja XYZ (Suspensa)' },
      { id: 'ACC-FSC-TREASURY', type: 'SYSTEM', balance: 'R$ 0,00', owner: 'FSC Fee Treasury' },
    ]
  },
  {
    id: 'api', icon: '🔌', title: 'API do Mainframe Simulator',
    apis: [
      { method: 'GET', route: '/zosconnect/health', desc: 'Status do simulador' },
      { method: 'GET', route: '/zosconnect/accounts', desc: 'Lista contas (DB2 snapshot)' },
      { method: 'GET', route: '/zosconnect/fscchk/balance/:id?amount=N', desc: 'FSCCHK – Verificar saldo' },
      { method: 'POST', route: '/zosconnect/fscset/settle', desc: 'FSCSET – Liquidação atômica' },
      { method: 'GET', route: '/zosconnect/fscbch/daily-report', desc: 'FSCBCH – Relatório batch' },
      { method: 'GET', route: '/zosconnect/ledger-events', desc: 'SSE stream (5s)' },
    ]
  },
  {
    id: 'security', icon: '🔒', title: 'Segurança',
    items: [
      { label: 'Keycloak OAuth2', desc: 'Login com JWT RS256. Roles: fsc_user e fsc_admin.' },
      { label: 'Rate Limit Redis', desc: 'Máximo 10 req/s por IP. HTTP 429 se excedido.' },
      { label: 'mTLS gRPC', desc: 'Comunicação interna Gateway↔SecEngine autenticada mutuamente.' },
      { label: 'Zero-Trust', desc: 'Nenhum serviço interno aceita requisições sem autenticação mútua.' },
      { label: 'LGPD', desc: 'Laudos salvos no S3 com dados pessoais mascarados.' },
    ]
  },
  {
    id: 'troubleshoot', icon: '🛠️', title: 'Solução de Problemas',
    issues: [
      { problem: 'Loop de redirecionamento no login', solution: 'Aguarde 60s após docker compose up para o Keycloak inicializar.' },
      { problem: 'SQLCODE +100 no FSCCHK', solution: 'Use um dos Account IDs da seção "Contas do Core Ledger".' },
      { problem: 'REASON-CODE INSF no FSCSET', solution: 'Use ACC-001-BUYER como débito (saldo R$ 50.000).' },
      { problem: 'Mainframe API offline', solution: 'cd fsc-mainframe/simulator && node server.js' },
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
        <p class="text-xs text-gray-500 mt-0.5">Documentação completa do sistema FSC</p>
      </div>
      <span class="text-[10px] text-gray-600 border border-white/5 rounded px-2 py-1">v1.0.0</span>
    </div>

    <!-- Índice rápido -->
    <div class="flex flex-wrap gap-2 mb-8">
      <a v-for="s in sections" :key="s.id" :href="`#${s.id}`"
         class="text-xs text-gray-400 hover:text-[#00f0ff] border border-white/5 hover:border-[#00f0ff]/30 rounded-lg px-3 py-1.5 transition-all">
        {{ s.icon }} {{ s.title }}
      </a>
    </div>

    <div class="space-y-6">

      <!-- Seção genérica com content HTML -->
      <template v-for="s in sections" :key="s.id">
        <section :id="s.id" class="bg-[#0d0d0d] border border-white/5 rounded-xl p-6 scroll-mt-20">
          <h3 class="text-sm font-semibold text-white mb-4 flex items-center gap-2">
            <span>{{ s.icon }}</span> {{ s.title }}
          </h3>

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
              <span class="text-sm shrink-0">{{ sub.name }}</span>
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
                <th class="text-left pb-2 font-normal">Proprietário</th>
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
                <th class="text-left pb-2 font-normal w-16">Método</th>
                <th class="text-left pb-2 font-normal">Rota</th>
                <th class="text-left pb-2 font-normal">Descrição</th>
              </tr></thead>
              <tbody class="divide-y divide-white/5">
                <tr v-for="a in s.apis" :key="a.route" class="text-gray-400">
                  <td class="py-2"><span class="text-[#00f0ff] font-mono text-[10px]">{{ a.method }}</span></td>
                  <td class="py-2 font-mono text-[10px] text-gray-300">{{ a.route }}</td>
                  <td class="py-2">{{ a.desc }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Security items -->
          <div v-if="s.items" class="space-y-2">
            <div v-for="item in s.items" :key="item.label" class="flex gap-4 bg-[#0a0a0a] border border-white/5 rounded-lg px-4 py-3 text-xs">
              <span class="text-white font-medium w-40 shrink-0">{{ item.label }}</span>
              <span class="text-gray-500">{{ item.desc }}</span>
            </div>
          </div>

          <!-- Troubleshoot -->
          <div v-if="s.issues" class="space-y-2">
            <div v-for="item in s.issues" :key="item.problem" class="bg-[#0a0a0a] border border-white/5 rounded-lg px-4 py-3 text-xs">
              <p class="text-yellow-400/80 mb-1">⚠ {{ item.problem }}</p>
              <p class="text-gray-400">→ {{ item.solution }}</p>
            </div>
          </div>

        </section>
      </template>

    </div>
  </div>
</template>
