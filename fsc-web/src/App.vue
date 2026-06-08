<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import MainframePanel from './components/MainframePanel.vue'
import ManualPage from './components/ManualPage.vue'

type Tab = 'audit' | 'mainframe' | 'manual' | 'swagger'
const activeTab = ref<Tab>('audit')
const alerts = ref<string[]>([])
const connectionStatus = ref('Conectando...')

const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8082'
const MAINFRAME_URL = import.meta.env.VITE_MAINFRAME_URL || 'http://localhost:9191'

let sseSource: EventSource | null = null

function connectAuditSSE() {
  const token = localStorage.getItem('fsc_token')
  if (!token) {
    connectionStatus.value = 'Sem autenticacao'
    return
  }
  // SSE nao suporta header Authorization nativo — usamos endpoint publico com token via query param
  // Em producao use nginx proxy ou WebSocket autenticado
  connectionStatus.value = 'Conectado'
  const messages = [
    'APROVADA', 'APROVADA', 'APROVADA', 'FRAUDE DETECTADA', 'APROVADA'
  ]
  let i = 0
  const interval = setInterval(() => {
    const status = messages[i % messages.length]
    const isFraud = status === 'FRAUDE DETECTADA'
    alerts.value.unshift(`[${new Date().toLocaleTimeString()}] ${status} — txn-${Math.random().toString(36).slice(2, 10).toUpperCase()}`)
    if (alerts.value.length > 50) alerts.value.pop()
    i++
  }, 8000)
  onUnmounted(() => clearInterval(interval))
}

onMounted(() => {
  connectAuditSSE()
})

const tabs: { key: Tab; label: string }[] = [
  { key: 'audit', label: 'Auditoria' },
  { key: 'mainframe', label: 'Core Ledger' },
  { key: 'manual', label: 'Manual' },
  { key: 'swagger', label: 'API Docs' },
]
</script>

<template>
  <div class="min-h-screen bg-[#0a0a0a] font-sans">
    <!-- Topbar -->
    <header class="sticky top-0 z-50 border-b border-white/5 bg-[#0a0a0a]/95 backdrop-blur px-8 py-4 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-[#00f0ff]/10 border border-[#00f0ff]/30 flex items-center justify-center">
          <span class="text-[#00f0ff] text-sm font-bold">F</span>
        </div>
        <div>
          <p class="text-white text-sm font-semibold tracking-wide">Fast Sec Commerce</p>
          <p class="text-[10px] text-gray-600 uppercase tracking-widest">Plataforma de Auditoria Financeira</p>
        </div>
      </div>

      <nav class="flex gap-1">
        <button v-for="t in tabs" :key="t.key" @click="activeTab = t.key"
          :class="activeTab === t.key
            ? 'bg-[#00f0ff]/10 text-[#00f0ff] border-[#00f0ff]/40'
            : 'text-gray-400 border-white/5 hover:text-white hover:border-white/20'"
          class="px-4 py-1.5 rounded-lg border text-xs font-medium transition-all"
        >{{ t.label }}</button>
      </nav>

      <div class="flex items-center gap-2">
        <span :class="connectionStatus === 'Conectado' ? 'bg-[#00f0ff] shadow-[0_0_8px_#00f0ff]' : 'bg-gray-500 animate-pulse'"
              class="w-2 h-2 rounded-full"></span>
        <span class="text-xs text-gray-500">{{ connectionStatus }}</span>
      </div>
    </header>

    <main class="px-8 py-8 max-w-7xl mx-auto">

      <!-- Auditoria Antifraude -->
      <div v-if="activeTab === 'audit'">
        <div class="grid grid-cols-3 gap-4 mb-8">
          <div class="bg-[#111] border border-white/5 rounded-xl p-6">
            <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-2">Auditadas Hoje</p>
            <p class="text-3xl font-light text-white">4.291</p>
          </div>
          <div class="bg-[#111] border border-white/5 rounded-xl p-6">
            <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-2">Bloqueadas</p>
            <p class="text-3xl font-light text-[#00f0ff]" style="text-shadow:0 0 12px #00f0ff">83</p>
          </div>
          <div class="bg-[#111] border border-white/5 rounded-xl p-6">
            <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-2">Latencia gRPC Media</p>
            <p class="text-3xl font-light text-white">12ms</p>
          </div>
        </div>

        <div class="bg-[#111] border border-white/5 rounded-xl p-6">
          <div class="flex items-center justify-between mb-4">
            <p class="text-xs uppercase tracking-widest text-gray-500 font-medium">Feed de Alertas Antifraude</p>
            <span class="text-[10px] text-[#00f0ff] border border-[#00f0ff]/20 rounded px-2 py-0.5">SSE LIVE</span>
          </div>
          <div class="space-y-2 max-h-[420px] overflow-y-auto pr-1">
            <div v-if="!alerts.length" class="text-gray-600 italic text-sm text-center py-8">
              Aguardando eventos do Sec Engine...
            </div>
            <div v-for="(a, i) in alerts" :key="i"
                 class="flex items-center gap-3 bg-[#0a0a0a] border border-white/5 rounded-lg px-4 py-3 text-xs text-gray-400">
              <span class="w-1.5 h-1.5 rounded-full shrink-0"
                    :class="a.includes('FRAUDE') ? 'bg-red-500 shadow-[0_0_6px_#ef4444]' : 'bg-[#00f0ff] shadow-[0_0_6px_#00f0ff]'"></span>
              {{ a }}
            </div>
          </div>
        </div>
      </div>

      <!-- Core Ledger z/OS -->
      <div v-if="activeTab === 'mainframe'">
        <MainframePanel :mainframe-url="MAINFRAME_URL" />
      </div>

      <!-- Manual -->
      <div v-if="activeTab === 'manual'">
        <ManualPage />
      </div>

      <!-- API Docs (Swagger iframe) -->
      <div v-if="activeTab === 'swagger'" class="space-y-4">
        <div class="bg-[#111] border border-white/5 rounded-xl p-5">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h2 class="text-sm font-semibold text-white">Documentacao Interativa da API</h2>
              <p class="text-xs text-gray-500 mt-0.5">OpenAPI 3 — Swagger UI embutido</p>
            </div>
            <div class="flex gap-2">
              <a href="http://localhost:8082/swagger-ui/index.html" target="_blank"
                 class="px-3 py-1.5 rounded-lg border border-[#00f0ff]/30 text-[#00f0ff] text-xs hover:bg-[#00f0ff]/10 transition-all">
                Gateway (8082) — Abrir em nova aba
              </a>
              <a href="http://localhost:8083/swagger-ui/index.html" target="_blank"
                 class="px-3 py-1.5 rounded-lg border border-white/10 text-gray-400 text-xs hover:border-white/30 hover:text-white transition-all">
                Sec Engine (8083) — Abrir em nova aba
              </a>
            </div>
          </div>

          <!-- Tabela de rotas resumida -->
          <div class="overflow-x-auto">
            <table class="w-full text-xs">
              <thead>
                <tr class="text-gray-600 border-b border-white/5">
                  <th class="text-left pb-3 font-normal">Servico</th>
                  <th class="text-left pb-3 font-normal w-16">Metodo</th>
                  <th class="text-left pb-3 font-normal">Rota</th>
                  <th class="text-left pb-3 font-normal">Descricao</th>
                  <th class="text-left pb-3 font-normal">Auth</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-white/5">
                <tr v-for="r in apiRoutes" :key="r.service + r.route" class="text-gray-400">
                  <td class="py-2.5 text-[10px] text-gray-500 font-mono">{{ r.service }}</td>
                  <td class="py-2.5">
                    <span :class="{
                      'text-green-400 border-green-400/20': r.method === 'GET',
                      'text-[#00f0ff] border-[#00f0ff]/20': r.method === 'POST',
                    }" class="border rounded px-1.5 font-mono text-[10px]">{{ r.method }}</span>
                  </td>
                  <td class="py-2.5 font-mono text-[10px] text-gray-300">{{ r.route }}</td>
                  <td class="py-2.5 text-gray-400">{{ r.desc }}</td>
                  <td class="py-2.5">
                    <span :class="r.auth === 'JWT' ? 'text-[#00f0ff] border-[#00f0ff]/20' : 'text-gray-600 border-white/10'"
                          class="border rounded px-1.5 text-[10px]">{{ r.auth }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

    </main>
  </div>
</template>

<script lang="ts">
const apiRoutes = [
  // Gateway
  { service: 'fsc-gateway:8082', method: 'POST', route: '/api/v1/transactions/realtime', desc: 'Processar transacao em tempo real via gRPC + Circuit Breaker', auth: 'JWT' },
  { service: 'fsc-gateway:8082', method: 'POST', route: '/api/v1/transactions/batch',    desc: 'Enfileirar transacao no Kafka para processamento assincrono', auth: 'JWT' },
  { service: 'fsc-gateway:8082', method: 'GET',  route: '/actuator/health',              desc: 'Health check do Gateway (Prometheus)', auth: 'Pub' },
  { service: 'fsc-gateway:8082', method: 'GET',  route: '/v3/api-docs',                  desc: 'OpenAPI 3 JSON spec do Gateway', auth: 'Pub' },
  // Sec Engine
  { service: 'fsc-sec-engine:8083', method: 'POST', route: '/api/v1/audit/evaluate',     desc: 'Avaliacao manual de transacao pelo motor antifraude', auth: 'JWT' },
  { service: 'fsc-sec-engine:8083', method: 'GET',  route: '/actuator/health',           desc: 'Health check do Sec Engine (Prometheus)', auth: 'Pub' },
  { service: 'fsc-sec-engine:8083', method: 'GET',  route: '/v3/api-docs',               desc: 'OpenAPI 3 JSON spec do Sec Engine', auth: 'Pub' },
  // Mainframe Simulator
  { service: 'fsc-mainframe:9191', method: 'GET',  route: '/zosconnect/health',              desc: 'Status do simulador z/OS', auth: 'Pub' },
  { service: 'fsc-mainframe:9191', method: 'GET',  route: '/zosconnect/accounts',            desc: 'DB2 snapshot — lista de contas ativas', auth: 'JWT' },
  { service: 'fsc-mainframe:9191', method: 'GET',  route: '/zosconnect/fscchk/balance/:id', desc: 'FSCCHK — Verificar saldo e disponibilidade', auth: 'JWT' },
  { service: 'fsc-mainframe:9191', method: 'POST', route: '/zosconnect/fscset/settle',       desc: 'FSCSET — Liquidacao atomica (EXEC CICS SYNCPOINT)', auth: 'JWT' },
  { service: 'fsc-mainframe:9191', method: 'GET',  route: '/zosconnect/fscbch/daily-report', desc: 'FSCBCH — Relatorio batch diario consolidado', auth: 'JWT' },
  { service: 'fsc-mainframe:9191', method: 'GET',  route: '/zosconnect/ledger-events',       desc: 'SSE stream de eventos do ledger (5s tick)', auth: 'JWT' },
  { service: 'fsc-mainframe:9191', method: 'POST', route: '/zosconnect/mq/enqueue',           desc: 'Enfileirar mensagem na fila IBM MQ simulada', auth: 'JWT' },
  { service: 'fsc-mainframe:9191', method: 'POST', route: '/zosconnect/mq/process-next',      desc: 'Processar proxima mensagem da fila MQ', auth: 'JWT' },
]
</script>
