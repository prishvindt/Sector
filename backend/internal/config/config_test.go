package config

import "testing"

func TestLoadDefaults(t *testing.T) {
	cfg, err := LoadFromLookup(func(string) (string, bool) {
		return "", false
	})
	if err != nil {
		t.Fatalf("LoadFromLookup returned error: %v", err)
	}

	if cfg.HTTPAddr != ":8080" {
		t.Fatalf("HTTPAddr = %q, want :8080", cfg.HTTPAddr)
	}
	if cfg.DatabasePath != "/data/telemetry.db" {
		t.Fatalf("DatabasePath = %q, want /data/telemetry.db", cfg.DatabasePath)
	}
	if cfg.EventRetentionDays != 180 {
		t.Fatalf("EventRetentionDays = %d, want 180", cfg.EventRetentionDays)
	}
	if cfg.TelegramReportTimezone != "Europe/Tallinn" {
		t.Fatalf("TelegramReportTimezone = %q, want Europe/Tallinn", cfg.TelegramReportTimezone)
	}
}
