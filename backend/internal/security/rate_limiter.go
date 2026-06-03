package security

import (
	"sync"
	"time"
)

type RateLimiter struct {
	mu      sync.Mutex
	entries map[string]entry
	now     func() time.Time
}

type entry struct {
	windowStart time.Time
	count       int
}

func NewRateLimiter() *RateLimiter {
	return &RateLimiter{
		entries: make(map[string]entry),
		now:     time.Now,
	}
}

func (l *RateLimiter) Allow(key string, limit int, window time.Duration) bool {
	if key == "" {
		key = "unknown"
	}
	now := l.now()

	l.mu.Lock()
	defer l.mu.Unlock()

	current := l.entries[key]
	if current.windowStart.IsZero() || now.Sub(current.windowStart) >= window {
		l.entries[key] = entry{windowStart: now, count: 1}
		l.cleanupLocked(now, window)
		return true
	}

	if current.count >= limit {
		return false
	}
	current.count++
	l.entries[key] = current
	return true
}

func (l *RateLimiter) cleanupLocked(now time.Time, window time.Duration) {
	if len(l.entries) < 1000 {
		return
	}
	for key, current := range l.entries {
		if now.Sub(current.windowStart) >= window*2 {
			delete(l.entries, key)
		}
	}
}
