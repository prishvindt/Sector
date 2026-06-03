package httpapi

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/prishvindt/sector/backend/internal/config"
	"github.com/prishvindt/sector/backend/internal/security"
	"github.com/prishvindt/sector/backend/internal/store"
	"github.com/prishvindt/sector/backend/internal/telemetry"
)

const maxEventBodyBytes = 8 * 1024

type Server struct {
	cfg            config.Config
	store          *store.Store
	logger         *log.Logger
	ipLimiter      *security.RateLimiter
	installLimiter *security.RateLimiter
}

func NewServer(cfg config.Config, store *store.Store, logger *log.Logger) *Server {
	return &Server{
		cfg:            cfg,
		store:          store,
		logger:         logger,
		ipLimiter:      security.NewRateLimiter(),
		installLimiter: security.NewRateLimiter(),
	}
}

func (s *Server) Router() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", s.handleHealth)
	mux.HandleFunc("/api/v1/events", s.handleEvents)
	mux.HandleFunc("/api/v1/admin/summary", s.handleAdminSummary)
	mux.HandleFunc("/api/v1/admin/devices", s.handleAdminDevices)
	mux.HandleFunc("/api/v1/admin/events", s.handleAdminEvents)
	return mux
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":      true,
		"service": "sector-telemetry",
		"version": config.ServiceVersion,
	})
}

func (s *Server) handleEvents(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !s.requireAppToken(w, r) {
		return
	}
	if !isJSONContentType(r.Header.Get("Content-Type")) {
		writeError(w, http.StatusBadRequest, "content-type must be application/json")
		return
	}
	if !s.ipLimiter.Allow("ip:"+clientIP(r), 120, time.Minute) {
		writeError(w, http.StatusTooManyRequests, "rate limit exceeded")
		return
	}

	var request telemetry.EventRequest
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxEventBodyBytes))
	if err := decoder.Decode(&request); err != nil {
		var maxBytesError *http.MaxBytesError
		if errors.As(err, &maxBytesError) {
			writeError(w, http.StatusRequestEntityTooLarge, "body too large")
			return
		}
		writeError(w, http.StatusBadRequest, "invalid json")
		return
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		writeError(w, http.StatusBadRequest, "json body must contain one object")
		return
	}

	event, err := telemetry.ValidateEvent(request)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if !s.installLimiter.Allow("install:"+event.InstallID, 60, time.Minute) {
		writeError(w, http.StatusTooManyRequests, "rate limit exceeded")
		return
	}

	if err := s.store.RecordEvent(r.Context(), store.EventInput{
		InstallID:              event.InstallID,
		EventType:              event.EventType,
		AppVersion:             event.AppVersion,
		VersionCode:            event.VersionCode,
		Manufacturer:           event.Manufacturer,
		Model:                  event.Model,
		AndroidSDK:             event.AndroidSDK,
		SessionID:              event.SessionID,
		SessionDurationSeconds: event.SessionDurationSeconds,
	}); err != nil {
		if s.logger != nil {
			s.logger.Printf("record event failed: %v", err)
		}
		writeError(w, http.StatusInternalServerError, "server error")
		return
	}

	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleAdminSummary(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !s.requireAdminToken(w, r) {
		return
	}
	summary, err := s.store.Summary(r.Context())
	if err != nil {
		if s.logger != nil {
			s.logger.Printf("admin summary failed: %v", err)
		}
		writeError(w, http.StatusInternalServerError, "server error")
		return
	}
	writeJSON(w, http.StatusOK, summary)
}

func (s *Server) handleAdminDevices(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !s.requireAdminToken(w, r) {
		return
	}
	devices, err := s.store.ListDevices(r.Context(), time.Now().UTC(), 500)
	if err != nil {
		if s.logger != nil {
			s.logger.Printf("admin devices failed: %v", err)
		}
		writeError(w, http.StatusInternalServerError, "server error")
		return
	}
	writeJSON(w, http.StatusOK, devices)
}

func (s *Server) handleAdminEvents(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !s.requireAdminToken(w, r) {
		return
	}
	filter, err := parseEventFilter(r)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	events, err := s.store.ListEvents(r.Context(), filter)
	if err != nil {
		if s.logger != nil {
			s.logger.Printf("admin events failed: %v", err)
		}
		writeError(w, http.StatusInternalServerError, "server error")
		return
	}
	writeJSON(w, http.StatusOK, events)
}

func (s *Server) requireAppToken(w http.ResponseWriter, r *http.Request) bool {
	if s.cfg.AppToken == "" {
		if s.cfg.AllowEmptyAppToken {
			return true
		}
		writeError(w, http.StatusUnauthorized, "app token is not configured")
		return false
	}
	if !secureCompare(r.Header.Get("X-App-Token"), s.cfg.AppToken) {
		writeError(w, http.StatusUnauthorized, "bad or missing app token")
		return false
	}
	return true
}

func (s *Server) requireAdminToken(w http.ResponseWriter, r *http.Request) bool {
	if s.cfg.AdminToken == "" {
		writeError(w, http.StatusServiceUnavailable, "admin api is disabled")
		return false
	}
	if !secureCompare(r.Header.Get("X-Admin-Token"), s.cfg.AdminToken) {
		writeError(w, http.StatusUnauthorized, "bad or missing admin token")
		return false
	}
	return true
}

func secureCompare(got string, want string) bool {
	if got == "" || want == "" {
		return false
	}
	if len(got) != len(want) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}

func parseEventFilter(r *http.Request) (store.EventFilter, error) {
	query := r.URL.Query()
	filter := store.EventFilter{Limit: 100}

	if rawLimit := strings.TrimSpace(query.Get("limit")); rawLimit != "" {
		limit, err := strconv.Atoi(rawLimit)
		if err != nil || limit <= 0 {
			return store.EventFilter{}, errors.New("limit must be positive")
		}
		filter.Limit = limit
	}

	if rawUID := strings.TrimSpace(query.Get("uid")); rawUID != "" {
		rawUID = strings.TrimPrefix(rawUID, "uid:")
		uid, err := strconv.Atoi(rawUID)
		if err != nil || uid <= 0 {
			return store.EventFilter{}, errors.New("uid must be like uid:001 or 1")
		}
		filter.UID = uid
	}

	if rawSince := strings.TrimSpace(query.Get("since")); rawSince != "" {
		since, err := time.Parse(time.RFC3339, rawSince)
		if err != nil {
			return store.EventFilter{}, errors.New("since must be RFC3339")
		}
		filter.Since = &since
	}

	return filter, nil
}

func isJSONContentType(value string) bool {
	value = strings.ToLower(strings.TrimSpace(value))
	return value == "application/json" || strings.HasPrefix(value, "application/json;")
}

func clientIP(r *http.Request) string {
	if forwarded := strings.TrimSpace(r.Header.Get("X-Forwarded-For")); forwarded != "" {
		first := strings.Split(forwarded, ",")[0]
		if parsed := strings.TrimSpace(first); parsed != "" {
			return parsed
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil && host != "" {
		return host
	}
	if r.RemoteAddr != "" {
		return r.RemoteAddr
	}
	return "unknown"
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]any{
		"ok":    false,
		"error": message,
	})
}
