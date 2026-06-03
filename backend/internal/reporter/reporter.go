package reporter

import (
	"context"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/prishvindt/sector/backend/internal/config"
	"github.com/prishvindt/sector/backend/internal/store"
)

const telegramMessageLimit = 3900

type TelegramClient struct {
	BotToken string
	ChatID   string
	Client   *http.Client
}

func (c TelegramClient) SendMessages(ctx context.Context, messages []string) error {
	if c.Client == nil {
		c.Client = &http.Client{Timeout: 10 * time.Second}
	}
	for _, message := range messages {
		form := url.Values{}
		form.Set("chat_id", c.ChatID)
		form.Set("text", message)
		form.Set("disable_web_page_preview", "true")
		endpoint := "https://api.telegram.org/bot" + c.BotToken + "/sendMessage"
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, strings.NewReader(form.Encode()))
		if err != nil {
			return err
		}
		req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
		resp, err := c.Client.Do(req)
		if err != nil {
			return err
		}
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
		_ = resp.Body.Close()
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			return fmt.Errorf("telegram status %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
		}
	}
	return nil
}

func StartDailyScheduler(ctx context.Context, cfg config.Config, st *store.Store, logger *log.Logger) {
	if !cfg.TelegramReportEnabled {
		return
	}
	if cfg.TelegramBotToken == "" || cfg.TelegramChatID == "" {
		if logger != nil {
			logger.Printf("telegram report enabled but token/chat id is missing; scheduler disabled")
		}
		return
	}

	location, err := time.LoadLocation(cfg.TelegramReportTimezone)
	if err != nil {
		if logger != nil {
			logger.Printf("telegram report timezone is invalid: %v", err)
		}
		return
	}
	reportTime, err := time.Parse("15:04", cfg.TelegramReportTime)
	if err != nil {
		if logger != nil {
			logger.Printf("telegram report time is invalid: %v", err)
		}
		return
	}

	client := TelegramClient{
		BotToken: cfg.TelegramBotToken,
		ChatID:   cfg.TelegramChatID,
	}

	go func() {
		for {
			now := time.Now().In(location)
			next := nextRun(now, reportTime, location)
			timer := time.NewTimer(time.Until(next))
			select {
			case <-ctx.Done():
				timer.Stop()
				return
			case <-timer.C:
				end := next
				start := end.Add(-24 * time.Hour)
				data, err := st.DailyReportData(ctx, start.UTC(), end.UTC(), cfg.TelegramMaxDevicesInReport)
				if err != nil {
					if logger != nil {
						logger.Printf("build telegram report failed: %v", err)
					}
					continue
				}
				text := FormatDailyReport(data, location)
				if err := client.SendMessages(ctx, SplitTelegramMessages(text)); err != nil && logger != nil {
					logger.Printf("send telegram report failed: %v", err)
				}
			}
		}
	}()
}

func FormatDailyReport(data store.ReportData, location *time.Location) string {
	if location == nil {
		location = time.UTC
	}

	var out strings.Builder
	writeLine(&out, "sector telemetry / daily")
	writeLine(&out, "период: %s -> %s", data.PeriodStart.In(location).Format("2006-01-02 15:04"), data.PeriodEnd.In(location).Format("2006-01-02 15:04"))
	writeLine(&out, "")
	writeLine(&out, "сводка:")
	writeLine(&out, "всего устройств: %d", data.Summary.TotalDevices)
	writeLine(&out, "новые за сутки: %d", data.Summary.New24h)
	writeLine(&out, "активны за 24ч: %d", data.Summary.Active24h)
	writeLine(&out, "активны за 7д: %d", data.Summary.Active7d)
	writeLine(&out, "неактивны 7+ дней: %d", data.Summary.Inactive7d)
	writeLine(&out, "событий за сутки: %d", data.Summary.Events24h)
	writeLine(&out, "сессий за сутки: %d", data.Summary.Sessions24h)
	writeLine(&out, "суммарное время работы за сутки: %s", formatDuration(data.Summary.TotalSessionSeconds24h))
	writeLine(&out, "")

	if len(data.Summary.Versions) > 0 {
		writeLine(&out, "версии:")
		for _, version := range data.Summary.Versions {
			writeLine(&out, "%s - %d", version.Name, version.Count)
		}
		writeLine(&out, "")
	}

	if len(data.Devices) > 0 {
		writeLine(&out, "устройства:")
		for _, device := range data.Devices {
			writeLine(&out, "%s · %s · %s · %s", device.UID, formatModel(device.Manufacturer, device.Model), formatAndroid(device.AndroidSDK), device.AppVersion)
			writeLine(&out, "последний запуск: %s", device.LastSeenAt.In(location).Format("02.01 15:04"))
			writeLine(&out, "сессий за сутки: %d", device.SessionsInPeriod)
			writeLine(&out, "время за сутки: %s", formatDuration(device.SessionSecondsInPeriod))
			writeLine(&out, "всего запусков: %d", device.LaunchCount)
			writeLine(&out, "")
		}
		if data.DeviceTotal > data.DeviceLimit {
			writeLine(&out, "показаны последние %d из %d устройств", data.DeviceLimit, data.DeviceTotal)
			writeLine(&out, "")
		}
	}

	if len(data.Summary.TopModels) > 0 {
		writeLine(&out, "топ моделей:")
		for _, model := range data.Summary.TopModels {
			writeLine(&out, "%s - %d", model.Name, model.Count)
		}
		writeLine(&out, "")
	}

	if len(data.OldVersionDevices) > 0 {
		writeLine(&out, "старые версии:")
		for _, device := range data.OldVersionDevices {
			writeLine(&out, "%s · %s · последний запуск %s", device.UID, device.AppVersion, daysAgo(device.LastSeenAt, data.PeriodEnd))
		}
	}

	return strings.TrimSpace(out.String())
}

func SplitTelegramMessages(text string) []string {
	if len(text) <= telegramMessageLimit {
		return []string{text}
	}
	lines := strings.Split(text, "\n")
	messages := make([]string, 0)
	var current strings.Builder
	for _, line := range lines {
		extra := len(line) + 1
		if current.Len() > 0 && current.Len()+extra > telegramMessageLimit {
			messages = append(messages, strings.TrimSpace(current.String()))
			current.Reset()
		}
		if len(line) > telegramMessageLimit {
			for len(line) > telegramMessageLimit {
				messages = append(messages, line[:telegramMessageLimit])
				line = line[telegramMessageLimit:]
			}
		}
		current.WriteString(line)
		current.WriteByte('\n')
	}
	if strings.TrimSpace(current.String()) != "" {
		messages = append(messages, strings.TrimSpace(current.String()))
	}
	return messages
}

func nextRun(now time.Time, reportTime time.Time, location *time.Location) time.Time {
	next := time.Date(now.Year(), now.Month(), now.Day(), reportTime.Hour(), reportTime.Minute(), 0, 0, location)
	if !next.After(now) {
		next = next.Add(24 * time.Hour)
	}
	return next
}

func writeLine(out *strings.Builder, format string, args ...any) {
	if len(args) == 0 {
		out.WriteString(format)
	} else {
		out.WriteString(fmt.Sprintf(format, args...))
	}
	out.WriteByte('\n')
}

func formatDuration(seconds int) string {
	if seconds <= 0 {
		return "0ч 00м"
	}
	hours := seconds / 3600
	minutes := (seconds % 3600) / 60
	return fmt.Sprintf("%dч %02dм", hours, minutes)
}

func formatModel(manufacturer *string, model *string) string {
	parts := make([]string, 0, 2)
	if manufacturer != nil && strings.TrimSpace(*manufacturer) != "" {
		parts = append(parts, strings.ToLower(strings.TrimSpace(*manufacturer)))
	}
	if model != nil && strings.TrimSpace(*model) != "" {
		parts = append(parts, strings.ToLower(strings.TrimSpace(*model)))
	}
	if len(parts) == 0 {
		return "unknown device"
	}
	return strings.Join(parts, " ")
}

func formatAndroid(androidSDK *int) string {
	if androidSDK == nil {
		return "android unknown"
	}
	if *androidSDK >= 35 {
		return "android 15"
	}
	if *androidSDK == 34 {
		return "android 14"
	}
	return fmt.Sprintf("android sdk %d", *androidSDK)
}

func daysAgo(value time.Time, now time.Time) string {
	days := int(now.Sub(value) / (24 * time.Hour))
	if days <= 0 {
		return "сегодня"
	}
	return fmt.Sprintf("%dд назад", days)
}
