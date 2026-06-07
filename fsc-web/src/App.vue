<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import MainframePanel from './components/MainframePanel.vue'
import ManualPage from './components/ManualPage.vue'

type Tab = 'audit' | 'mainframe' | 'manual'
const activeTab = ref<Tab>('audit')
const alerts = ref<string[]>([])
const connectionStatus = ref('Conectando...')

onMounted(() => {
  connectionStatus.value = 'Conectado'
  const messages = [
    'APROVADA', 'APROVADA', 'APROVADA', '🔴 FRAUDE DETECTADA', 'APROVADA'
  ]
  let i = 0
  setInterval(() => {
    const status = messages[i % messages.length]
    alerts.value.unshift(`[${new Date().toLocaleTimeString()}] ${status} — txn-${Math.random().toString(36).slice(2, 10).toUpperCase()}`)
    if (alerts.value.length > 50) alerts.value.pop()
    i++
  }, 8000)
})

const tabs: { key: Tab; label: string }[] = [
  { key: 'audit', label: '🔍 Auditoria' },
  { key: 'mainframe', label: '🖥️ Core Ledger' },
  { key: 'manual', label: '📖 Manual' },
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
            <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-2">Latência gRPC Média</p>
            <p class="text-3xl font-light text-white">12ms</p>
          </div>
        </div>

        <div class="bg-[#111] border border-white/5 rounded-xl p-6">
          <div class="flex items-center justify-between mb-4">
            <p class="text-xs uppercase tracking-widest text-gray-500 font-medium">Feed de Alertas Antifraude</p>
            <span class="text-[10px] text-[#00f0ff] border border-[#00f0ff]/20 rounded px-2 py-0.5">● SSE LIVE</span>
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
        <MainframePanel />
      </div>

      <!-- Manual -->
      <div v-if="activeTab === 'manual'">
        <ManualPage />
      </div>

    </main>
  </div>
</template>
