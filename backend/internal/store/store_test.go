package store

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/prishvindt/sector/backend/internal/telemetry"
)

func TestAppStartCreatesDeviceAndIncrementsLaunchCount(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()
	now := time.Date(2026, 7, 4, 8, 0, 0, 0, time.UTC)

	if err := st.RecordEvent(ctx, testEvent(telemetry.EventTypeAppStart, now)); err != nil {
		t.Fatalf("RecordEvent: %v", err)
	}

	devices, err := st.ListDevices(ctx, now, 10)
	if err != nil {
		t.Fatalf("ListDevices: %v", err)
	}
	if len(devices) != 1 {
		t.Fatalf("devices len = %d, want 1", len(devices))
	}
	if devices[0].UID != "uid:001" {
		t.Fatalf("UID = %q, want uid:001", devices[0].UID)
	}
	if devices[0].LaunchCount != 1 {
		t.Fatalf("LaunchCount = %d, want 1", devices[0].LaunchCount)
	}
}

func TestHeartbeatUpdatesLastSeenAndHeartbeatCount(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()
	start := time.Date(2026, 7, 4, 8, 0, 0, 0, time.UTC)
	heartbeat := start.Add(15 * time.Minute)

	if err := st.RecordEvent(ctx, testEvent(telemetry.EventTypeAppStart, start)); err != nil {
		t.Fatalf("RecordEvent app_start: %v", err)
	}
	event := testEvent(telemetry.EventTypeHeartbeat, heartbeat)
	event.SessionID = "11111111-1111-4111-8111-111111111111"
	if err := st.RecordEvent(ctx, event); err != nil {
		t.Fatalf("RecordEvent heartbeat: %v", err)
	}

	devices, err := st.ListDevices(ctx, heartbeat, 10)
	if err != nil {
		t.Fatalf("ListDevices: %v", err)
	}
	if devices[0].HeartbeatCount != 1 {
		t.Fatalf("HeartbeatCount = %d, want 1", devices[0].HeartbeatCount)
	}
	if !devices[0].LastSeenAt.Equal(heartbeat) {
		t.Fatalf("LastSeenAt = %v, want %v", devices[0].LastSeenAt, heartbeat)
	}
	if devices[0].LaunchCount != 1 {
		t.Fatalf("LaunchCount = %d, want 1", devices[0].LaunchCount)
	}
}

func TestAppBackgroundAddsSessionDuration(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()
	now := time.Date(2026, 7, 4, 8, 0, 0, 0, time.UTC)
	duration := 5400

	if err := st.RecordEvent(ctx, testEvent(telemetry.EventTypeAppStart, now)); err != nil {
		t.Fatalf("RecordEvent app_start: %v", err)
	}
	event := testEvent(telemetry.EventTypeAppBackground, now.Add(90*time.Minute))
	event.SessionID = "22222222-2222-4222-8222-222222222222"
	event.SessionDurationSeconds = &duration
	if err := st.RecordEvent(ctx, event); err != nil {
		t.Fatalf("RecordEvent app_background: %v", err)
	}

	devices, err := st.ListDevices(ctx, now.Add(90*time.Minute), 10)
	if err != nil {
		t.Fatalf("ListDevices: %v", err)
	}
	if devices[0].TotalSessionSeconds != duration {
		t.Fatalf("TotalSessionSeconds = %d, want %d", devices[0].TotalSessionSeconds, duration)
	}
}

func TestAdminSummaryCalculatesActiveWindows(t *testing.T) {
	st := newTestStore(t)
	ctx := context.Background()
	now := time.Date(2026, 7, 4, 8, 0, 0, 0, time.UTC)

	recent := testEvent(telemetry.EventTypeAppStart, now.Add(-2*time.Hour))
	recent.InstallID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
	if err := st.RecordEvent(ctx, recent); err != nil {
		t.Fatalf("RecordEvent recent: %v", err)
	}
	old := testEvent(telemetry.EventTypeAppStart, now.Add(-48*time.Hour))
	old.InstallID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
	if err := st.RecordEvent(ctx, old); err != nil {
		t.Fatalf("RecordEvent old: %v", err)
	}

	summary, err := st.SummaryAt(ctx, now)
	if err != nil {
		t.Fatalf("SummaryAt: %v", err)
	}
	if summary.TotalDevices != 2 {
		t.Fatalf("TotalDevices = %d, want 2", summary.TotalDevices)
	}
	if summary.Active24h != 1 {
		t.Fatalf("Active24h = %d, want 1", summary.Active24h)
	}
	if summary.Active7d != 2 {
		t.Fatalf("Active7d = %d, want 2", summary.Active7d)
	}
}

func newTestStore(t *testing.T) *Store {
	t.Helper()
	st, err := Open(context.Background(), filepath.Join(t.TempDir(), "telemetry.db"), findMigrationsDir(t))
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	t.Cleanup(func() {
		_ = st.Close()
	})
	return st
}

func testEvent(eventType string, createdAt time.Time) EventInput {
	sdk := 35
	return EventInput{
		InstallID:    "550e8400-e29b-41d4-a716-446655440000",
		EventType:    eventType,
		AppVersion:   "0.1.8",
		VersionCode:  9,
		Manufacturer: "samsung",
		Model:        "SM-G991B",
		AndroidSDK:   &sdk,
		CreatedAt:    createdAt,
	}
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
