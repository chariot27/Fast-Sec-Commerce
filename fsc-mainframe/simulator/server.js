/**
 * FSC Core Ledger — Mainframe Simulator
 * Simula os programas COBOL FSCCHK, FSCSET e FSCBCH via REST API.
 * Expõe as mesmas interfaces que o z/OS Connect EE exporia em produção.
 * Porta: 9191
 *
 * SECURITY FIXES (2026-06-07):
 *  - T1119/T1565: JWT authentication middleware em todas as rotas sensíveis
 *  - T1070: Input sanitization para prevenir Stored XSS e injection
 */

import express from 'express';
import cors from 'cors';
import { v4 as uuidv4 } from 'uuid';
import jwt from 'jsonwebtoken';
import jwksClient from 'jwks-rsa';

const app = express();
app.use(cors());
app.use(express.json());

// =====================================================================
// CONFIGURAÇÃO DE SEGURANÇA — JWT via Keycloak JWKS
// =====================================================================
const KEYCLOAK_JWKS_URI = process.env.KEYCLOAK_JWKS_URI ||
  'http://localhost:8080/realms/fsc/protocol/openid-connect/certs';
const KEYCLOAK_ISSUER   = process.env.KEYCLOAK_ISSUER ||
  'http://localhost:8080/realms/fsc';

const jwks = jwksClient({
  jwksUri: KEYCLOAK_JWKS_URI,
  cache: true,
  cacheMaxEntries: 5,
  cacheMaxAge: 600000, // 10 minutos
});

function getSigningKey(header, callback) {
  jwks.getSigningKey(header.kid, (err, key) => {
    if (err) return callback(err);
    callback(null, key.getPublicKey());
  });
}

/**
 * Middleware JWT — valida Bearer token do Keycloak.
 * FIX T1119 + T1565: protege todas as rotas sensíveis.
 */
function requireAuth(req, res, next) {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({
      error: 'UNAUTHORIZED',
      message: 'Authorization header com Bearer token é obrigatório.',
    });
  }

  const token = authHeader.split(' ')[1];
  jwt.verify(token, getSigningKey, {
    algorithms: ['RS256'],
    issuer: KEYCLOAK_ISSUER,
  }, (err, decoded) => {
    if (err) {
      return res.status(401).json({
        error: 'INVALID_TOKEN',
        message: `Token inválido ou expirado: ${err.message}`,
      });
    }
    req.user = decoded;
    next();
  });
}

// =====================================================================
// INPUT SANITIZATION — FIX T1070 (Stored XSS)
// =====================================================================

/**
 * Remove caracteres HTML/script e limita o tamanho do campo.
 */
function sanitizeString(value, maxLen = 64) {
  if (typeof value !== 'string') return '';
  return value
    .replace(/[<>"'`&]/g, '')   // Remove caracteres HTML perigosos
    .replace(/[^\w\s\-\.]/g, '') // Permite apenas alfanuméricos, espaço, hífen e ponto
    .trim()
    .slice(0, maxLen);
}

function sanitizeAmount(value) {
  const n = parseFloat(value);
  if (isNaN(n) || n <= 0 || n > 999999999) return null;
  return parseFloat(n.toFixed(2));
}

// =====================================================================
// DB2 IN-MEMORY SIMULATOR (z/OS Ledger State)
// =====================================================================
const db2 = {
  accounts: {
    'ACC-001-BUYER':    { id: 'ACC-001-BUYER',    type: 'BUYER',    balance: 50000.00, reserved: 0, status: 'ACTIVE',    owner: 'João Silva' },
    'ACC-002-BUYER':    { id: 'ACC-002-BUYER',    type: 'BUYER',    balance: 1200.00,  reserved: 0, status: 'ACTIVE',    owner: 'Maria Souza' },
    'ACC-003-MERCHANT': { id: 'ACC-003-MERCHANT', type: 'MERCHANT', balance: 8500.00,  reserved: 0, status: 'ACTIVE',    owner: 'Loja FastTech' },
    'ACC-004-MERCHANT': { id: 'ACC-004-MERCHANT', type: 'MERCHANT', balance: 320.00,   reserved: 0, status: 'SUSPENDED', owner: 'Loja XYZ (Suspensa)' },
    'ACC-FSC-TREASURY': { id: 'ACC-FSC-TREASURY', type: 'SYSTEM',   balance: 0,        reserved: 0, status: 'ACTIVE',    owner: 'FSC Fee Treasury' },
  },
  ledgerAudit: [],
  mqQueue: [],
};

// =====================================================================
// HELPERS
// =====================================================================
function now() { return new Date().toISOString(); }
function cobolSqlcode(code, msg) { return { sqlcode: code, sqlerrm: msg }; }
function calcFee(amount) { return Math.max(parseFloat((amount * 0.015).toFixed(2)), 0.50); }

// =====================================================================
// ROTAS PÚBLICAS (sem auth)
// =====================================================================

// Health check — aberto para Prometheus / Docker healthcheck
app.get('/zosconnect/health', (req, res) => {
  res.json({
    status: 'UP',
    system: 'FSC-Core-Ledger-Simulator',
    cobolPrograms: ['FSCCHK', 'FSCSET', 'FSCBCH'],
    db2Tables: ['FSC.ACCOUNTS', 'FSC.LEDGER_AUDIT'],
    mqQueueDepth: db2.mqQueue.length,
    timestamp: now(),
  });
});

// SSE — Stream de eventos (protegido: requer auth)
app.get('/zosconnect/ledger-events', requireAuth, (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders();

  const interval = setInterval(() => {
    const today = new Date().toISOString().slice(0, 10);
    const todaySettled = db2.ledgerAudit.filter(e =>
      e.procTimestamp.startsWith(today) && e.status === 'SETTLED'
    );
    const data = {
      timestamp: now(),
      totalSettled: todaySettled.length,
      totalAmount: parseFloat(todaySettled.reduce((s, e) => s + e.amount, 0).toFixed(2)),
      totalFees: parseFloat(todaySettled.reduce((s, e) => s + e.fee, 0).toFixed(2)),
      treasuryBalance: db2.accounts['ACC-FSC-TREASURY'].balance,
      latestEntry: db2.ledgerAudit.slice(-1)[0] || null,
      mqQueueDepth: db2.mqQueue.length,
    };
    res.write(`data: ${JSON.stringify(data)}\n\n`);
  }, 5000);

  res.write(`data: ${JSON.stringify({ type: 'CONNECTED', msg: 'FSC Core Ledger (z/OS Sim) Online', timestamp: now() })}\n\n`);
  req.on('close', () => clearInterval(interval));
});

// =====================================================================
// ROTAS PROTEGIDAS — Requerem JWT válido do Keycloak
// =====================================================================

// Listar contas — FIX T1119: agora requer autenticação
app.get('/zosconnect/accounts', requireAuth, (req, res) => {
  res.json(Object.values(db2.accounts));
});

// PROGRAMA FSCCHK — Verificação de Saldo
// FIX T1119: agora requer autenticação
app.get('/zosconnect/fscchk/balance/:accountId', requireAuth, (req, res) => {
  const accountId = sanitizeString(req.params.accountId, 30);
  const { amount = 0 } = req.query;
  const acct = db2.accounts[accountId];

  if (!acct) {
    return res.status(404).json({
      ...cobolSqlcode(100, 'ACCOUNT NOT FOUND'),
      fscResReturnCode: -100,
      fscResReasonCode: 'NFND',
      fscResStatus: 'NOT_FOUND',
      fscResMsg: `ACCOUNT ${accountId} NOT FOUND IN LEDGER`,
    });
  }

  const available = parseFloat((acct.balance - acct.reserved).toFixed(2));
  const reqAmount = parseFloat(amount);
  const hasFunds = available >= reqAmount;

  res.json({
    ...cobolSqlcode(0, 'SUCCESSFUL'),
    fscResReturnCode: 0,
    fscResReasonCode: hasFunds ? 'OK  ' : 'INSF',
    fscResBalance: acct.balance,
    fscResAvailable: available,
    fscResStatus: hasFunds ? acct.status : 'INSUFFICIENT_FUNDS',
    fscResOwner: acct.owner,
    fscResAccountType: acct.type,
    fscResMsg: hasFunds ? 'BALANCE CHECK SUCCESSFUL' : 'INSUFFICIENT FUNDS FOR THIS TRANSACTION',
  });
});

// PROGRAMA FSCSET — Liquidação
// FIX T1565 + T1070: agora requer auth + sanitiza inputs
app.post('/zosconnect/fscset/settle', requireAuth, (req, res) => {
  const rawOrderId    = req.body.orderId   || `ORD-${Date.now()}`;
  const rawDebitAcct  = req.body.debitAcct;
  const rawCreditAcct = req.body.creditAcct;
  const rawAmount     = req.body.amount;

  // FIX T1070: sanitizar todos os inputs
  const transId    = uuidv4();
  const orderId    = sanitizeString(rawOrderId, 64);
  const debitAcct  = sanitizeString(rawDebitAcct, 30);
  const creditAcct = sanitizeString(rawCreditAcct, 30);
  const amount     = sanitizeAmount(rawAmount);
  const currency   = sanitizeString(req.body.currency || 'BRL', 3);
  const auditHash  = uuidv4().replace(/-/g, '');

  if (!amount) {
    return res.status(400).json({ error: 'INVALID_AMOUNT', message: 'Valor inválido ou fora do limite permitido.' });
  }

  const debit  = db2.accounts[debitAcct];
  const credit = db2.accounts[creditAcct];

  if (!debit)  return res.status(404).json({ ...cobolSqlcode(100, 'DEBIT ACCOUNT NOT FOUND'),  fscResReasonCode: 'NFND' });
  if (!credit) return res.status(404).json({ ...cobolSqlcode(100, 'CREDIT ACCOUNT NOT FOUND'), fscResReasonCode: 'NFND' });

  const fee        = calcFee(amount);
  const totalDebit = parseFloat((amount + fee).toFixed(2));

  if (debit.balance < totalDebit) {
    return res.status(422).json({
      ...cobolSqlcode(100, 'INSUFFICIENT FUNDS - ROLLBACK ISSUED'),
      fscResReturnCode: -422,
      fscResReasonCode: 'INSF',
      fscResMsg: 'EXEC CICS SYNCPOINT ROLLBACK - INSUFFICIENT FUNDS',
    });
  }

  const duplicate = db2.ledgerAudit.find(r => r.transId === transId);
  if (duplicate) {
    return res.status(200).json({
      ...cobolSqlcode(-803, 'DUPLICATE KEY - IDEMPOTENT IGNORE'),
      fscResReturnCode: 0,
      fscResReasonCode: 'DUPL',
      fscResMsg: 'TRANSACTION ALREADY SETTLED - IDEMPOTENT RETURN',
      settlement: duplicate,
    });
  }

  // EXEC CICS SYNCPOINT
  debit.balance  = parseFloat((debit.balance - totalDebit).toFixed(2));
  credit.balance = parseFloat((credit.balance + amount).toFixed(2));
  db2.accounts['ACC-FSC-TREASURY'].balance = parseFloat((db2.accounts['ACC-FSC-TREASURY'].balance + fee).toFixed(2));

  const auditEntry = {
    transId, orderId, debitAcct, creditAcct,
    amount, fee, totalDebit, currency,
    status: 'SETTLED',
    procTimestamp: now(),
    auditHash,
  };
  db2.ledgerAudit.push(auditEntry);

  res.status(201).json({
    ...cobolSqlcode(0, 'SYNCPOINT COMMITTED'),
    fscResReturnCode: 0,
    fscResReasonCode: 'SETL',
    fscResMsg: 'EXEC CICS SYNCPOINT - DEBIT/CREDIT COMMITTED TO DB2',
    settlement: auditEntry,
  });
});

// PROGRAMA FSCBCH — Relatório Batch Diário
app.get('/zosconnect/fscbch/daily-report', requireAuth, (req, res) => {
  const today = new Date().toISOString().slice(0, 10);
  const todayEntries = db2.ledgerAudit.filter(e =>
    e.procTimestamp.startsWith(today) && e.status === 'SETTLED'
  );

  const totalTrans  = todayEntries.length;
  const totalAmount = parseFloat(todayEntries.reduce((s, e) => s + e.amount, 0).toFixed(2));
  const totalFees   = parseFloat(todayEntries.reduce((s, e) => s + e.fee, 0).toFixed(2));
  const fraudBlocked = db2.ledgerAudit.filter(e =>
    e.procTimestamp.startsWith(today) && e.status === 'FRAUD_BLOCKED'
  ).length;

  const sysprint = [
    '================================================================',
    ' FSC CORE LEDGER - DAILY SETTLEMENT REPORT                      ',
    '================================================================',
    `PROCESS DATE:  ${today}`,
    '',
    `TOTAL TRANSACTIONS SETTLED:    ${String(totalTrans).padStart(9)}`,
    `TOTAL AMOUNT SETTLED (BRL):    ${totalAmount.toFixed(2).padStart(18)}`,
    `TOTAL FEES COLLECTED (BRL):    ${totalFees.toFixed(2).padStart(18)}`,
    `FRAUD BLOCKED (COUNT):         ${String(fraudBlocked).padStart(9)}`,
    '',
    '================================================================',
  ].join('\n');

  res.json({
    ...cobolSqlcode(0, 'BATCH COMPLETED SUCCESSFULLY'),
    reportDate: today,
    totalTransactions: totalTrans,
    totalAmountSettled: totalAmount,
    totalFeesCollected: totalFees,
    fraudBlocked,
    treasuryBalance: db2.accounts['ACC-FSC-TREASURY'].balance,
    sysprint,
    entries: todayEntries.slice(-20),
  });
});

// Fila MQ — protegida
app.post('/zosconnect/mq/enqueue', requireAuth, (req, res) => {
  const msg = { ...req.body, mqMsgId: uuidv4(), enqueuedAt: now() };
  db2.mqQueue.push(msg);
  res.status(202).json({ status: 'ENQUEUED', mqMsgId: msg.mqMsgId, queueDepth: db2.mqQueue.length });
});

app.post('/zosconnect/mq/process-next', requireAuth, (req, res) => {
  const msg = db2.mqQueue.shift();
  if (!msg) return res.status(204).json({ msg: 'QUEUE EMPTY' });

  const fee        = calcFee(msg.amount);
  const totalDebit = parseFloat((msg.amount + fee).toFixed(2));
  const debit      = db2.accounts[msg.debitAcct];
  const credit     = db2.accounts[msg.creditAcct];

  if (!debit || !credit || debit.balance < totalDebit) {
    return res.status(422).json({ status: 'ROLLBACK', reason: 'INSF_FUNDS_OR_ACCT_NOT_FOUND' });
  }

  debit.balance  = parseFloat((debit.balance - totalDebit).toFixed(2));
  credit.balance = parseFloat((credit.balance + msg.amount).toFixed(2));
  db2.accounts['ACC-FSC-TREASURY'].balance = parseFloat((db2.accounts['ACC-FSC-TREASURY'].balance + fee).toFixed(2));

  const entry = { transId: msg.mqMsgId, ...msg, fee, status: 'SETTLED', procTimestamp: now() };
  db2.ledgerAudit.push(entry);
  res.status(200).json({ status: 'COMMITTED', settlement: entry });
});

// =====================================================================
// START
// =====================================================================
const PORT = 9191;
app.listen(PORT, () => {
  console.log(`\n╔══════════════════════════════════════════════════════╗`);
  console.log(`║   FSC CORE LEDGER — z/OS MAINFRAME SIMULATOR [SECURE]║`);
  console.log(`║   Programas COBOL: FSCCHK | FSCSET | FSCBCH          ║`);
  console.log(`║   Porta: ${PORT}  │  JWT Auth: ENABLED               ║`);
  console.log(`║   JWKS: ${KEYCLOAK_JWKS_URI.slice(0, 42)}  ║`);
  console.log(`╚══════════════════════════════════════════════════════╝\n`);
});
