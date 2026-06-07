package main

const dashboardHTML = `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>FSC WAF — Admin Observability</title>
<style>
:root{--bg:#070b14;--card:#0d1117;--border:#ffffff0d;--accent:#00f0ff;--red:#ef4444;--green:#22c55e;--yellow:#f59e0b;--honey:#f97316;--purple:#a78bfa}
*{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:#e5e7eb;font-family:'Segoe UI',system-ui,sans-serif;min-height:100vh}
header{border-bottom:1px solid var(--border);padding:.85rem 2rem;display:flex;align-items:center;gap:.75rem;position:sticky;top:0;background:var(--bg)85;backdrop-filter:blur(12px);z-index:20}
.logo{width:34px;height:34px;background:#00f0ff12;border:1px solid #00f0ff35;border-radius:8px;display:grid;place-items:center;font-weight:800;color:var(--accent);font-size:.9rem}
.badge-alert{background:#ef444420;border:1px solid #ef444445;color:var(--red);border-radius:6px;padding:.15rem .5rem;font-size:.7rem;font-weight:700;margin-left:.5rem;animation:pulse 1.5s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.5}}
.dot{width:8px;height:8px;border-radius:50%;background:var(--green);box-shadow:0 0 8px var(--green);margin-left:auto;animation:pulse 2s infinite}
nav{display:flex;gap:.25rem;padding:.6rem 2rem;border-bottom:1px solid var(--border);overflow-x:auto}
nav button{background:none;border:1px solid transparent;color:#6b7280;padding:.4rem .9rem;border-radius:6px;cursor:pointer;font-size:.8rem;white-space:nowrap;transition:all .2s}
nav button.active,nav button:hover{background:#ffffff08;border-color:var(--border);color:#e5e7eb}
nav button.active{color:var(--accent);border-color:#00f0ff25}
main{padding:1.5rem 2rem;max-width:1500px;margin:auto}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:.85rem;margin-bottom:1.5rem}
.card{background:var(--card);border:1px solid var(--border);border-radius:10px;padding:1.1rem}
.card-label{font-size:.6rem;text-transform:uppercase;letter-spacing:.12em;color:#6b7280;margin-bottom:.4rem}
.card-value{font-size:1.75rem;font-weight:300}
.cyan{color:var(--accent)}
.red{color:var(--red)}
.green{color:var(--green)}
.honey{color:var(--honey);text-shadow:0 0 10px var(--honey)}
.yellow{color:var(--yellow)}
.purple{color:var(--purple)}
.panel{display:none}
.panel.active{display:block}
.section-title{font-size:.7rem;text-transform:uppercase;letter-spacing:.12em;color:#6b7280;margin-bottom:.85rem}
.table-wrap{background:var(--card);border:1px solid var(--border);border-radius:10px;overflow:hidden;overflow-x:auto}
table{width:100%;border-collapse:collapse;font-size:.78rem;min-width:600px}
thead tr{border-bottom:1px solid var(--border)}
th{padding:.6rem .75rem;text-align:left;color:#6b7280;font-weight:500;font-size:.7rem;text-transform:uppercase;letter-spacing:.08em}
td{padding:.55rem .75rem;border-bottom:1px solid #ffffff04}
tbody tr:hover{background:#ffffff04}
.badge{display:inline-block;padding:.1rem .45rem;border-radius:4px;font-size:.62rem;font-weight:700;border:1px solid}
.bd{color:var(--red);border-color:#ef444430;background:#ef444408}
.ba{color:var(--green);border-color:#22c55e30;background:#22c55e08}
.bh{color:var(--honey);border-color:#f9731630;background:#f9731608}
.bc{color:#f97316;border-color:#f9731630}
.bhi{color:var(--yellow);border-color:#f59e0b30}
.bme{color:var(--purple);border-color:#a78bfa30}
.mono{font-family:monospace;font-size:.72rem;color:#9ca3af}
.attacker-row td:first-child{font-weight:600}
.resolve-btn{background:none;border:1px solid #22c55e30;color:var(--green);padding:.2rem .5rem;border-radius:4px;cursor:pointer;font-size:.68rem}
.resolve-btn:hover{background:#22c55e12}
.inv-badge{display:inline-flex;align-items:center;gap:.3rem;padding:.2rem .5rem;border-radius:6px;background:#ef444415;border:1px solid #ef444430;color:var(--red);font-size:.65rem;font-weight:700}
.honeypot-path{color:var(--honey);font-family:monospace;font-size:.72rem}
.empty{color:#374151;text-align:center;padding:2.5rem;font-size:.8rem}
</style>
</head>
<body>
<header>
  <div class="logo">W</div>
  <div>
    <div style="font-size:.62rem;text-transform:uppercase;letter-spacing:.1em;color:#6b7280">FSC WAF</div>
    <div style="font-size:.85rem;font-weight:600">Admin Observability Panel</div>
  </div>
  <span class="badge-alert" id="pending-badge" style="display:none">⚠ <span id="pending-count">0</span> para investigar</span>
  <div class="dot"></div>
</header>

<nav>
  <button class="active" onclick="showPanel('overview')">📊 Overview</button>
  <button onclick="showPanel('investigation')">🔍 Investigação</button>
  <button onclick="showPanel('honeypots')">🍯 Honeypots</button>
  <button onclick="showPanel('attackers')">👤 Perfis de Atacantes</button>
  <button onclick="showPanel('events')">📋 Todos os Eventos</button>
</nav>

<main>
<!-- OVERVIEW -->
<div id="panel-overview" class="panel active">
  <div class="grid">
    <div class="card"><div class="card-label">Total Inspecionadas</div><div class="card-value cyan" id="s-total">0</div></div>
    <div class="card"><div class="card-label">Bloqueadas</div><div class="card-value red" id="s-blocked">0</div></div>
    <div class="card"><div class="card-label">Permitidas</div><div class="card-value green" id="s-allowed">0</div></div>
    <div class="card"><div class="card-label">🍯 Honeypots</div><div class="card-value honey" id="s-honey">0</div></div>
    <div class="card"><div class="card-label">⚠ Pendentes</div><div class="card-value yellow" id="s-pending">0</div></div>
    <div class="card"><div class="card-label">SQLi</div><div class="card-value red" id="s-sqli">0</div></div>
    <div class="card"><div class="card-label">XSS</div><div class="card-value red" id="s-xss">0</div></div>
    <div class="card"><div class="card-label">RCE</div><div class="card-value red" id="s-rce">0</div></div>
    <div class="card"><div class="card-label">SSRF</div><div class="card-value purple" id="s-ssrf">0</div></div>
    <div class="card"><div class="card-label">Rate Limit</div><div class="card-value" id="s-rate">0</div></div>
  </div>
  <div class="section-title">Últimos Eventos (live · 3s)</div>
  <div class="table-wrap"><table>
    <thead><tr><th>Veredicto</th><th>IP</th><th>Método</th><th>Path</th><th>Categoria</th><th>Sev.</th><th>Matched</th><th>Hora</th></tr></thead>
    <tbody id="ev-overview"></tbody>
  </table></div>
</div>

<!-- INVESTIGATION QUEUE -->
<div id="panel-investigation" class="panel">
  <div class="section-title">Fila de Investigação — Eventos que requerem análise do admin</div>
  <div class="table-wrap"><table>
    <thead><tr><th>Tipo</th><th>IP</th><th>Path</th><th>Categoria</th><th>Matched</th><th>User-Agent</th><th>Hora</th><th>Ação</th></tr></thead>
    <tbody id="ev-investigation"></tbody>
  </table></div>
</div>

<!-- HONEYPOTS -->
<div id="panel-honeypots" class="panel">
  <div class="section-title">🍯 Honeypot Triggers — IPs que acessaram rotas de isca</div>
  <div class="table-wrap"><table>
    <thead><tr><th>IP</th><th>Honeypot Ativado</th><th>Path Acessado</th><th>User-Agent</th><th>Hora</th></tr></thead>
    <tbody id="ev-honeypots"></tbody>
  </table></div>
</div>

<!-- ATTACKER PROFILES -->
<div id="panel-attackers" class="panel">
  <div class="section-title">Perfis de Atacantes — IPs com atividade maliciosa detectada</div>
  <div class="table-wrap"><table>
    <thead><tr><th>IP</th><th>🍯 Honeypots</th><th>🚫 Bloqueios</th><th>Req. Total</th><th>Último acesso</th><th>Paths</th><th>User-Agents</th></tr></thead>
    <tbody id="ev-attackers"></tbody>
  </table></div>
</div>

<!-- ALL EVENTS -->
<div id="panel-events" class="panel">
  <div class="section-title">Todos os Eventos (últimos 50)</div>
  <div class="table-wrap"><table>
    <thead><tr><th>Veredicto</th><th>IP</th><th>Método</th><th>Path</th><th>Categoria</th><th>Sev.</th><th>Matched</th><th>Hora</th></tr></thead>
    <tbody id="ev-all"></tbody>
  </table></div>
</div>
</main>

<script>
function showPanel(name){
  document.querySelectorAll('.panel').forEach(p=>p.classList.remove('active'));
  document.querySelectorAll('nav button').forEach(b=>b.classList.remove('active'));
  document.getElementById('panel-'+name).classList.add('active');
  event.target.classList.add('active');
}

function verdictBadge(v){
  if(v==='DENY')    return '<span class="badge bd">DENY</span>';
  if(v==='HONEYPOT')return '<span class="badge bh">🍯 HONEYPOT</span>';
  return '<span class="badge ba">ALLOW</span>';
}
function sevBadge(s){
  if(!s)return '—';
  const cls=s==='CRITICAL'?'bc':s==='HIGH'?'bhi':'bme';
  return '<span class="badge '+cls+'">'+s+'</span>';
}
function mono(s,max=40){
  if(!s)return '<span style="color:#374151">—</span>';
  const t=s.length>max?s.slice(0,max)+'…':s;
  return '<span class="mono" title="'+s.replace(/"/g,'&quot;')+'">'+t+'</span>';
}
function ts(t){return t?new Date(t).toLocaleTimeString('pt-BR'):''}

function renderEvents(data,tbodyId){
  const tb=document.getElementById(tbodyId);
  if(!data||!data.length){tb.innerHTML='<tr><td colspan="8" class="empty">Nenhum evento ainda.</td></tr>';return;}
  tb.innerHTML=data.slice(0,50).map(e=>'<tr>'
    +'<td>'+verdictBadge(e.verdict)+'</td>'
    +'<td class="mono">'+e.client_ip+'</td>'
    +'<td>'+e.method+'</td>'
    +'<td>'+mono(e.path,36)+'</td>'
    +'<td>'+(e.category||'—')+'</td>'
    +'<td>'+sevBadge(e.severity)+'</td>'
    +'<td>'+mono(e.matched,32)+'</td>'
    +'<td class="mono">'+ts(e.timestamp)+'</td>'
    +'</tr>').join('');
}

async function fetchStats(){
  try{
    const d=await(await fetch('/api/stats')).json();
    document.getElementById('s-total').textContent=d.total_requests.toLocaleString('pt-BR');
    document.getElementById('s-blocked').textContent=d.total_blocked.toLocaleString('pt-BR');
    document.getElementById('s-allowed').textContent=d.total_allowed.toLocaleString('pt-BR');
    document.getElementById('s-honey').textContent=d.total_honeypots.toLocaleString('pt-BR');
    document.getElementById('s-pending').textContent=d.pending_review.toLocaleString('pt-BR');
    document.getElementById('s-sqli').textContent=d.sqli_blocked.toLocaleString('pt-BR');
    document.getElementById('s-xss').textContent=d.xss_blocked.toLocaleString('pt-BR');
    document.getElementById('s-rce').textContent=d.rce_blocked.toLocaleString('pt-BR');
    document.getElementById('s-ssrf').textContent=d.ssrf_blocked.toLocaleString('pt-BR');
    document.getElementById('s-rate').textContent=d.rate_limited.toLocaleString('pt-BR');
    const p=d.pending_review;
    document.getElementById('pending-count').textContent=p;
    document.getElementById('pending-badge').style.display=p>0?'inline-flex':'none';
  }catch(e){}
}

async function fetchEvents(){
  try{
    const d=await(await fetch('/api/events')).json();
    renderEvents(d,'ev-overview');
    renderEvents(d,'ev-all');
  }catch(e){}
}

async function fetchInvestigation(){
  try{
    const d=await(await fetch('/api/investigation')).json();
    const tb=document.getElementById('ev-investigation');
    if(!d||!d.length){tb.innerHTML='<tr><td colspan="8" class="empty">✅ Nenhum item pendente de investigação.</td></tr>';return;}
    tb.innerHTML=d.map(e=>'<tr>'
      +'<td>'+verdictBadge(e.verdict)+'</td>'
      +'<td class="mono">'+e.client_ip+'</td>'
      +'<td>'+mono(e.path,40)+'</td>'
      +'<td>'+(e.category||'—')+'</td>'
      +'<td>'+mono(e.matched,30)+'</td>'
      +'<td>'+mono(e.user_agent,28)+'</td>'
      +'<td class="mono">'+ts(e.timestamp)+'</td>'
      +'<td><button class="resolve-btn" onclick="resolve(\''+e.trace_id+'\')">✓ Resolver</button></td>'
      +'</tr>').join('');
  }catch(e){}
}

async function fetchHoneypots(){
  try{
    const d=await(await fetch('/api/events')).json();
    const honey=d.filter(e=>e.verdict==='HONEYPOT');
    const tb=document.getElementById('ev-honeypots');
    if(!honey.length){tb.innerHTML='<tr><td colspan="5" class="empty">🍯 Nenhum honeypot ativado ainda.</td></tr>';return;}
    tb.innerHTML=honey.map(e=>'<tr>'
      +'<td class="mono" style="color:var(--honey)">'+e.client_ip+'</td>'
      +'<td><span class="badge bh">'+(e.honeypot_label||'—')+'</span></td>'
      +'<td class="honeypot-path">'+e.path+'</td>'
      +'<td>'+mono(e.user_agent,36)+'</td>'
      +'<td class="mono">'+ts(e.timestamp)+'</td>'
      +'</tr>').join('');
  }catch(e){}
}

async function fetchAttackers(){
  try{
    const d=await(await fetch('/api/attackers')).json();
    const tb=document.getElementById('ev-attackers');
    if(!d||!d.length){tb.innerHTML='<tr><td colspan="7" class="empty">Nenhum atacante registrado.</td></tr>';return;}
    tb.innerHTML=d.map(a=>'<tr class="attacker-row">'
      +'<td class="mono" style="color:var(--red)">'+a.ip+'</td>'
      +'<td style="color:var(--honey);font-weight:700">'+a.honeypot_hits+'</td>'
      +'<td style="color:var(--red)">'+a.blocked_hits+'</td>'
      +'<td>'+a.total_requests+'</td>'
      +'<td class="mono">'+ts(a.last_seen)+'</td>'
      +'<td>'+mono((a.paths_accessed||[]).join(', '),42)+'</td>'
      +'<td>'+mono((a.user_agents||[]).join(', '),36)+'</td>'
      +'</tr>').join('');
  }catch(e){}
}

async function resolve(traceId){
  try{
    await fetch('/api/investigation/resolve?trace_id='+traceId,{method:'POST'});
    fetchInvestigation();fetchStats();
  }catch(e){}
}

function refresh(){
  fetchStats();fetchEvents();fetchInvestigation();fetchHoneypots();fetchAttackers();
}

refresh();
setInterval(refresh,3000);
</script>
</body>
</html>`
