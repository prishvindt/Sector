package store

import "time"

type EventInput struct {
	InstallID              string
	EventType              string
	AppVersion             string
	VersionCode            int
	Manufacturer           string
	Model                  string
	AndroidSDK             *int
	SessionID              string
	SessionDurationSeconds *int
	CreatedAt              time.Time
}

type CountValue struct {
	Name  string `json:"name"`
	Count int    `json:"count"`
}

type Summary struct {
	TotalDevices            int          `json:"totalDevices"`
	New24h                  int          `json:"new24h"`
	Active24h               int          `json:"active24h"`
	Active7d                int          `json:"active7d"`
	Inactive7d              int          `json:"inactive7d"`
	Events24h               int          `json:"events24h"`
	Sessions24h             int          `json:"sessions24h"`
	TotalSessionSeconds24h  int          `json:"totalSessionSeconds24h"`
	Versions                []CountValue `json:"versions"`
	TopModels               []CountValue `json:"topModels"`
}

type Device struct {
	UID                 string    `json:"uid"`
	UIDNumber           int       `json:"-"`
	InstallIDShort      string    `json:"installIdShort"`
	FirstSeenAt         time.Time `json:"firstSeenAt"`
	LastSeenAt          time.Time `json:"lastSeenAt"`
	AppVersion          string    `json:"appVersion"`
	VersionCode         int       `json:"versionCode"`
	Manufacturer        *string   `json:"manufacturer"`
	Model               *string   `json:"model"`
	AndroidSDK          *int      `json:"androidSdk"`
	LaunchCount         int       `json:"launchCount"`
	HeartbeatCount      int       `json:"heartbeatCount"`
	TotalSessionSeconds int       `json:"totalSessionSeconds"`
	Active24h           bool      `json:"active24h"`
	Active7d            bool      `json:"active7d"`
}

type EventRecord struct {
	ID                     int64     `json:"id"`
	UID                    string    `json:"uid"`
	EventType              string    `json:"eventType"`
	AppVersion             string    `json:"appVersion"`
	VersionCode            int       `json:"versionCode"`
	SessionID              *string   `json:"sessionId"`
	SessionDurationSeconds *int      `json:"sessionDurationSeconds"`
	CreatedAt              time.Time `json:"createdAt"`
}

type EventFilter struct {
	Limit int
	UID   int
	Since *time.Time
}

type ReportDevice struct {
	Device
	SessionsInPeriod       int
	SessionSecondsInPeriod int
}

type ReportData struct {
	PeriodStart        time.Time
	PeriodEnd          time.Time
	Summary            Summary
	Devices            []ReportDevice
	DeviceLimit        int
	DeviceTotal        int
	OldVersionDevices  []ReportDevice
	LatestVersionCode  int
}
