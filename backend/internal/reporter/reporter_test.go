package reporter

import (
	"strings"
	"testing"
	"time"

	"github.com/prishvindt/sector/backend/internal/store"
)

func TestTelegramReportFormattingHasCoreFields(t *testing.T) {
	manufacturer := "samsung"
	model := "SM-G991B"
	sdk := 35
	location := time.UTC
	data := store.ReportData{
		PeriodStart: time.Date(2026, 7, 3, 8, 0, 0, 0, time.UTC),
		PeriodEnd:   time.Date(2026, 7, 4, 8, 0, 0, 0, time.UTC),
		Summary: store.Summary{
			TotalDevices:           1,
			New24h:                 1,
			Active24h:              1,
			Active7d:               1,
			Events24h:              2,
			Sessions24h:            1,
			TotalSessionSeconds24h: 3600,
			Versions:               []store.CountValue{{Name: "0.1.8", Count: 1}},
			TopModels:              []store.CountValue{{Name: "samsung sm-g991b", Count: 1}},
		},
		Devices: []store.ReportDevice{{
			Device: store.Device{
				UID:          "uid:001",
				LastSeenAt:   time.Date(2026, 7, 4, 7, 42, 0, 0, time.UTC),
				AppVersion:   "0.1.8",
				Manufacturer: &manufacturer,
				Model:        &model,
				AndroidSDK:   &sdk,
				LaunchCount:  18,
			},
			SessionsInPeriod:       1,
			SessionSecondsInPeriod: 3600,
		}},
		DeviceTotal: 1,
		DeviceLimit: 20,
	}

	text := FormatDailyReport(data, location)
	for _, want := range []string{
		"sector telemetry / daily",
		"всего устройств: 1",
		"uid:001",
		"суммарное время работы за сутки: 1ч 00м",
	} {
		if !strings.Contains(text, want) {
			t.Fatalf("report does not contain %q:\n%s", want, text)
		}
	}
}
