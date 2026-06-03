package httpapi

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/prishvindt/sector/backend/internal/config"
	"github.com/prishvindt/sector/backend/internal/store"
)

func TestEventsRejectMissingAndWrongAppToken(t *testing.T) {
	server := newTestHTTPServer(t)

	body := `{"installId":"550e8400-e29b-41d4-a716-446655440000","eventType":"app_start","appVersion":"0.1.8","versionCode":9}`
	req := httptest.NewRequest(http.MethodPost, "/api/v1/events", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	resp := httptest.NewRecorder()
	server.Router().ServeHTTP(resp, req)
	if resp.Code != http.StatusUnauthorized {
		t.Fatalf("missing token status = %d, want 401", resp.Code)
	}

	req = httptest.NewRequest(http.MethodPost, "/api/v1/events", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-App-Token", "wrong")
	resp = httptest.NewRecorder()
	server.Router().ServeHTTP(resp, req)
	if resp.Code != http.StatusUnauthorized {
		t.Fatalf("wrong token status = %d, want 401", resp.Code)
	}
}

func TestAdminEndpointsRejectMissingAndWrongToken(t *testing.T) {
	server := newTestHTTPServer(t)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/admin/summary", nil)
	resp := httptest.NewRecorder()
	server.Router().ServeHTTP(resp, req)
	if resp.Code != http.StatusUnauthorized {
		t.Fatalf("missing admin token status = %d, want 401", resp.Code)
	}

	req = httptest.NewRequest(http.MethodGet, "/api/v1/admin/summary", nil)
	req.Header.Set("X-Admin-Token", "wrong")
	resp = httptest.NewRecorder()
	server.Router().ServeHTTP(resp, req)
	if resp.Code != http.StatusUnauthorized {
		t.Fatalf("wrong admin token status = %d, want 401", resp.Code)
	}
}

func newTestHTTPServer(t *testing.T) *Server {
	t.Helper()
	st, err := store.Open(context.Background(), filepath.Join(t.TempDir(), "telemetry.db"), findMigrationsDir(t))
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	t.Cleanup(func() {
		_ = st.Close()
	})
	cfg := config.Config{
		AppToken:   "app-token",
		AdminToken: "admin-token",
	}
	return NewServer(cfg, st, nil)
}

func findMigrationsDir(t *testing.T) string {
	t.Helper()
	dir, err := os.Getwd()
	if err != nil {
		t.Fatalf("Getwd: %v", err)
	}
	for {
		candidate := filepath.Join(dir, "migrations")
		if _, err := os.Stat(filepath.Join(candidate, "001_init.sql")); err == nil {
			return candidate
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			t.Fatal("could not find backend/migrations")
		}
		dir = parent
	}
}
