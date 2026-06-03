package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

const ServiceVersion = "0.1.0"

type Config struct {
	AppEnv                     string
	HTTPAddr                   string
	DatabasePath               string
	MigrationsDir              string
	AppToken                   string
	AllowEmptyAppToken         bool
	AdminToken                 string
	EventRetentionDays         int
	TelegramReportEnabled      bool
	TelegramBotToken           string
	TelegramChatID             string
	TelegramReportTime         string
	TelegramReportTimezone     string
	TelegramMaxDevicesInReport int
	PublicBaseURL              string
}

func Load() (Config, error) {
	return LoadFromLookup(os.LookupEnv)
}

func LoadFromLookup(lookup func(string) (string, bool)) (Config, error) {
	cfg := Config{
		AppEnv:                     getString(lookup, "APP_ENV", "production"),
		HTTPAddr:                   getString(lookup, "HTTP_ADDR", ":8080"),
		DatabasePath:               getString(lookup, "DATABASE_PATH", "/data/telemetry.db"),
		MigrationsDir:              getString(lookup, "MIGRATIONS_DIR", "migrations"),
		AppToken:                   strings.TrimSpace(getString(lookup, "APP_TOKEN", "")),
		AllowEmptyAppToken:         getBool(lookup, "ALLOW_EMPTY_APP_TOKEN", false),
		AdminToken:                 strings.TrimSpace(getString(lookup, "ADMIN_TOKEN", "")),
		EventRetentionDays:         getInt(lookup, "EVENT_RETENTION_DAYS", 180, 1, 3650),
		TelegramReportEnabled:      getBool(lookup, "TELEGRAM_REPORT_ENABLED", false),
		TelegramBotToken:           strings.TrimSpace(getString(lookup, "TELEGRAM_BOT_TOKEN", "")),
		TelegramChatID:             strings.TrimSpace(getString(lookup, "TELEGRAM_CHAT_ID", "")),
		TelegramReportTime:         getString(lookup, "TELEGRAM_REPORT_TIME", "08:00"),
		TelegramReportTimezone:     getString(lookup, "TELEGRAM_REPORT_TIMEZONE", "Europe/Tallinn"),
		TelegramMaxDevicesInReport: getInt(lookup, "TELEGRAM_MAX_DEVICES_IN_REPORT", 20, 1, 200),
		PublicBaseURL:              getString(lookup, "PUBLIC_BASE_URL", "https://telemetry.sector-map.ru"),
	}

	if _, err := time.Parse("15:04", cfg.TelegramReportTime); err != nil {
		return Config{}, fmt.Errorf("invalid TELEGRAM_REPORT_TIME: %w", err)
	}
	if _, err := time.LoadLocation(cfg.TelegramReportTimezone); err != nil {
		return Config{}, fmt.Errorf("invalid TELEGRAM_REPORT_TIMEZONE: %w", err)
	}

	return cfg, nil
}

func getString(lookup func(string) (string, bool), key string, fallback string) string {
	if value, ok := lookup(key); ok {
		return strings.TrimSpace(value)
	}
	return fallback
}

func getBool(lookup func(string) (string, bool), key string, fallback bool) bool {
	value, ok := lookup(key)
	if !ok || strings.TrimSpace(value) == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(strings.TrimSpace(value))
	if err != nil {
		return fallback
	}
	return parsed
}

func getInt(lookup func(string) (string, bool), key string, fallback int, min int, max int) int {
	value, ok := lookup(key)
	if !ok || strings.TrimSpace(value) == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || parsed < min || parsed > max {
		return fallback
	}
	return parsed
}
