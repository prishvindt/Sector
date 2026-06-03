package telemetry

import "testing"

func validRequest() EventRequest {
	sdk := 35
	return EventRequest{
		InstallID:    "550e8400-e29b-41d4-a716-446655440000",
		EventType:    EventTypeAppStart,
		AppVersion:   "0.1.8",
		VersionCode:  9,
		Manufacturer: "samsung",
		Model:        "SM-G991B",
		AndroidSDK:   &sdk,
	}
}

func TestValidateRejectsMissingInstallID(t *testing.T) {
	req := validRequest()
	req.InstallID = ""

	if _, err := ValidateEvent(req); err == nil {
		t.Fatal("ValidateEvent accepted missing installId")
	}
}

func TestValidateRejectsBadEventType(t *testing.T) {
	req := validRequest()
	req.EventType = "location"

	if _, err := ValidateEvent(req); err == nil {
		t.Fatal("ValidateEvent accepted bad eventType")
	}
}
