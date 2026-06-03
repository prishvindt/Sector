package com.prishvindt.sector.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.prishvindt.sector.domain.telemetry.TelemetrySessionTracker

class TelemetryLifecycleObserver(
    private val sessionTracker: TelemetrySessionTracker
) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        sessionTracker.onForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        sessionTracker.onBackground()
    }
}
