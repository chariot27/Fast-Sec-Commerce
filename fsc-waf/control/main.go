package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sort"
	"sync"
	"time"
)

// ── Structs ────────────────────────────────────────────────────

type WafEvent struct {
	TraceID            string  `json:"trace_id"`
	ClientIP           string  `json:"client_ip"`
	Method             string  `json:"method"`
	Path               string  `json:"path"`
	Verdict            string  `json:"verdict"`
	RuleID             *string `json:"rule_id"`
	Category           *string `json:"category"`
	Severity           *string `json:"severity"`
	Matched            *string `json:"matched"`
	Reason             string  `json:"reason"`
	Timestamp          string  `json:"timestamp"`
	UserAgent          string  `json:"user_agent"`
	HoneypotLabel      *string `json:"honeypot_label"`
	NeedsInvestigation bool    `json:"needs_investigation"`
}

// Perfil completo de um atacante
type AttackerProfile struct {
	IP             string     `json:"ip"`
	FirstSeen      time.Time  `json:"first_seen"`
	LastSeen       time.Time  `json:"last_seen"`
	HoneypotHits   int        `json:"honeypot_hits"`
	BlockedHits    int        `json:"blocked_hits"`
	TotalRequests  int        `json:"total_requests"`
	UserAgents     []string   `json:"user_agents"`
	PathsAccessed  []string   `json:"paths_accessed"`
	HoneypotLabels []string   `json:"honeypot_labels"`
	Investigated   bool       `json:"investigated"`
	Notes          string     `json:"notes"`
}

type Stats struct {
	TotalRequests  int `json:"total_requests"`
	TotalBlocked   int `json:"total_blocked"`
	TotalAllowed   int `json:"total_allowed"`
	TotalHoneypots int `json:"total_honeypots"`
	PendingReview  int `json:"pending_review"`
	SQLiBlocked    int `json:"sqli_blocked"`
	XSSBlocked     int `json:"xss_blocked"`
	RCEBlocked     int `json:"rce_blocked"`
	SSRFBlocked    int `json:"ssrf_blocked"`
	RateLimited    int `json:"rate_limited"`
	OtherBlocked   int `json:"other_blocked"`
	UptimeSecs     int `json:"uptime_seconds"`
}

// ── Estado Global ──────────────────────────────────────────────

var (
	mu              sync.RWMutex
	events          []WafEvent
	attackers       = map[string]*AttackerProfile{}
	investigationQ  []WafEvent // eventos que precisam de revisão admin
	stats           Stats
	startTime       = time.Now()
)

const maxEvents = 1000

// ── POST /events ───────────────────────────────────────────────

func handleEvent(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", 405); return
	}
	var ev WafEvent
	if err := json.NewDecoder(r.Body).Decode(&ev); err != nil {
		http.Error(w, "Bad Request", 400); return
	}

	mu.Lock()
	defer mu.Unlock()

	// Ring buffer eventos
	if len(events) >= maxEvents { events = events[1:] }
	events = append(events, ev)

	// Estatísticas
	stats.TotalRequests++
	switch ev.Verdict {
	case "ALLOW":
		stats.TotalAllowed++
	case "HONEYPOT":
		stats.TotalBlocked++
		stats.TotalHoneypots++
	case "DENY":
		stats.TotalBlocked++
		cat := ""
		if ev.Category != nil { cat = *ev.Category }
		switch cat {
		case "SQL Injection":            stats.SQLiBlocked++
		case "Cross-Site Scripting":     stats.XSSBlocked++
		case "RCE/Command Injection":    stats.RCEBlocked++
		case "SSRF":                     stats.SSRFBlocked++
		case "Rate Limit":               stats.RateLimited++
		default:                         stats.OtherBlocked++
		}
	}

	// Fila de investigação
	if ev.NeedsInvestigation || ev.Verdict == "HONEYPOT" {
		investigationQ = append(investigationQ, ev)
		stats.PendingReview = len(investigationQ)
	}

	// Perfil do atacante
	if ev.Verdict == "DENY" || ev.Verdict == "HONEYPOT" {
		p, ok := attackers[ev.ClientIP]
		if !ok {
			p = &AttackerProfile{IP: ev.ClientIP, FirstSeen: time.Now()}
			attackers[ev.ClientIP] = p
		}
		p.LastSeen = time.Now()
		p.TotalRequests++
		if ev.Verdict == "HONEYPOT" { p.HoneypotHits++ }
		if ev.Verdict == "DENY"     { p.BlockedHits++ }

		// User-agents únicos
		found := false
		for _, ua := range p.UserAgents {
			if ua == ev.UserAgent { found = true; break }
		}
		if !found && ev.UserAgent != "" { p.UserAgents = append(p.UserAgents, ev.UserAgent) }

		// Paths únicos
		found = false
		for _, pt := range p.PathsAccessed {
			if pt == ev.Path { found = true; break }
		}
		if !found { p.PathsAccessed = append(p.PathsAccessed, ev.Path) }

		// Honeypot labels
		if ev.HoneypotLabel != nil {
			found = false
			for _, hl := range p.HoneypotLabels {
				if hl == *ev.HoneypotLabel { found = true; break }
			}
			if !found { p.HoneypotLabels = append(p.HoneypotLabels, *ev.HoneypotLabel) }
		}
	}

	w.WriteHeader(http.StatusCreated)
}

// ── GET /api/stats ─────────────────────────────────────────────

func handleStats(w http.ResponseWriter, r *http.Request) {
	mu.RLock()
	s := stats
	s.UptimeSecs = int(time.Since(startTime).Seconds())
	mu.RUnlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(s)
}

// ── GET /api/events ────────────────────────────────────────────

func handleEvents(w http.ResponseWriter, r *http.Request) {
	mu.RLock()
	out := make([]WafEvent, len(events))
	copy(out, events)
	mu.RUnlock()
	// inverter
	for i, j := 0, len(out)-1; i < j; i, j = i+1, j-1 { out[i], out[j] = out[j], out[i] }
	if len(out) > 50 { out = out[:50] }
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(out)
}

// ── GET /api/investigation ────────────────────────────────────

func handleInvestigation(w http.ResponseWriter, r *http.Request) {
	mu.RLock()
	out := make([]WafEvent, len(investigationQ))
	copy(out, investigationQ)
	mu.RUnlock()
	// mais recente primeiro
	for i, j := 0, len(out)-1; i < j; i, j = i+1, j-1 { out[i], out[j] = out[j], out[i] }
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(out)
}

// ── POST /api/investigation/:traceId/resolve ──────────────────

func handleResolve(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost { http.Error(w, "Method Not Allowed", 405); return }
	traceID := r.URL.Query().Get("trace_id")
	mu.Lock()
	newQ := investigationQ[:0]
	for _, ev := range investigationQ {
		if ev.TraceID != traceID { newQ = append(newQ, ev) }
	}
	investigationQ = newQ
	stats.PendingReview = len(investigationQ)
	mu.Unlock()
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "resolved", "trace_id": traceID})
}

// ── GET /api/attackers ────────────────────────────────────────

func handleAttackers(w http.ResponseWriter, r *http.Request) {
	mu.RLock()
	list := make([]*AttackerProfile, 0, len(attackers))
	for _, p := range attackers { list = append(list, p) }
	mu.RUnlock()
	// ordenar por honeypot_hits desc
	sort.Slice(list, func(i, j int) bool {
		return list[i].HoneypotHits+list[i].BlockedHits > list[j].HoneypotHits+list[j].BlockedHits
	})
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(list)
}

// ── GET /metrics (Prometheus) ─────────────────────────────────

func handleMetrics(w http.ResponseWriter, r *http.Request) {
	mu.RLock()
	s := stats
	mu.RUnlock()
	w.Header().Set("Content-Type", "text/plain; version=0.0.4")
	fmt.Fprintf(w, "fsc_waf_requests_total %d\n", s.TotalRequests)
	fmt.Fprintf(w, "fsc_waf_blocked_total %d\n", s.TotalBlocked)
	fmt.Fprintf(w, "fsc_waf_allowed_total %d\n", s.TotalAllowed)
	fmt.Fprintf(w, "fsc_waf_honeypot_total %d\n", s.TotalHoneypots)
	fmt.Fprintf(w, "fsc_waf_pending_review %d\n", s.PendingReview)
	fmt.Fprintf(w, "fsc_waf_sqli_total %d\n", s.SQLiBlocked)
	fmt.Fprintf(w, "fsc_waf_xss_total %d\n", s.XSSBlocked)
	fmt.Fprintf(w, "fsc_waf_rce_total %d\n", s.RCEBlocked)
	fmt.Fprintf(w, "fsc_waf_ssrf_total %d\n", s.SSRFBlocked)
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	fmt.Fprintf(w, `{"status":"UP","service":"fsc-waf-control"}`)
}

// ── Main ──────────────────────────────────────────────────────

func main() {
	events = make([]WafEvent, 0, maxEvents)
	investigationQ = make([]WafEvent, 0, 200)

	mux := http.NewServeMux()
	mux.HandleFunc("/events",                 handleEvent)
	mux.HandleFunc("/api/stats",              handleStats)
	mux.HandleFunc("/api/events",             handleEvents)
	mux.HandleFunc("/api/investigation",      handleInvestigation)
	mux.HandleFunc("/api/investigation/resolve", handleResolve)
	mux.HandleFunc("/api/attackers",          handleAttackers)
	mux.HandleFunc("/metrics",                handleMetrics)
	mux.HandleFunc("/health",                 handleHealth)
	mux.HandleFunc("/",                       handleDashboard)

	log.Printf("FSC WAF Control Plane — http://localhost:9200")
	log.Fatal(http.ListenAndServe(":9200", mux))
}

func handleDashboard(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/" { http.NotFound(w, r); return }
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, dashboardHTML)
}
