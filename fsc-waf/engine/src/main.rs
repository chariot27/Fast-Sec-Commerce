mod rules;

use axum::{
    extract::State,
    http::StatusCode,
    response::Json,
    routing::{get, post},
    Router,
};
use chrono::Utc;
use dashmap::DashMap;
use once_cell::sync::Lazy;
use serde::{Deserialize, Serialize};
use std::{
    net::SocketAddr,
    sync::Arc,
    time::{Duration, Instant},
};
use tracing::{info, warn};
use uuid::Uuid;

const RATE_LIMIT_MAX:    usize    = 30;
const RATE_LIMIT_WINDOW: Duration = Duration::from_secs(10);

static CONTROL_PLANE_URL: Lazy<String> = Lazy::new(|| {
    std::env::var("CONTROL_PLANE_URL")
        .unwrap_or_else(|_| "http://localhost:9200/events".to_string())
});

#[derive(Clone)]
struct WafState {
    rate_map:    Arc<DashMap<String, (usize, Instant)>>,
    http_client: reqwest::Client,
}

#[derive(Deserialize, Debug)]
struct CheckRequest {
    client_ip: String,
    method:    String,
    path:      String,
    #[serde(default)] query:   String,
    #[serde(default)] headers: std::collections::HashMap<String, String>,
    #[serde(default)] body:    String,
}

#[derive(Serialize, Clone)]
struct CheckResponse {
    trace_id:  String,
    verdict:   String,         // "ALLOW" | "DENY" | "HONEYPOT"
    rule_id:   Option<String>,
    category:  Option<String>,
    severity:  Option<String>,
    matched:   Option<String>,
    reason:    String,
    timestamp: String,
}

#[derive(Serialize)]
struct WafEvent {
    trace_id:        String,
    client_ip:       String,
    method:          String,
    path:            String,
    verdict:         String,
    rule_id:         Option<String>,
    category:        Option<String>,
    severity:        Option<String>,
    matched:         Option<String>,
    reason:          String,
    timestamp:       String,
    user_agent:      String,
    honeypot_label:  Option<String>,   // nome do honeypot ativado
    needs_investigation: bool,         // flag para fila do admin
}

// ── Handler principal ─────────────────────────────────────────

async fn check_request(
    State(state): State<WafState>,
    Json(req): Json<CheckRequest>,
) -> (StatusCode, Json<CheckResponse>) {
    let trace_id  = Uuid::new_v4().to_string();
    let timestamp = Utc::now().to_rfc3339();
    let user_agent = req.headers
        .get("user-agent").cloned().unwrap_or_default();

    // ── 1. Honeypot check ──────────────────────────────────────
    // Feito ANTES das regras: atacante vê resposta falsa e continua
    // sendo rastreado, enquanto o admin recebe alerta de investigação.
    if let Some(label) = rules::check_honeypot(&req.path) {
        warn!(
            trace_id = %trace_id,
            ip       = %req.client_ip,
            path     = %req.path,
            honeypot = %label,
            "🍯 HONEYPOT ATIVADO"
        );

        // Retorna resposta falsa convincente (não revela que é isca)
        let fake_body = honeypot_fake_response(&req.path, label);
        let resp = CheckResponse {
            trace_id:  trace_id.clone(),
            verdict:   "HONEYPOT".to_string(),
            rule_id:   Some("WAF-HON".to_string()),
            category:  Some("Honeypot Triggered".to_string()),
            severity:  Some("CRITICAL".to_string()),
            matched:   Some(req.path.clone()),
            reason:    format!("Honeypot '{}' ativado — IP marcado para investigação", label),
            timestamp: timestamp.clone(),
        };

        notify_event(&state.http_client, WafEvent {
            trace_id:   trace_id.clone(),
            client_ip:  req.client_ip.clone(),
            method:     req.method.clone(),
            path:       req.path.clone(),
            verdict:    "HONEYPOT".to_string(),
            rule_id:    Some("WAF-HON".to_string()),
            category:   Some("Honeypot Triggered".to_string()),
            severity:   Some("CRITICAL".to_string()),
            matched:    Some(format!("Path: {} | Label: {}", req.path, label)),
            reason:     format!("Honeypot '{}' ativado", label),
            timestamp:  timestamp.clone(),
            user_agent: user_agent.clone(),
            honeypot_label: Some(label.to_string()),
            needs_investigation: true,
        }).await;

        // HTTP 200 com conteúdo falso (engana o atacante)
        // O Envoy receberá ALLOW aqui, mas o payload indica honeypot
        // Em produção, o ext_authz retornaria o fake_body diretamente.
        // Nesta implementação, logamos e permitimos para o upstream
        // onde o simulador responderá com 404 (não existe a rota).
        let _ = fake_body; // usado em modo standalone
        return (StatusCode::OK, Json(resp));
    }

    // ── 2. Rate Limiting ───────────────────────────────────────
    {
        let now = Instant::now();
        let mut entry = state.rate_map
            .entry(req.client_ip.clone())
            .or_insert((0, now));

        if now.duration_since(entry.1) > RATE_LIMIT_WINDOW {
            *entry = (1, now);
        } else {
            entry.0 += 1;
        }

        if entry.0 > RATE_LIMIT_MAX {
            let resp = mk_resp(&trace_id, "DENY", Some("WAF-000"),
                Some("Rate Limit"), Some("MEDIUM"), None,
                "Taxa de requisições excedida", &timestamp);
            notify_event(&state.http_client, WafEvent {
                trace_id: trace_id.clone(), client_ip: req.client_ip.clone(),
                method: req.method.clone(), path: req.path.clone(),
                verdict: "DENY".to_string(), rule_id: Some("WAF-000".to_string()),
                category: Some("Rate Limit".to_string()), severity: Some("MEDIUM".to_string()),
                matched: None, reason: "Taxa excedida".to_string(),
                timestamp: timestamp.clone(), user_agent,
                honeypot_label: None, needs_investigation: false,
            }).await;
            return (StatusCode::TOO_MANY_REQUESTS, Json(resp));
        }
    }

    // ── 3. Inspeção de payload ─────────────────────────────────
    let surfaces: Vec<(&str, &str)> = vec![
        ("path",       &req.path),
        ("query",      &req.query),
        ("body",       &req.body),
        ("user-agent", req.headers.get("user-agent").map(|s| s.as_str()).unwrap_or("")),
        ("referer",    req.headers.get("referer").map(|s| s.as_str()).unwrap_or("")),
        ("cookie",     req.headers.get("cookie").map(|s| s.as_str()).unwrap_or("")),
    ];

    for (surface, value) in &surfaces {
        if value.is_empty() { continue; }

        if *surface == "body" && value.len() > 524_288 {
            let resp = mk_resp(&trace_id, "DENY", Some("WAF-008"),
                Some("Oversized Payload"), Some("MEDIUM"), None,
                &format!("Body {}B excede 512KB", value.len()), &timestamp);
            notify_event(&state.http_client, WafEvent {
                trace_id: trace_id.clone(), client_ip: req.client_ip.clone(),
                method: req.method.clone(), path: req.path.clone(),
                verdict: "DENY".to_string(), rule_id: Some("WAF-008".to_string()),
                category: Some("Oversized Payload".to_string()), severity: Some("MEDIUM".to_string()),
                matched: None, reason: "Payload excessivo".to_string(),
                timestamp: timestamp.clone(), user_agent: user_agent.clone(),
                honeypot_label: None, needs_investigation: false,
            }).await;
            return (StatusCode::PAYLOAD_TOO_LARGE, Json(resp));
        }

        if let Some(v) = rules::inspect(value) {
            // Ataques críticos vão para investigação
            let needs_inv = v.severity == "CRITICAL";
            warn!(
                trace_id = %trace_id, ip = %req.client_ip,
                rule = %v.rule_id, surface = %surface,
                matched = %v.matched, "🚨 WAF BLOQUEOU"
            );
            let reason = format!("'{}' no campo '{}'", v.category, surface);
            let resp = mk_resp(&trace_id, "DENY", Some(v.rule_id),
                Some(v.category), Some(v.severity), Some(&v.matched), &reason, &timestamp);
            notify_event(&state.http_client, WafEvent {
                trace_id: trace_id.clone(), client_ip: req.client_ip.clone(),
                method: req.method.clone(), path: req.path.clone(),
                verdict: "DENY".to_string(), rule_id: Some(v.rule_id.to_string()),
                category: Some(v.category.to_string()), severity: Some(v.severity.to_string()),
                matched: Some(v.matched.clone()), reason: reason.clone(),
                timestamp: timestamp.clone(), user_agent: user_agent.clone(),
                honeypot_label: None, needs_investigation: needs_inv,
            }).await;
            return (StatusCode::FORBIDDEN, Json(resp));
        }
    }

    // ── 4. ALLOW ──────────────────────────────────────────────
    info!(trace_id = %trace_id, ip = %req.client_ip, path = %req.path, "✅ PERMITIDO");
    let resp = mk_resp(&trace_id, "ALLOW", None, None, None, None,
        "Aprovada por todas as regras", &timestamp);
    notify_event(&state.http_client, WafEvent {
        trace_id: trace_id.clone(), client_ip: req.client_ip.clone(),
        method: req.method.clone(), path: req.path.clone(),
        verdict: "ALLOW".to_string(), rule_id: None, category: None,
        severity: None, matched: None, reason: "Aprovada".to_string(),
        timestamp: timestamp.clone(), user_agent: user_agent.clone(),
        honeypot_label: None, needs_investigation: false,
    }).await;
    (StatusCode::OK, Json(resp))
}

// ── Fake response para honeypots (engana o atacante) ─────────

fn honeypot_fake_response(path: &str, label: &str) -> String {
    let path_l = path.to_lowercase();
    if path_l.contains(".env") {
        return r#"APP_ENV=production
DB_HOST=db.internal
DB_PASSWORD=REDACTED
SECRET_KEY=REDACTED"#.to_string();
    }
    if path_l.contains("wp-") || path_l.contains("wordpress") {
        return "<html><body>WordPress 6.4 — Login</body></html>".to_string();
    }
    if path_l.contains("passwd") {
        return "root:x:0:0:root:/root:/bin/bash\ndaemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin".to_string();
    }
    format!("<!-- {} -->", label)
}

// ── Helpers ──────────────────────────────────────────────────

fn mk_resp(
    trace_id: &str, verdict: &str, rule_id: Option<&str>,
    category: Option<&str>, severity: Option<&str>, matched: Option<&str>,
    reason: &str, timestamp: &str,
) -> CheckResponse {
    CheckResponse {
        trace_id:  trace_id.to_string(),
        verdict:   verdict.to_string(),
        rule_id:   rule_id.map(String::from),
        category:  category.map(String::from),
        severity:  severity.map(String::from),
        matched:   matched.map(String::from),
        reason:    reason.to_string(),
        timestamp: timestamp.to_string(),
    }
}

async fn notify_event(client: &reqwest::Client, event: WafEvent) {
    let _ = client
        .post(CONTROL_PLANE_URL.as_str())
        .json(&event)
        .timeout(Duration::from_millis(50))
        .send().await;
}

async fn health() -> Json<serde_json::Value> {
    Json(serde_json::json!({
        "status": "UP",
        "service": "fsc-waf-engine",
        "layer": "Rust · Deep Packet Inspection + Honeypots",
        "honeypot_count": 32,
        "rules": ["WAF-HON Honeypot","WAF-000 Rate Limit","WAF-001 Log4Shell",
                  "WAF-002 SQLi","WAF-003 RCE","WAF-004 Path Traversal",
                  "WAF-005 XSS","WAF-006 SSRF","WAF-007 SSTI","WAF-008 Oversized"],
        "timestamp": Utc::now().to_rfc3339()
    }))
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt().with_env_filter("info").compact().init();

    let state = WafState {
        rate_map:    Arc::new(DashMap::new()),
        http_client: reqwest::Client::new(),
    };

    let app = Router::new()
        .route("/check",  post(check_request))
        .route("/health", get(health))
        .with_state(state);

    let addr: SocketAddr = "0.0.0.0:9100".parse().unwrap();
    info!("╔══════════════════════════════════════════════════════╗");
    info!("║  FSC WAF ENGINE — Rust DPI + Honeypot Manager       ║");
    info!("║  Porta: 9100 │ Regras: 10 │ Honeypots: 32          ║");
    info!("╚══════════════════════════════════════════════════════╝");

    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
