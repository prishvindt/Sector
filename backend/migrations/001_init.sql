CREATE TABLE devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uid_number INTEGER UNIQUE NOT NULL,
    install_id TEXT UNIQUE NOT NULL,
    first_seen_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL,
    app_version TEXT NOT NULL,
    version_code INTEGER NOT NULL,
    manufacturer TEXT,
    model TEXT,
    android_sdk INTEGER,
    launch_count INTEGER NOT NULL DEFAULT 0,
    heartbeat_count INTEGER NOT NULL DEFAULT 0,
    total_session_seconds INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    app_version TEXT,
    version_code INTEGER,
    session_id TEXT,
    session_duration_seconds INTEGER,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
);

CREATE INDEX idx_devices_install_id ON devices(install_id);
CREATE INDEX idx_devices_last_seen_at ON devices(last_seen_at);
CREATE INDEX idx_events_created_at ON events(created_at);
CREATE INDEX idx_events_device_id ON events(device_id);
