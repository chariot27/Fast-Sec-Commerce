/**
 * FSC Core Ledger — Mainframe Simulator
 * Porta: 9191
 * Swagger UI estatico disponivel em: /api-docs
 */

import express from 'express';
import cors from 'cors';
import { v4 as uuidv4 } from 'uuid';
import jwt from 'jsonwebtoken';
import jwksClient from 'jwks-rsa';

const app = express();

app.use(cors({
  origin: process.env.CORS_ORIGIN || 'http://localhost:5173',
  methods: ['GET', 'POST'],
  allowedHeaders: ['Authorization', 'Content-Type'],
}));
app.use(express.json({ limit: '64kb' }));

// Remover header que vaza versao do servidor
app.disable('x-powered-by');

// =====================================================================
// JWT / KEYCLOAK
// =====================================================================
const KEYCLOAK_JWKS_URI = process.env.KEYCLOAK_JWKS_URI ||
  'http://localhost:8080/realms/fsc/protocol/openid-connect/certs';
const KEYCLOAK_ISSUER = process.env.KEYCLOAK_ISSUER ||
  'http://localhost:8080/realms/fsc';

const jwks = jwksClient({
  jwksUri: KEYCLOAK_JWKS_URI,
  cache: true,
  cacheMaxEntries: 5,
  cacheMaxAge: 600000,
});

function getSigningKey(header, callback) {
  jwks.getSigningKey(header.kid, (err, key) => {
    if (err) return callback(err);
    callback(null, key.getPublicKey());
  });
}

function requireAuth(req, res, next) {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'UNAUTHORIZED', message: 'Authorization Bearer token obrigatorio.' });
  }
  const token = authHeader.split(' ')[1];
  jwt.verify(token, getSigningKey, { algorithms: ['RS256'], issuer: KEYCLOAK_ISSUER }, (err, decoded) => {
    if (err) return res.status(401).json({ error: 'INVALID_TOKEN', message: `Token invalido: ${err.message}` });
    req.user = decoded;
    next();
  });
}

// =====================================================================
// INPUT SANITIZATION
// =====================================================================
function sanitizeString(value, maxLen = 64) {
  if (typeof value !== 'string') return '';
  return value
    .replace(/[<>"'`&]/g, '')
    .replace(/[^\w\s\-\.]/g, '')
    .trim()
    .slice(0, maxLen);
}

function sanitizeAmount(value) {
  const n = parseFloat(value);
  if (isNaN(n) || n <= 0 || n > 999999999) return null;
  return parseFloat(n.toFixed(2));
}

// =====================================================================
// DB2 IN-MEMORY SIMULATOR
// =====================================================================
const db2 = {
  accounts: {
    'ACC-001-BUYER':    { id: 'ACC-001-BUYER',    type: 'BUYER',    balance: 50000.00, reserved: 0, status: 'ACTIVE',    owner: 'Joao Silva' },
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
// SWAGGER UI ESTATICO (OpenAPI 3)
// =====================================================================
const openApiSpec = {
  openapi: '3.0.3',
  info: {
    title: 'FSC Core Ledger — Mainframe Simulator API',
    version: '1.0.0',
    description: 'API REST que simula os programas COBOL FSCCHK, FSCSET e FSCBCH do z/OS Connect EE. ' +
                 'Todas as rotas sensiveis requerem Bearer JWT RS256 do Keycloak (realm: fsc).',
    contact: { name: 'FSC Engineering', url: 'https://github.com/chariot27/Fast-Sec-Commerce' },
    license: { name: 'MIT', url: 'https://opensource.org/licenses/MIT' },
  },
  servers: [{ url: 'http://localhost:9191', description: 'Local Development' }],
  components: {
    securitySchemes: {
      bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT',
                    description: 'JWT RS256 emitido pelo Keycloak. Realm: fsc.' },
    },
    schemas: {
      SettleRequest: {
        type: 'object', required: ['debitAcct', 'creditAcct', 'amount'],
        properties: {
          orderId:    { type: 'string', example: 'ORD-20240601-001' },
          debitAcct:  { type: 'string', example: 'ACC-001-BUYER' },
          creditAcct: { type: 'string', example: 'ACC-003-MERCHANT' },
          amount:     { type: 'number', format: 'double', example: 250.00 },
          currency:   { type: 'string', example: 'BRL', default: 'BRL' },
        },
      },
      BalanceResponse: {
        type: 'object',
        properties: {
          sqlcode:          { type: 'integer', example: 0 },
          sqlerrm:          { type: 'string', example: 'SUCCESSFUL' },
          fscResReturnCode: { type: 'integer', example: 0 },
          fscResReasonCode: { type: 'string', example: 'OK  ' },
          fscResBalance:    { type: 'number', example: 50000.00 },
          fscResAvailable:  { type: 'number', example: 50000.00 },
          fscResStatus:     { type: 'string', example: 'ACTIVE' },
          fscResOwner:      { type: 'string', example: 'Joao Silva' },
          fscResMsg:        { type: 'string', example: 'BALANCE CHECK SUCCESSFUL' },
        },
      },
      SettleResponse: {
        type: 'object',
        properties: {
          sqlcode:          { type: 'integer', example: 0 },
          fscResReturnCode: { type: 'integer', example: 0 },
          fscResReasonCode: { type: 'string', example: 'SETL' },
          fscResMsg:        { type: 'string', example: 'EXEC CICS SYNCPOINT - DEBIT/CREDIT COMMITTED TO DB2' },
          settlement: {
            type: 'object',
            properties: {
              transId:      { type: 'string', format: 'uuid' },
              orderId:      { type: 'string' },
              amount:       { type: 'number' },
              fee:          { type: 'number' },
              totalDebit:   { type: 'number' },
              status:       { type: 'string', example: 'SETTLED' },
              procTimestamp:{ type: 'string', format: 'date-time' },
            },
          },
        },
      },
    },
  },
  security: [{ bearerAuth: [] }],
  paths: {
    '/zosconnect/health': {
      get: {
        tags: ['System'],
        summary: 'Health check do simulador z/OS',
        description: 'Endpoint publico para Prometheus e Docker healthcheck. Nao requer autenticacao.',
        security: [],
        responses: {
          '200': { description: 'Simulador operacional',
            content: { 'application/json': { example: {
              status: 'UP', system: 'FSC-Core-Ledger-Simulator',
              cobolPrograms: ['FSCCHK', 'FSCSET', 'FSCBCH'],
              db2Tables: ['FSC.ACCOUNTS', 'FSC.LEDGER_AUDIT'],
              mqQueueDepth: 0, timestamp: '2024-01-01T00:00:00.000Z',
            }}}},
        },
      },
    },
    '/zosconnect/accounts': {
      get: {
        tags: ['Ledger'],
        summary: 'DB2 Snapshot — lista de contas',
        description: 'Retorna todas as contas do ledger em memoria (equivalente: SELECT * FROM FSC.ACCOUNTS). Requer JWT.',
        responses: {
          '200': { description: 'Lista de contas retornada com sucesso' },
          '401': { description: 'Token JWT ausente ou invalido' },
        },
      },
    },
    '/zosconnect/fscchk/balance/{accountId}': {
      get: {
        tags: ['FSCCHK'],
        summary: 'FSCCHK — Verificar saldo de conta',
        description: 'Programa COBOL FSCCHK via CICS REST. Executa EXEC SQL SELECT FROM FSC.ACCOUNTS. ' +
                     'Retorna SQLCODE, REASON-CODE, saldo total e disponivel.',
        parameters: [
          { name: 'accountId', in: 'path', required: true, schema: { type: 'string' }, example: 'ACC-001-BUYER',
            description: 'ID da conta no formato ACC-XXX-TYPE' },
          { name: 'amount', in: 'query', required: false, schema: { type: 'number' }, example: 1000.00,
            description: 'Valor a verificar disponibilidade (opcional)' },
        ],
        responses: {
          '200': { description: 'Saldo verificado com sucesso',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/BalanceResponse' }}}},
          '401': { description: 'Token JWT ausente ou invalido' },
          '404': { description: 'Conta nao encontrada no ledger (SQLCODE +100)' },
        },
      },
    },
    '/zosconnect/fscset/settle': {
      post: {
        tags: ['FSCSET'],
        summary: 'FSCSET — Liquidacao atomica entre contas',
        description: 'Programa COBOL FSCSET. Simula EXEC CICS SYNCPOINT com debito/credito atomico no DB2. ' +
                     'Calcula taxa de 1,5% (minimo R$0,50) automaticamente. Idempotente por transId.',
        requestBody: {
          required: true,
          content: { 'application/json': { schema: { $ref: '#/components/schemas/SettleRequest' },
            example: { orderId: 'ORD-20240601-001', debitAcct: 'ACC-001-BUYER',
                       creditAcct: 'ACC-003-MERCHANT', amount: 250.00, currency: 'BRL' }}},
        },
        responses: {
          '201': { description: 'Liquidacao executada — SYNCPOINT COMMITTED',
            content: { 'application/json': { schema: { $ref: '#/components/schemas/SettleResponse' }}}},
          '400': { description: 'Valor invalido ou fora do limite' },
          '401': { description: 'Token JWT ausente ou invalido' },
          '404': { description: 'Conta debitada ou creditada nao encontrada' },
          '422': { description: 'Saldo insuficiente — SYNCPOINT ROLLBACK emitido' },
        },
      },
    },
    '/zosconnect/fscbch/daily-report': {
      get: {
        tags: ['FSCBCH'],
        summary: 'FSCBCH — Relatorio batch diario',
        description: 'Programa JCL FSCBCH. Retorna SELECT COUNT(*), SUM(amount), SUM(fee) FROM FSC.LEDGER_AUDIT ' +
                     'agrupado pela data atual. Inclui output SYSPRINT formatado.',
        responses: {
          '200': { description: 'Relatorio gerado com sucesso' },
          '401': { description: 'Token JWT ausente ou invalido' },
        },
      },
    },
    '/zosconnect/ledger-events': {
      get: {
        tags: ['Ledger'],
        summary: 'SSE Stream — eventos do ledger em tempo real',
        description: 'Server-Sent Events. Emite snapshot do ledger a cada 5 segundos: ' +
                     'totalSettled, totalAmount, totalFees, mqQueueDepth.',
        responses: {
          '200': { description: 'Stream SSE iniciado. Content-Type: text/event-stream' },
          '401': { description: 'Token JWT ausente ou invalido' },
        },
      },
    },
    '/zosconnect/mq/enqueue': {
      post: {
        tags: ['IBM MQ'],
        summary: 'Enfileirar mensagem na fila IBM MQ simulada',
        description: 'Adiciona uma mensagem de liquidacao na fila MQ em memoria. Retorna mqMsgId e depth atual.',
        responses: {
          '202': { description: 'Mensagem enfileirada com sucesso' },
          '401': { description: 'Token JWT ausente ou invalido' },
        },
      },
    },
    '/zosconnect/mq/process-next': {
      post: {
        tags: ['IBM MQ'],
        summary: 'Processar proxima mensagem da fila MQ',
        description: 'Consome e processa a proxima mensagem da fila MQ. Executa liquidacao automatica.',
        responses: {
          '200': { description: 'Mensagem processada e liquidacao commitada' },
          '204': { description: 'Fila vazia — nenhuma mensagem para processar' },
          '401': { description: 'Token JWT ausente ou invalido' },
          '422': { description: 'Saldo insuficiente ou conta nao encontrada — ROLLBACK' },
        },
      },
    },
  },
};

// Servir spec OpenAPI JSON
app.get('/v3/api-docs', (req, res) => {
  res.json(openApiSpec);
});

// Servir Swagger UI via CDN
app.get('/api-docs', (req, res) => {
  res.setHeader('Content-Type', 'text/html');
  res.send(`<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <title>FSC Core Ledger — API Docs</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.17.14/swagger-ui.css">
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://unpkg.com/swagger-ui-dist@5.17.14/swagger-ui-bundle.js"></script>
  <script src="https://unpkg.com/swagger-ui-dist@5.17.14/swagger-ui-standalone-preset.js"></script>
  <script>
    SwaggerUIBundle({
      url: '/v3/api-docs',
      dom_id: '#swagger-ui',
      presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
      layout: 'StandaloneLayout',
      tryItOutEnabled: true,
      displayRequestDuration: true,
      tagsSorter: 'alpha',
    });
  </script>
</body>
</html>`);
});

// =====================================================================
// ROTAS PUBLICAS
// =====================================================================
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

// SSE (requer auth)
app.get('/zosconnect/ledger-events', requireAuth, (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Accel-Buffering', 'no');
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
// ROTAS PROTEGIDAS
// =====================================================================
app.get('/zosconnect/accounts', requireAuth, (req, res) => {
  res.json(Object.values(db2.accounts));
});

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

app.post('/zosconnect/fscset/settle', requireAuth, (req, res) => {
  const rawOrderId    = req.body.orderId || `ORD-${Date.now()}`;
  const rawDebitAcct  = req.body.debitAcct;
  const rawCreditAcct = req.body.creditAcct;
  const rawAmount     = req.body.amount;

  const transId    = uuidv4();
  const orderId    = sanitizeString(rawOrderId, 64);
  const debitAcct  = sanitizeString(rawDebitAcct, 30);
  const creditAcct = sanitizeString(rawCreditAcct, 30);
  const amount     = sanitizeAmount(rawAmount);
  const currency   = sanitizeString(req.body.currency || 'BRL', 3);
  const auditHash  = uuidv4().replace(/-/g, '');

  if (!amount) {
    return res.status(400).json({ error: 'INVALID_AMOUNT', message: 'Valor invalido ou fora do limite permitido.' });
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

  const duplicate = db2.ledgerAudit.find(r => r.orderId === orderId && r.debitAcct === debitAcct);
  if (duplicate) {
    return res.status(200).json({
      ...cobolSqlcode(-803, 'DUPLICATE KEY - IDEMPOTENT IGNORE'),
      fscResReturnCode: 0,
      fscResReasonCode: 'DUPL',
      fscResMsg: 'TRANSACTION ALREADY SETTLED - IDEMPOTENT RETURN',
      settlement: duplicate,
    });
  }

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
const PORT = process.env.PORT || 9191;
app.listen(PORT, () => {
  console.log('\n======================================================');
  console.log('  FSC CORE LEDGER — z/OS MAINFRAME SIMULATOR [SECURE]');
  console.log('  Programas COBOL: FSCCHK | FSCSET | FSCBCH');
  console.log(`  Porta: ${PORT}  |  JWT Auth: ENABLED`);
  console.log(`  Swagger UI: http://localhost:${PORT}/api-docs`);
  console.log(`  OpenAPI JSON: http://localhost:${PORT}/v3/api-docs`);
  console.log('======================================================\n');
});
