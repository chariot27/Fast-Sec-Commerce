// ============================================================
// FSC WAF — Rules Engine + Honeypot Detection (Rust)
// ============================================================

use regex::Regex;
use once_cell::sync::Lazy;

#[derive(Debug, Clone)]
pub struct RuleViolation {
    pub rule_id:  &'static str,
    pub category: &'static str,
    pub severity: &'static str,
    pub matched:  String,
}

// ── Padrões OWASP ────────────────────────────────────────────

static SQLI: Lazy<Regex> = Lazy::new(|| Regex::new(
    r"(?i)(\bunion\b.{0,30}\bselect\b|\bselect\b.{0,30}\bfrom\b|\bdrop\b.{0,10}\btable\b|\binsert\b.{0,10}\binto\b|\bdelete\b.{0,10}\bfrom\b|--\s|#\s*$|'\s*or\s*'?\d|1\s*=\s*1|;\s*shutdown|xp_cmdshell|exec\s*\(|cast\s*\(|waitfor\s+delay|sleep\s*\(|benchmark\s*\()"
).unwrap());

static XSS: Lazy<Regex> = Lazy::new(|| Regex::new(
    r#"(?i)(<\s*script[\s>]|<\s*img[^>]+onerror|javascript\s*:|on(load|click|mouseover|error|focus|blur|input|change|submit|keydown|keyup)\s*=|<\s*iframe|<\s*object|<\s*embed|alert\s*\(|document\.(cookie|location|write)|eval\s*\()"#
).unwrap());

static RCE: Lazy<Regex> = Lazy::new(|| Regex::new(
    r"(?i)(;|\||`|\$\()\s*(ls|cat|id|whoami|uname|wget|curl|bash|sh|python|perl|nc|netcat|chmod|rm\s+-rf|/etc/passwd|/proc/self|/bin/sh)|(\.\./){2,}|cmd\.exe|powershell"
).unwrap());

static PATH_TRAVERSAL: Lazy<Regex> = Lazy::new(|| Regex::new(
    r"(?i)(\.\.[\\/]){2,}|(\.\.%2f){2,}|(%2e%2e%2f){2,}|/etc/(passwd|shadow|hosts|crontab)|/proc/(self|version)"
).unwrap());

static SSRF: Lazy<Regex> = Lazy::new(|| Regex::new(
    r"(?i)(http|ftp|gopher|file)://\s*(localhost|127\.0\.0\.1|0\.0\.0\.0|::1|169\.254\.|10\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.|192\.168\.|169\.254\.169\.254)"
).unwrap());

static SSTI: Lazy<Regex> = Lazy::new(|| Regex::new(
    r"(?i)(\{\{.{0,50}config|__class__|__mro__|__subclasses__|__import__|subprocess|os\.system|\{\%\s*(for|if|set|import))"
).unwrap());

static LOG4SHELL: Lazy<Regex> = Lazy::new(|| Regex::new(
    r"(?i)\$\{(jndi|lower|upper|::-|\$\{)"
).unwrap());

// ── Honeypot Paths ───────────────────────────────────────────
// Caminhos que nenhuma aplicação legítima deveria acessar.
// Acesso = atacante ou scanner automático.

static HONEYPOT_PATHS: &[(&str, &str)] = &[
    // WordPress / CMS
    ("/wp-admin",           "WordPress Admin Panel"),
    ("/wp-login.php",       "WordPress Login"),
    ("/wp-config.php",      "WordPress Config"),
    ("/xmlrpc.php",         "WordPress XMLRPC"),
    // Arquivos de configuração sensíveis
    ("/.env",               "Environment File"),
    ("/.env.backup",        "Environment Backup"),
    ("/.git/config",        "Git Repository"),
    ("/.git/HEAD",          "Git Repository"),
    ("/.aws/credentials",   "AWS Credentials"),
    ("/.ssh/id_rsa",        "SSH Private Key"),
    // Painéis de banco de dados
    ("/phpmyadmin",         "phpMyAdmin"),
    ("/phpMyAdmin",         "phpMyAdmin"),
    ("/adminer",            "Adminer DB"),
    ("/h2-console",         "H2 Database Console"),
    // Shell e execução
    ("/shell",              "Remote Shell"),
    ("/cmd",                "Command Execution"),
    ("/exec",               "Remote Execution"),
    ("/cgi-bin/",           "CGI Execution"),
    // Actuator sensível (Spring Boot)
    ("/actuator/env",       "Spring Actuator ENV"),
    ("/actuator/heapdump",  "Spring Heap Dump"),
    ("/actuator/beans",     "Spring Beans"),
    // Paths de scanner
    ("/manager/html",       "Tomcat Manager"),
    ("/jmx-console",        "JBoss JMX Console"),
    ("/invoker/",           "JBoss Invoker"),
    ("/console",            "Admin Console"),
    ("/administrator",      "CMS Admin"),
    // Arquivos PHP genéricos
    ("/config.php",         "PHP Config"),
    ("/settings.php",       "PHP Settings"),
    ("/install.php",        "PHP Installer"),
    ("/setup.php",          "PHP Setup"),
    // Outros
    ("/etc/passwd",         "Linux Passwd File"),
    ("/proc/self/environ",  "Process Environment"),
    ("/server-status",      "Apache Server Status"),
    ("/server-info",        "Apache Server Info"),
];

/// Verifica se o path é um honeypot. Retorna o label do honeypot se for.
pub fn check_honeypot(path: &str) -> Option<&'static str> {
    let path_lower = path.to_lowercase();
    for (honeypot_path, label) in HONEYPOT_PATHS {
        if path_lower.starts_with(&honeypot_path.to_lowercase()) {
            return Some(label);
        }
    }
    None
}

// ── Inspeção de payload ──────────────────────────────────────

pub fn inspect(input: &str) -> Option<RuleViolation> {
    let decoded = url_decode(input);
    let target  = decoded.as_str();

    if let Some(m) = LOG4SHELL.find(target) {
        return Some(RuleViolation { rule_id: "WAF-001", category: "Log4Shell/RCE", severity: "CRITICAL", matched: m.as_str().to_string() });
    }
    if let Some(m) = SQLI.find(target) {
        return Some(RuleViolation { rule_id: "WAF-002", category: "SQL Injection", severity: "CRITICAL", matched: m.as_str().to_string() });
    }
    if let Some(m) = RCE.find(target) {
        return Some(RuleViolation { rule_id: "WAF-003", category: "RCE/Command Injection", severity: "CRITICAL", matched: m.as_str().to_string() });
    }
    if let Some(m) = PATH_TRAVERSAL.find(target) {
        return Some(RuleViolation { rule_id: "WAF-004", category: "Path Traversal", severity: "HIGH", matched: m.as_str().to_string() });
    }
    if let Some(m) = XSS.find(target) {
        return Some(RuleViolation { rule_id: "WAF-005", category: "Cross-Site Scripting", severity: "HIGH", matched: m.as_str().to_string() });
    }
    if let Some(m) = SSRF.find(target) {
        return Some(RuleViolation { rule_id: "WAF-006", category: "SSRF", severity: "HIGH", matched: m.as_str().to_string() });
    }
    if let Some(m) = SSTI.find(target) {
        return Some(RuleViolation { rule_id: "WAF-007", category: "Template Injection", severity: "HIGH", matched: m.as_str().to_string() });
    }
    None
}

fn url_decode(s: &str) -> String {
    let once  = percent_decode(s);
    let twice = percent_decode(&once);
    twice
}

fn percent_decode(s: &str) -> String {
    let mut result = String::with_capacity(s.len());
    let bytes = s.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(hex) = std::str::from_utf8(&bytes[i+1..i+3]) {
                if let Ok(byte) = u8::from_str_radix(hex, 16) {
                    result.push(byte as char);
                    i += 3;
                    continue;
                }
            }
        }
        result.push(bytes[i] as char);
        i += 1;
    }
    result
}
