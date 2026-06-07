import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── Métricas customizadas ───────────────────────────────────────────────────
const errorRate      = new Rate('error_rate');
const txDuration     = new Trend('transaction_duration', true);
const successCounter = new Counter('transactions_success');
const failCounter    = new Counter('transactions_failed');

// ─── Cenário de Carga — Objetivo: 20-25k usuários ───────────────────────────
export const options = {
    scenarios: {
        // Cenário 1: Ramp progressivo até 25k VUs
        ramp_to_peak: {
            executor: 'ramping-vus',
            stages: [
                { duration: '1m',  target: 100   },  // Aquecimento
                { duration: '2m',  target: 1000  },  // 1k usuários
                { duration: '2m',  target: 5000  },  // 5k usuários
                { duration: '3m',  target: 10000 },  // 10k usuários
                { duration: '3m',  target: 20000 },  // 20k usuários
                { duration: '5m',  target: 25000 },  // 🔴 Pico: 25k usuários
                { duration: '2m',  target: 10000 },  // Ramp-down
                { duration: '2m',  target: 0     },  // Finalização
            ],
            gracefulRampDown: '30s',
        },
    },
    // Thresholds de qualidade
    thresholds: {
        'http_req_duration':          ['p(95)<500', 'p(99)<2000'], // p95 < 500ms, p99 < 2s
        'http_req_failed':            ['rate<0.05'],                // <5% de falhas
        'error_rate':                 ['rate<0.05'],
        'transaction_duration':       ['p(95)<500'],
        'checks':                     ['rate>0.95'],                // >95% dos checks ok
    },
};

// ─── Dados de teste variados ─────────────────────────────────────────────────
const ACCOUNTS   = ['ACC-001', 'ACC-002', 'ACC-003', 'ACC-004', 'ACC-005'];
const MERCHANTS  = ['MERCH-001', 'MERCH-002', 'MERCH-003'];
const CURRENCIES = ['USD', 'BRL', 'EUR'];

function randomFrom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

// ─── Função principal ─────────────────────────────────────────────────────────
export default function () {
    const url = 'http://127.0.0.1:8082/api/v1/transactions/realtime';

    const payload = JSON.stringify({
        accountId:  randomFrom(ACCOUNTS),
        amount:     parseFloat((Math.random() * 9999 + 1).toFixed(2)),
        currency:   randomFrom(CURRENCIES),
        merchantId: randomFrom(MERCHANTS),
        timestamp:  new Date().toISOString(),
    });

    const params = {
        headers: { 'Content-Type': 'application/json' },
        timeout: '10s',
    };

    group('realtime_transaction', () => {
        const start = Date.now();
        const res = http.post(url, payload, params);
        const elapsed = Date.now() - start;

        txDuration.add(elapsed);

        const ok = check(res, {
            'status 200':          (r) => r.status === 200,
            'response time < 1s':  (r) => r.timings.duration < 1000,
            'body not empty':      (r) => r.body && r.body.length > 0,
        });

        if (ok) {
            successCounter.add(1);
        } else {
            failCounter.add(1);
            errorRate.add(1);
        }
    });

    // Sleep curto para simular usuários reais (não spam puro)
    sleep(Math.random() * 0.5);
}
