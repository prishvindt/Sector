package telemetry

import (
	"errors"
	"regexp"
	"strings"
)

const (
	EventTypeAppStart      = "app_start"
	EventTypeAppBackground = "app_background"
	EventTypeHeartbeat     = "heartbeat"
)

type EventRequest struct {
	InstallID              string `json:"installId"`
	EventType              string `json:"eventType"`
	AppVersion             string `json:"appVersion"`
	VersionCode            int    `json:"versionCode"`
	Manufacturer           string `json:"manufacturer"`
	Model                  string `json:"model"`
	AndroidSDK             *int   `json:"androidSdk"`
	SessionID              string `json:"sessionId"`
	SessionDurationSeconds *int   `json:"sessionDurationSeconds"`
}

type Event struct {
	InstallID              string
	EventType              string
	AppVersion             string
	VersionCode            int
	Manufacturer           string
	Model                  string
	AndroidSDK             *int
	SessionID              string
	SessionDurationSeconds *int
}

var uuidLikePattern = regexp.MustCompile(`^[A-Fa-f0-9]{8}-?[A-Fa-f0-9]{4}-?[A-Fa-f0-9]{4}-?[A-Fa-f0-9]{4}-?[A-Fa-f0-9]{12}$`)

func ValidateEvent(req EventRequest) (Event, error) {
	event := Event{
		InstallID:    strings.TrimSpace(req.InstallID),
		EventType:    strings.TrimSpace(req.EventType),
		AppVersion:   strings.TrimSpace(req.AppVersion),
		VersionCode:  req.VersionCode,
		Manufacturer: cleanOptional(req.Manufacturer, 64),
		Model:        cleanOptional(req.Model, 64),
		AndroidSDK:   req.AndroidSDK,
		SessionID:    strings.TrimSpace(req.SessionID),
	}

	if event.InstallID == "" {
		return Event{}, errors.New("installId is required")
	}
	if len(event.InstallID) > 64 || !uuidLikePattern.MatchString(event.InstallID) {
		return Event{}, errors.New("installId must be a UUID-like value")
	}
	if event.AppVersion == "" {
		return Event{}, errors.New("appVersion is required")
	}
	if len(event.AppVersion) > 32 {
		return Event{}, errors.New("appVersion is too long")
	}
	if event.VersionCode <= 0 {
		return Event{}, errors.New("versionCode must be positive")
	}
	if event.VersionCode > 1000000 {
		return Event{}, errors.New("versionCode is out of range")
	}
	if event.AndroidSDK != nil && (*event.AndroidSDK < 1 || *event.AndroidSDK > 100) {
		return Event{}, errors.New("androidSdk is out of range")
	}

	switch event.EventType {
	case EventTypeAppStart:
		event.SessionID = ""
	case EventTypeHeartbeat:
		if event.SessionID != "" && (len(event.SessionID) > 64 || !uuidLikePattern.MatchString(event.SessionID)) {
			return Event{}, errors.New("sessionId must be a UUID-like value")
		}
	case EventTypeAppBackground:
		if event.SessionID != "" && (len(event.SessionID) > 64 || !uuidLikePattern.MatchString(event.SessionID)) {
			return Event{}, errors.New("sessionId must be a UUID-like value")
		}
		if req.SessionDurationSeconds != nil {
			if *req.SessionDurationSeconds < 0 || *req.SessionDurationSeconds > 604800 {
				return Event{}, errors.New("sessionDurationSeconds is out of range")
			}
			event.SessionDurationSeconds = req.SessionDurationSeconds
		}
	default:
		return Event{}, errors.New("eventType is invalid")
	}

	return event, nil
}

func cleanOptional(value string, maxLen int) string {
	value = strings.TrimSpace(value)
	if len(value) > maxLen {
		return value[:maxLen]
	}
	return value
}
