package store

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	_ "github.com/mattn/go-sqlite3"

	"github.com/prishvindt/sector/backend/internal/telemetry"
)

type Store struct {
	db *sql.DB
}

func Open(ctx context.Context, databasePath string, migrationsDir string) (*Store, error) {
	if databasePath != ":memory:" {
		dir := filepath.Dir(databasePath)
		if dir != "." && dir != "" {
			if err := os.MkdirAll(dir, 0o700); err != nil {
				return nil, fmt.Errorf("create database directory: %w", err)
			}
		}
	}

	db, err := sql.Open("sqlite3", databasePath)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("ping sqlite: %w", err)
	}

	for _, pragma := range []string{
		"PRAGMA foreign_keys = ON",
		"PRAGMA journal_mode = WAL",
		"PRAGMA busy_timeout = 5000",
	} {
		if _, err := db.ExecContext(ctx, pragma); err != nil {
			_ = db.Close()
			return nil, fmt.Errorf("apply %s: %w", pragma, err)
		}
	}

	if err := applyMigrations(ctx, db, migrationsDir); err != nil {
		_ = db.Close()
		return nil, err
	}

	return &Store{db: db}, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func applyMigrations(ctx context.Context, db *sql.DB, migrationsDir string) error {
	if migrationsDir == "" {
		return fmt.Errorf("migrations directory is empty")
	}
	if _, err := db.ExecContext(ctx, `
CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    applied_at DATETIME NOT NULL
)`); err != nil {
		return fmt.Errorf("create schema_migrations: %w", err)
	}

	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return fmt.Errorf("read migrations dir %q: %w", migrationsDir, err)
	}
	sort.Slice(entries, func(i, j int) bool {
		return entries[i].Name() < entries[j].Name()
	})

	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".sql") {
			continue
		}
		version := strings.TrimSuffix(entry.Name(), ".sql")
		var exists int
		if err := db.QueryRowContext(ctx, "SELECT COUNT(*) FROM schema_migrations WHERE version = ?", version).Scan(&exists); err != nil {
			return fmt.Errorf("check migration %s: %w", version, err)
		}
		if exists > 0 {
			continue
		}

		sqlText, err := os.ReadFile(filepath.Join(migrationsDir, entry.Name()))
		if err != nil {
			return fmt.Errorf("read migration %s: %w", entry.Name(), err)
		}

		tx, err := db.BeginTx(ctx, nil)
		if err != nil {
			return fmt.Errorf("begin migration %s: %w", version, err)
		}
		if _, err := tx.ExecContext(ctx, string(sqlText)); err != nil {
			_ = tx.Rollback()
			return fmt.Errorf("execute migration %s: %w", version, err)
		}
		if _, err := tx.ExecContext(ctx, "INSERT INTO schema_migrations(version, applied_at) VALUES (?, ?)", version, time.Now().UTC()); err != nil {
			_ = tx.Rollback()
			return fmt.Errorf("record migration %s: %w", version, err)
		}
		if err := tx.Commit(); err != nil {
			return fmt.Errorf("commit migration %s: %w", version, err)
		}
	}

	return nil
}

func (s *Store) RecordEvent(ctx context.Context, event EventInput) error {
	if event.CreatedAt.IsZero() {
		event.CreatedAt = time.Now().UTC()
	}
	event.CreatedAt = event.CreatedAt.UTC()

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin record event: %w", err)
	}
	defer tx.Rollback()

	deviceID, err := findDeviceID(ctx, tx, event.InstallID)
	if err == sql.ErrNoRows {
		deviceID, err = insertDevice(ctx, tx, event)
	}
	if err != nil {
		return err
	}

	launchIncrement := 0
	heartbeatIncrement := 0
	sessionSecondsIncrement := 0
	if event.EventType == telemetry.EventTypeAppStart {
		launchIncrement = 1
	}
	if event.EventType == telemetry.EventTypeHeartbeat {
		heartbeatIncrement = 1
	}
	if event.EventType == telemetry.EventTypeAppBackground && event.SessionDurationSeconds != nil && *event.SessionDurationSeconds > 0 {
		sessionSecondsIncrement = *event.SessionDurationSeconds
	}

	if _, err := tx.ExecContext(ctx, `
UPDATE devices
SET last_seen_at = ?,
    app_version = ?,
    version_code = ?,
    manufacturer = COALESCE(?, manufacturer),
    model = COALESCE(?, model),
    android_sdk = COALESCE(?, android_sdk),
    launch_count = launch_count + ?,
    heartbeat_count = heartbeat_count + ?,
    total_session_seconds = total_session_seconds + ?
WHERE id = ?`,
		event.CreatedAt,
		event.AppVersion,
		event.VersionCode,
		nullString(event.Manufacturer),
		nullString(event.Model),
		nullInt(event.AndroidSDK),
		launchIncrement,
		heartbeatIncrement,
		sessionSecondsIncrement,
		deviceID,
	); err != nil {
		return fmt.Errorf("update device: %w", err)
	}

	if _, err := tx.ExecContext(ctx, `
INSERT INTO events(device_id, event_type, app_version, version_code, session_id, session_duration_seconds, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?)`,
		deviceID,
		event.EventType,
		event.AppVersion,
		event.VersionCode,
		nullString(event.SessionID),
		nullInt(event.SessionDurationSeconds),
		event.CreatedAt,
	); err != nil {
		return fmt.Errorf("insert event: %w", err)
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit record event: %w", err)
	}
	return nil
}

func findDeviceID(ctx context.Context, tx *sql.Tx, installID string) (int64, error) {
	var id int64
	err := tx.QueryRowContext(ctx, "SELECT id FROM devices WHERE install_id = ?", installID).Scan(&id)
	return id, err
}

func insertDevice(ctx context.Context, tx *sql.Tx, event EventInput) (int64, error) {
	result, err := tx.ExecContext(ctx, `
INSERT INTO devices(
    uid_number,
    install_id,
    first_seen_at,
    last_seen_at,
    app_version,
    version_code,
    manufacturer,
    model,
    android_sdk
)
VALUES (
    (SELECT COALESCE(MAX(uid_number), 0) + 1 FROM devices),
    ?, ?, ?, ?, ?, ?, ?, ?
)`,
		event.InstallID,
		event.CreatedAt,
		event.CreatedAt,
		event.AppVersion,
		event.VersionCode,
		nullString(event.Manufacturer),
		nullString(event.Model),
		nullInt(event.AndroidSDK),
	)
	if err != nil {
		return 0, fmt.Errorf("insert device: %w", err)
	}
	deviceID, err := result.LastInsertId()
	if err != nil {
		return 0, fmt.Errorf("get device id: %w", err)
	}
	return deviceID, nil
}

func (s *Store) PruneEvents(ctx context.Context, olderThanDays int) error {
	if olderThanDays <= 0 {
		return nil
	}
	cutoff := time.Now().UTC().AddDate(0, 0, -olderThanDays)
	if _, err := s.db.ExecContext(ctx, "DELETE FROM events WHERE created_at < ?", cutoff); err != nil {
		return fmt.Errorf("prune events: %w", err)
	}
	return nil
}

func (s *Store) StartRetention(ctx context.Context, olderThanDays int, interval time.Duration, logger *log.Logger) {
	if olderThanDays <= 0 {
		return
	}
	go func() {
		if err := s.PruneEvents(ctx, olderThanDays); err != nil && logger != nil {
			logger.Printf("retention prune failed: %v", err)
		}

		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				if err := s.PruneEvents(ctx, olderThanDays); err != nil && logger != nil {
					logger.Printf("retention prune failed: %v", err)
				}
			}
		}
	}()
}

func nullString(value string) any {
	if strings.TrimSpace(value) == "" {
		return nil
	}
	return strings.TrimSpace(value)
}

func nullInt(value *int) any {
	if value == nil {
		return nil
	}
	return *value
}
