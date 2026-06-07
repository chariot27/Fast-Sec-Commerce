<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const MAINFRAME_URL = 'http://localhost:9191/zosconnect'

// ─── Estado ────────────────────────────────────────────────────────
const status = ref<'online' | 'offline' | 'connecting'>('connecting')
const accounts = ref<any[]>([])
const report = ref<any>(null)
const ledgerEvents = ref<any[]>([])
const settlement = ref({ debitAcct: '', creditAcct: '', amount: '', orderId: '' })
const settleResult = ref<any>(null)
const settleLoading = ref(false)
const checkAcct = ref('')
const checkAmount = ref('')
const checkResult = ref<any>(null)
const mqDepth = ref(0)
const activeSection = ref<'ledger' | 'check' | 'settle' | 'report' | 'sysprint'>('ledger')
const sysprintText = ref('')

let eventSource: EventSource | null = null

// ─── Helpers ────────────────────────────────────────────────────────
const fmt = (n: number) => n?.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }) ?? 'R$ 0,00'

// ─── Funções de API ─────────────────────────────────────────────────

async function loadAccounts() {
  try {
    const res = await fetch(`${MAINFRAME_URL}/accounts`)
    accounts.value = await res.json()
  } catch { accounts.value = [] }
}

async function loadReport() {
  try {
    const res = await fetch(`${MAINFRAME_URL}/fscbch/daily-report`)
    const data = await res.json()
    report.value = data
    sysprintText.value = data.sysprint ?? ''
  } catch { report.value = null }
}

async function checkBalance() {
  if (!checkAcct.value) return
  try {
    const url = `${MAINFRAME_URL}/fscchk/balance/${encodeURIComponent(checkAcct.value)}?amount=${checkAmount.value || 0}`
    const res = await fetch(url)
    checkResult.value = { ...await res.json(), httpStatus: res.status }
  } catch (e: any) { checkResult.value = { error: e.message } }
}

async function doSettle() {
  settleLoading.value = true
  settleResult.value = null
  try {
    const res = await fetch(`${MAINFRAME_URL}/fscset/settle`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        orderId: settlement.value.orderId || `ORD-${Date.now()}`,
        debitAcct: settlement.value.debitAcct,
        creditAcct: settlement.value.creditAcct,
        amount: parseFloat(settlement.value.amount),
      })
    })
    const data = await res.json()
    settleResult.value = { ...data, httpStatus: res.status }
    // Recarregar contas e relatório após liquidação
    await Promise.all([loadAccounts(), loadReport()])
  } catch (e: any) {
    settleResult.value = { error: e.message }
  } finally { settleLoading.value = false }
}

function connectSSE() {
  eventSource = new EventSource(`${MAINFRAME_URL}/ledger-events`)
  eventSource.onopen = () => { status.value = 'online' }
  eventSource.onerror = () => { status.value = 'offline' }
  eventSource.onmessage = (e) => {
    const data = JSON.parse(e.data)
    mqDepth.value = data.mqQueueDepth ?? 0
    if (data.type !== 'CONNECTED') {
      ledgerEvents.value.unshift(data)
      if (ledgerEvents.value.length > 20) ledgerEvents.value.pop()
    }
  }
}

onMounted(async () => {
  connectSSE()
  await Promise.all([loadAccounts(), loadReport()])
})

onUnmounted(() => eventSource?.close())
</script>

<template>
  <div class="text-white">

    <!-- Header do Painel z/OS -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h2 class="text-lg font-semibold text-white">FSC Core Ledger</h2>
        <p class="text-xs text-gray-500 mt-0.5">z/OS CICS/DB2 Simulator · Programas: FSCCHK · FSCSET · FSCBCH</p>
      </div>
      <div class="flex items-center gap-2 border border-white/10 rounded-lg px-3 py-1.5">
        <span :class="status === 'online' ? 'bg-green-400 shadow-[0_0_6px_#4ade80]' : status === 'connecting' ? 'bg-yellow-400 animate-pulse' : 'bg-red-500'"
              class="w-2 h-2 rounded-full"></span>
        <span class="text-xs text-gray-400 uppercase tracking-widest">
          {{ status === 'online' ? 'z/OS Online' : status === 'connecting' ? 'Conectando...' : 'Offline' }}
        </span>
        <span class="ml-2 text-xs text-gray-600">MQ Depth: {{ mqDepth }}</span>
      </div>
    </div>

    <!-- Sub-navegação -->
    <div class="flex gap-2 mb-6 flex-wrap">
      <button v-for="s in [
        { key: 'ledger',   label: '📊 Ledger Live' },
        { key: 'check',    label: '🔎 FSCCHK – Saldo' },
        { key: 'settle',   label: '💳 FSCSET – Liquidar' },
        { key: 'report',   label: '📈 FSCBCH – Relatório' },
        { key: 'sysprint', label: '🖨️ SYSPRINT JCL' },
      ]" :key="s.key"
        @click="activeSection = s.key as any"
        :class="activeSection === s.key ? 'border-[#00f0ff]/50 text-[#00f0ff] bg-[#00f0ff]/5' : 'border-white/5 text-gray-400 hover:text-gray-200'"
        class="px-3 py-1.5 rounded-lg border text-xs transition-all"
      >{{ s.label }}</button>
    </div>

    <!-- ── LEDGER LIVE ─────────────────────────────────────────── -->
    <div v-if="activeSection === 'ledger'" class="space-y-5">

      <!-- Cards das contas (DB2 Snapshot) -->
      <div>
        <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-3">DB2 · FSC.ACCOUNTS (Snapshot ao vivo)</p>
        <div class="grid grid-cols-2 xl:grid-cols-3 gap-3">
          <div v-for="acct in accounts" :key="acct.id"
               class="bg-[#0d0d0d] border border-white/5 rounded-xl p-4 space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-[10px] font-mono text-gray-500">{{ acct.id }}</span>
              <span :class="acct.status === 'ACTIVE' ? 'text-green-400 border-green-400/30' : 'text-red-400 border-red-400/30'"
                    class="text-[9px] border rounded px-1.5 py-0.5 uppercase">{{ acct.status }}</span>
            </div>
            <p class="text-white text-sm font-medium">{{ acct.owner }}</p>
            <p :class="acct.type === 'SYSTEM' ? 'text-[#00f0ff]' : 'text-white'"
               class="text-xl font-light">{{ fmt(acct.balance) }}</p>
            <div class="flex gap-3 text-[10px] text-gray-500">
              <span>Reservado: {{ fmt(acct.reserved) }}</span>
              <span>· {{ acct.type }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- SSE Ledger events -->
      <div class="bg-[#0d0d0d] border border-white/5 rounded-xl p-5">
        <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-3">SSE Stream · LEDGER_AUDIT Events</p>
        <div class="space-y-2 max-h-52 overflow-y-auto font-mono text-xs">
          <div v-if="ledgerEvents.length === 0" class="text-gray-600 italic">Aguardando próximo tick (5s)...</div>
          <div v-for="(ev, i) in ledgerEvents" :key="i"
               class="bg-[#0a0a0a] border border-white/5 rounded-lg px-3 py-2 flex justify-between items-center text-gray-400">
            <span>
              Settled: <span class="text-white">{{ ev.totalSettled }}</span> ·
              Montante: <span class="text-[#00f0ff]">{{ fmt(ev.totalAmount) }}</span> ·
              Taxas: {{ fmt(ev.totalFees) }} ·
              MQ: {{ ev.mqQueueDepth }}
            </span>
            <span class="text-gray-600 text-[10px]">{{ new Date(ev.timestamp).toLocaleTimeString() }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ── FSCCHK – Verificação de Saldo ───────────────────────── -->
    <div v-if="activeSection === 'check'" class="space-y-4">
      <div class="bg-[#0d0d0d] border border-white/5 rounded-xl p-6">
        <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-4">FSCCHK · CICS REST · EXEC SQL SELECT FROM FSC.ACCOUNTS</p>
        <div class="flex gap-3 flex-wrap">
          <select v-model="checkAcct" class="bg-[#0a0a0a] border border-white/10 rounded-lg px-3 py-2 text-sm text-white flex-1">
            <option value="">— Selecione a conta —</option>
            <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.id }} · {{ a.owner }}</option>
          </select>
          <input v-model="checkAmount" type="number" placeholder="Valor a verificar (R$)"
                 class="bg-[#0a0a0a] border border-white/10 rounded-lg px-3 py-2 text-sm text-white w-52" />
          <button @click="checkBalance"
                  class="px-5 py-2 rounded-lg bg-[#00f0ff]/10 border border-[#00f0ff]/30 text-[#00f0ff] text-sm hover:bg-[#00f0ff]/20 transition-all">
            Executar FSCCHK
          </button>
        </div>

        <div v-if="checkResult" class="mt-5 bg-[#0a0a0a] border rounded-xl p-4 font-mono text-xs space-y-1"
             :class="checkResult.fscResReturnCode === 0 ? 'border-green-500/20' : 'border-red-500/20'">
          <div class="flex gap-4 flex-wrap">
            <span>SQLCODE: <span class="text-[#00f0ff]">{{ checkResult.sqlcode }}</span></span>
            <span>RETURN-CODE: <span :class="checkResult.fscResReturnCode === 0 ? 'text-green-400' : 'text-red-400'">{{ checkResult.fscResReturnCode }}</span></span>
            <span>REASON: <span class="text-white">{{ checkResult.fscResReasonCode }}</span></span>
          </div>
          <div class="flex gap-4 flex-wrap mt-2">
            <span>SALDO TOTAL: <span class="text-white">{{ fmt(checkResult.fscResBalance) }}</span></span>
            <span>DISPONÍVEL: <span class="text-[#00f0ff]">{{ fmt(checkResult.fscResAvailable) }}</span></span>
            <span>STATUS: <span :class="checkResult.fscResStatus === 'ACTIVE' ? 'text-green-400' : 'text-yellow-400'">{{ checkResult.fscResStatus }}</span></span>
          </div>
          <p class="text-gray-500 mt-2 italic">{{ checkResult.fscResMsg }}</p>
        </div>
      </div>
    </div>

    <!-- ── FSCSET – Liquidação ─────────────────────────────────── -->
    <div v-if="activeSection === 'settle'" class="space-y-4">
      <div class="bg-[#0d0d0d] border border-white/5 rounded-xl p-6">
        <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-4">FSCSET · MQ Triggered · EXEC CICS SYNCPOINT · DEBIT + CREDIT + AUDIT LOG</p>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="text-xs text-gray-500 mb-1 block">Conta Débito (Comprador)</label>
            <select v-model="settlement.debitAcct" class="w-full bg-[#0a0a0a] border border-white/10 rounded-lg px-3 py-2 text-sm text-white">
              <option value="">— Selecione —</option>
              <option v-for="a in accounts.filter(a => a.type === 'BUYER')" :key="a.id" :value="a.id">{{ a.id }} · {{ fmt(a.balance) }}</option>
            </select>
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">Conta Crédito (Lojista)</label>
            <select v-model="settlement.creditAcct" class="w-full bg-[#0a0a0a] border border-white/10 rounded-lg px-3 py-2 text-sm text-white">
              <option value="">— Selecione —</option>
              <option v-for="a in accounts.filter(a => a.type === 'MERCHANT')" :key="a.id" :value="a.id">{{ a.id }} · {{ fmt(a.balance) }}</option>
            </select>
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">Valor (R$)</label>
            <input v-model="settlement.amount" type="number" step="0.01" placeholder="0.00"
                   class="w-full bg-[#0a0a0a] border border-white/10 rounded-lg px-3 py-2 text-sm text-white" />
          </div>
          <div>
            <label class="text-xs text-gray-500 mb-1 block">Order ID (opcional)</label>
            <input v-model="settlement.orderId" type="text" placeholder="ORD-auto"
                   class="w-full bg-[#0a0a0a] border border-white/10 rounded-lg px-3 py-2 text-sm text-white" />
          </div>
        </div>
        <div class="mt-4 flex items-center gap-3">
          <button @click="doSettle" :disabled="settleLoading"
                  class="px-6 py-2 rounded-lg bg-[#00f0ff]/10 border border-[#00f0ff]/30 text-[#00f0ff] text-sm hover:bg-[#00f0ff]/20 transition-all disabled:opacity-50">
            {{ settleLoading ? 'Executando SYNCPOINT...' : 'Executar FSCSET' }}
          </button>
          <p class="text-[10px] text-gray-600">Taxa FSC: 1,5% sobre o valor (mín. R$0,50)</p>
        </div>

        <div v-if="settleResult" class="mt-5 bg-[#0a0a0a] border rounded-xl p-4 font-mono text-xs space-y-2"
             :class="settleResult.fscResReturnCode === 0 ? 'border-[#00f0ff]/20' : 'border-red-500/20'">
          <div class="flex gap-4 flex-wrap">
            <span>SQLCODE: <span class="text-[#00f0ff]">{{ settleResult.sqlcode }}</span></span>
            <span>REASON: <span :class="settleResult.fscResReturnCode === 0 ? 'text-green-400' : 'text-red-400'">{{ settleResult.fscResReasonCode }}</span></span>
            <span>HTTP: {{ settleResult.httpStatus }}</span>
          </div>
          <p class="text-gray-400 italic">{{ settleResult.fscResMsg }}</p>
          <div v-if="settleResult.settlement" class="mt-2 space-y-1 text-gray-400">
            <p>Trans-ID: <span class="text-white">{{ settleResult.settlement.transId }}</span></p>
            <p>Débito: <span class="text-red-400">-{{ fmt(settleResult.settlement.totalDebit) }}</span>
               · Crédito: <span class="text-green-400">+{{ fmt(settleResult.settlement.amount) }}</span>
               · Taxa: <span class="text-[#00f0ff]">{{ fmt(settleResult.settlement.fee) }}</span></p>
          </div>
        </div>
      </div>
    </div>

    <!-- ── FSCBCH – Relatório Batch ─────────────────────────────── -->
    <div v-if="activeSection === 'report'" class="space-y-4">
      <div class="flex items-center justify-between">
        <p class="text-[10px] uppercase tracking-widest text-gray-500">FSCBCH · Batch JCL · SELECT COUNT/SUM FROM FSC.LEDGER_AUDIT</p>
        <button @click="loadReport" class="text-xs text-[#00f0ff] border border-[#00f0ff]/30 px-3 py-1.5 rounded-lg hover:bg-[#00f0ff]/10 transition-all">
          ↻ Atualizar
        </button>
      </div>
      <div v-if="report" class="grid grid-cols-2 xl:grid-cols-4 gap-3">
        <div class="bg-[#0d0d0d] border border-white/5 rounded-xl p-5">
          <p class="text-[10px] text-gray-500 uppercase tracking-widest mb-1">Transações Liquidadas</p>
          <p class="text-3xl font-light text-white">{{ report.totalTransactions.toLocaleString('pt-BR') }}</p>
        </div>
        <div class="bg-[#0d0d0d] border border-white/5 rounded-xl p-5">
          <p class="text-[10px] text-gray-500 uppercase tracking-widest mb-1">Volume Liquidado</p>
          <p class="text-3xl font-light text-[#00f0ff]">{{ fmt(report.totalAmountSettled) }}</p>
        </div>
        <div class="bg-[#0d0d0d] border border-white/5 rounded-xl p-5">
          <p class="text-[10px] text-gray-500 uppercase tracking-widest mb-1">Taxas Coletadas</p>
          <p class="text-3xl font-light text-green-400">{{ fmt(report.totalFeesCollected) }}</p>
        </div>
        <div class="bg-[#0d0d0d] border border-white/5 rounded-xl p-5">
          <p class="text-[10px] text-gray-500 uppercase tracking-widest mb-1">Treasury (FSC)</p>
          <p class="text-3xl font-light text-white">{{ fmt(report.treasuryBalance) }}</p>
        </div>
      </div>

      <!-- Últimas entradas da LEDGER_AUDIT -->
      <div v-if="report?.entries?.length" class="bg-[#0d0d0d] border border-white/5 rounded-xl p-5">
        <p class="text-[10px] text-gray-500 uppercase tracking-widest mb-3">Últimas Entradas · FSC.LEDGER_AUDIT</p>
        <div class="overflow-x-auto">
          <table class="w-full text-xs">
            <thead>
              <tr class="text-gray-600 border-b border-white/5">
                <th class="text-left pb-2 font-normal">Trans-ID</th>
                <th class="text-right pb-2 font-normal">Valor</th>
                <th class="text-right pb-2 font-normal">Taxa</th>
                <th class="text-left pb-2 font-normal">Status</th>
                <th class="text-left pb-2 font-normal">Timestamp</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-white/5">
              <tr v-for="e in report.entries.slice().reverse()" :key="e.transId" class="text-gray-400">
                <td class="py-2 font-mono text-[10px] text-gray-500">{{ e.transId?.slice(0, 16) }}…</td>
                <td class="py-2 text-right text-white">{{ fmt(e.amount) }}</td>
                <td class="py-2 text-right text-[#00f0ff]">{{ fmt(e.fee) }}</td>
                <td class="py-2"><span class="text-green-400 border border-green-400/20 rounded px-1.5 text-[10px]">{{ e.status }}</span></td>
                <td class="py-2 text-gray-600 text-[10px]">{{ new Date(e.procTimestamp).toLocaleTimeString() }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- ── SYSPRINT (output JCL) ──────────────────────────────── -->
    <div v-if="activeSection === 'sysprint'">
      <p class="text-[10px] uppercase tracking-widest text-gray-500 mb-3">SYSPRINT · Output simulado do JCL FSCBCH</p>
      <pre class="bg-[#050505] border border-white/5 rounded-xl p-6 font-mono text-xs text-green-400 overflow-x-auto leading-6 whitespace-pre">{{ sysprintText || 'Nenhum relatório ainda. Execute uma liquidação primeiro.' }}</pre>
    </div>

  </div>
</template>
