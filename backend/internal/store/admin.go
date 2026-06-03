package store

import (
	"context"
	"database/sql"
	"fmt"
	"strconv"
	"strings"
	"time"
)

func (s *Store) Summary(ctx context.Context) (Summary, error) {
	return s.SummaryAt(ctx, time.Now().UTC())
}

func (s *Store) SummaryAt(ctx context.Context, now time.Time) (Summary, error) {
	now = now.UTC()
	cut24 := now.Add(-24 * time.Hour)
	cut7 := now.AddDate(0, 0, -7)

	summary := Summary{}
	var err error
	if summary.TotalDevices, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM devices"); err != nil {
		return Summary{}, err
	}
	if summary.New24h, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM devices WHERE first_seen_at >= ?", cut24); err != nil {
		return Summary{}, err
	}
	if summary.Active24h, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM devices WHERE last_seen_at >= ?", cut24); err != nil {
		return Summary{}, err
	}
	if summary.Active7d, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM devices WHERE last_seen_at >= ?", cut7); err != nil {
		return Summary{}, err
	}
	if summary.Inactive7d, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM devices WHERE last_seen_at < ?", cut7); err != nil {
		return Summary{}, err
	}
	if summary.Events24h, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM events WHERE created_at >= ?", cut24); err != nil {
		return Summary{}, err
	}
	if summary.Sessions24h, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM events WHERE created_at >= ? AND event_type = 'app_background' AND COALESCE(session_duration_seconds, 0) > 0", cut24); err != nil {
		return Summary{}, err
	}
	if summary.TotalSessionSeconds24h, err = queryInt(ctx, s.db, "SELECT COALESCE(SUM(session_duration_seconds), 0) FROM events WHERE created_at >= ? AND event_type = 'app_background'", cut24); err != nil {
		return Summary{}, err
	}
	if summary.Versions, err = countValues(ctx, s.db, "SELECT app_version, COUNT(*) FROM devices GROUP BY app_version ORDER BY COUNT(*) DESC, app_version ASC LIMIT 20"); err != nil {
		return Summary{}, err
	}
	if summary.TopModels, err = countValues(ctx, s.db, `
SELECT lower(trim(COALESCE(manufacturer, '') || ' ' || COALESCE(model, ''))) AS model_name, COUNT(*)
FROM devices
WHERE trim(COALESCE(manufacturer, '') || ' ' || COALESCE(model, '')) <> ''
GROUP BY model_name
ORDER BY COUNT(*) DESC, model_name ASC
LIMIT 20`); err != nil {
		return Summary{}, err
	}

	return summary, nil
}

func (s *Store) ListDevices(ctx context.Context, now time.Time, limit int) ([]Device, error) {
	if limit <= 0 || limit > 500 {
		limit = 500
	}
	rows, err := s.db.QueryContext(ctx, `
SELECT uid_number,
       install_id,
       first_seen_at,
       last_seen_at,
       app_version,
       version_code,
       manufacturer,
       model,
       android_sdk,
       launch_count,
       heartbeat_count,
       total_session_seconds
FROM devices
ORDER BY last_seen_at DESC
LIMIT ?`, limit)
	if err != nil {
		return nil, fmt.Errorf("list devices: %w", err)
	}
	defer rows.Close()

	devices := make([]Device, 0)
	for rows.Next() {
		device, err := scanDevice(rows, now)
		if err != nil {
			return nil, err
		}
		devices = append(devices, device)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate devices: %w", err)
	}
	return devices, nil
}

func (s *Store) ListEvents(ctx context.Context, filter EventFilter) ([]EventRecord, error) {
	if filter.Limit <= 0 {
		filter.Limit = 100
	}
	if filter.Limit > 500 {
		filter.Limit = 500
	}

	query := strings.Builder{}
	query.WriteString(`
SELECT e.id,
       d.uid_number,
       e.event_type,
       e.app_version,
       e.version_code,
       e.session_id,
       e.session_duration_seconds,
       e.created_at
FROM events e
JOIN devices d ON d.id = e.device_id`)

	conditions := make([]string, 0, 2)
	args := make([]any, 0, 3)
	if filter.UID > 0 {
		conditions = append(conditions, "d.uid_number = ?")
		args = append(args, filter.UID)
	}
	if filter.Since != nil {
		conditions = append(conditions, "e.created_at >= ?")
		args = append(args, filter.Since.UTC())
	}
	if len(conditions) > 0 {
		query.WriteString(" WHERE ")
		query.WriteString(strings.Join(conditions, " AND "))
	}
	query.WriteString(" ORDER BY e.created_at DESC LIMIT ?")
	args = append(args, filter.Limit)

	rows, err := s.db.QueryContext(ctx, query.String(), args...)
	if err != nil {
		return nil, fmt.Errorf("list events: %w", err)
	}
	defer rows.Close()

	events := make([]EventRecord, 0)
	for rows.Next() {
		var record EventRecord
		var uidNumber int
		var sessionID sql.NullString
		var sessionDuration sql.NullInt64
		if err := rows.Scan(
			&record.ID,
			&uidNumber,
			&record.EventType,
			&record.AppVersion,
			&record.VersionCode,
			&sessionID,
			&sessionDuration,
			&record.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan event: %w", err)
		}
		record.UID = FormatUID(uidNumber)
		record.SessionID = stringPtr(sessionID)
		record.SessionDurationSeconds = intPtr(sessionDuration)
		events = append(events, record)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate events: %w", err)
	}
	return events, nil
}

func (s *Store) DailyReportData(ctx context.Context, start time.Time, end time.Time, maxDevices int) (ReportData, error) {
	if maxDevices <= 0 {
		maxDevices = 20
	}
	summary, err := s.summaryForPeriod(ctx, start, end)
	if err != nil {
		return ReportData{}, err
	}

	totalDevices, err := queryInt(ctx, s.db, "SELECT COUNT(*) FROM devices")
	if err != nil {
		return ReportData{}, err
	}
	latestVersionCode, err := queryInt(ctx, s.db, "SELECT COALESCE(MAX(version_code), 0) FROM devices")
	if err != nil {
		return ReportData{}, err
	}
	devices, err := s.reportDevices(ctx, start, end, maxDevices, "")
	if err != nil {
		return ReportData{}, err
	}
	oldDevices, err := s.reportDevices(ctx, start, end, maxDevices, "version_code < "+strconv.Itoa(latestVersionCode))
	if err != nil {
		return ReportData{}, err
	}

	return ReportData{
		PeriodStart:       start.UTC(),
		PeriodEnd:         end.UTC(),
		Summary:           summary,
		Devices:           devices,
		DeviceLimit:       maxDevices,
		DeviceTotal:       totalDevices,
		OldVersionDevices: oldDevices,
		LatestVersionCode: latestVersionCode,
	}, nil
}

func (s *Store) summaryForPeriod(ctx context.Context, start time.Time, end time.Time) (Summary, error) {
	summary, err := s.SummaryAt(ctx, end)
	if err != nil {
		return Summary{}, err
	}
	if summary.New24h, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM devices WHERE first_seen_at >= ? AND first_seen_at < ?", start, end); err != nil {
		return Summary{}, err
	}
	if summary.Events24h, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM events WHERE created_at >= ? AND created_at < ?", start, end); err != nil {
		return Summary{}, err
	}
	if summary.Sessions24h, err = queryInt(ctx, s.db, "SELECT COUNT(*) FROM events WHERE created_at >= ? AND created_at < ? AND event_type = 'app_background' AND COALESCE(session_duration_seconds, 0) > 0", start, end); err != nil {
		return Summary{}, err
	}
	if summary.TotalSessionSeconds24h, err = queryInt(ctx, s.db, "SELECT COALESCE(SUM(session_duration_seconds), 0) FROM events WHERE created_at >= ? AND created_at < ? AND event_type = 'app_background'", start, end); err != nil {
		return Summary{}, err
	}
	return summary, nil
}

func (s *Store) reportDevices(ctx context.Context, start time.Time, end time.Time, limit int, extraWhere string) ([]ReportDevice, error) {
	query := strings.Builder{}
	query.WriteString(`
SELECT d.uid_number,
       d.install_id,
       d.first_seen_at,
       d.last_seen_at,
       d.app_version,
       d.version_code,
       d.manufacturer,
       d.model,
       d.android_sdk,
       d.launch_count,
       d.heartbeat_count,
       d.total_session_seconds,
       (
           SELECT COUNT(*)
           FROM events e
           WHERE e.device_id = d.id
             AND e.created_at >= ?
             AND e.created_at < ?
             AND e.event_type = 'app_background'
             AND COALESCE(e.session_duration_seconds, 0) > 0
       ) AS sessions_in_period,
       (
           SELECT COALESCE(SUM(e.session_duration_seconds), 0)
           FROM events e
           WHERE e.device_id = d.id
             AND e.created_at >= ?
             AND e.created_at < ?
             AND e.event_type = 'app_background'
       ) AS session_seconds_in_period
FROM devices d`)
	if strings.TrimSpace(extraWhere) != "" {
		query.WriteString(" WHERE ")
		query.WriteString(extraWhere)
	}
	query.WriteString(" ORDER BY d.last_seen_at DESC LIMIT ?")

	rows, err := s.db.QueryContext(ctx, query.String(), start, end, start, end, limit)
	if err != nil {
		return nil, fmt.Errorf("report devices: %w", err)
	}
	defer rows.Close()

	devices := make([]ReportDevice, 0)
	for rows.Next() {
		device, err := scanReportDevice(rows, end)
		if err != nil {
			return nil, err
		}
		devices = append(devices, device)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate report devices: %w", err)
	}
	return devices, nil
}

func queryInt(ctx context.Context, db *sql.DB, query string, args ...any) (int, error) {
	var value int
	if err := db.QueryRowContext(ctx, query, args...).Scan(&value); err != nil {
		return 0, fmt.Errorf("query int: %w", err)
	}
	return value, nil
}

func countValues(ctx context.Context, db *sql.DB, query string, args ...any) ([]CountValue, error) {
	rows, err := db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("count values: %w", err)
	}
	defer rows.Close()

	values := make([]CountValue, 0)
	for rows.Next() {
		var value CountValue
		if err := rows.Scan(&value.Name, &value.Count); err != nil {
			return nil, fmt.Errorf("scan count value: %w", err)
		}
		values = append(values, value)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate count values: %w", err)
	}
	return values, nil
}

func scanDevice(rows interface {
	Scan(dest ...any) error
}, now time.Time) (Device, error) {
	var uidNumber int
	var installID string
	var manufacturer sql.NullString
	var model sql.NullString
	var androidSDK sql.NullInt64
	var device Device
	if err := rows.Scan(
		&uidNumber,
		&installID,
		&device.FirstSeenAt,
		&device.LastSeenAt,
		&device.AppVersion,
		&device.VersionCode,
		&manufacturer,
		&model,
		&androidSDK,
		&device.LaunchCount,
		&device.HeartbeatCount,
		&device.TotalSessionSeconds,
	); err != nil {
		return Device{}, fmt.Errorf("scan device: %w", err)
	}
	device.UIDNumber = uidNumber
	device.UID = FormatUID(uidNumber)
	device.InstallIDShort = shortInstallID(installID)
	device.Manufacturer = stringPtr(manufacturer)
	device.Model = stringPtr(model)
	device.AndroidSDK = intPtr(androidSDK)
	device.Active24h = !device.LastSeenAt.Before(now.UTC().Add(-24 * time.Hour))
	device.Active7d = !device.LastSeenAt.Before(now.UTC().AddDate(0, 0, -7))
	return device, nil
}

func scanReportDevice(rows interface {
	Scan(dest ...any) error
}, now time.Time) (ReportDevice, error) {
	var reportDevice ReportDevice
	device, err := scanDevice(reportDeviceScanner{rows: rows, reportDevice: &reportDevice}, now)
	if err != nil {
		return ReportDevice{}, err
	}
	reportDevice.Device = device
	return reportDevice, nil
}

type reportDeviceScanner struct {
	rows         interface{ Scan(dest ...any) error }
	reportDevice *ReportDevice
}

func (s reportDeviceScanner) Scan(dest ...any) error {
	dest = append(dest, &s.reportDevice.SessionsInPeriod, &s.reportDevice.SessionSecondsInPeriod)
	return s.rows.Scan(dest...)
}

func FormatUID(number int) string {
	return fmt.Sprintf("uid:%03d", number)
}

func shortInstallID(installID string) string {
	if len(installID) <= 8 {
		return installID
	}
	return installID[:8]
}

func stringPtr(value sql.NullString) *string {
	if !value.Valid {
		return nil
	}
	return &value.String
}

func intPtr(value sql.NullInt64) *int {
	if !value.Valid {
		return nil
	}
	converted := int(value.Int64)
	return &converted
}
