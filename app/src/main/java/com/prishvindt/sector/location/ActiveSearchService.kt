package com.prishvindt.sector.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.prishvindt.sector.MainActivity
import com.prishvindt.sector.R
import com.prishvindt.sector.data.GpsMode
import com.prishvindt.sector.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ActiveSearchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var tracker: LocationTracker
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        tracker = LocationTracker(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { settingsRepository.setActiveSearchEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }

        if (!hasForegroundLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mode = intent?.getStringExtra(EXTRA_GPS_MODE)
            ?.let { runCatching { GpsMode.valueOf(it) }.getOrNull() }
            ?: GpsMode.NORMAL

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        tracker.start(mode)
        return START_STICKY
    }

    override fun onDestroy() {
        tracker.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Сектор")
            .setContentText("Активный поиск: GPS включён")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Активный поиск: GPS включён\nКоординаты обновляются локально"
                )
            )
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                0,
                "Остановить",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, ActiveSearchService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Активный поиск",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun hasForegroundLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        private const val CHANNEL_ID = "active_search"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.prishvindt.sector.action.STOP_ACTIVE_SEARCH"
        const val EXTRA_GPS_MODE = "gps_mode"

        fun start(context: Context, mode: GpsMode) {
            val intent = Intent(context, ActiveSearchService::class.java)
                .putExtra(EXTRA_GPS_MODE, mode.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ActiveSearchService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
