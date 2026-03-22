package com.example.myapplication.tmr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.tmr.MainActivity
import com.example.myapplication.tmr.R
import com.example.myapplication.tmr.data.models.CueInfo
import com.example.myapplication.tmr.data.models.TickEvent
import com.example.myapplication.tmr.data.network.VeriteWebSocket
import com.example.myapplication.tmr.data.repository.VeriteRepository
import com.example.myapplication.tmr.di.TmrDependencyContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


private const val TAG = "VeriteService"
private const val CHANNEL_ID = "verite_tmr_session"
private const val NOTIFICATION_ID = 1001
private const val WAKELOCK_TAG = "VeriteTMR::SessionWakeLock"

/**
 * Foreground service for overnight TMR sessions.
 *
 * CRITICAL: Without this, Android 12+ will kill the app within minutes
 * of the screen turning off. The WebSocket connection drops, tick
 * collection stops, and the entire session is lost.
 *
 * This service:
 *   1. Holds a PARTIAL_WAKE_LOCK to keep the CPU running
 *   2. Shows a persistent notification with session status
 *   3. Owns the WebSocket connection lifecycle
 *   4. Survives Activity destruction during screen-off
 *   5. Updates the notification with cue count in real time
 *
 * The ViewModel binds to this service and observes its flows.
 * The service owns the "truth" of the connection during overnight sessions.
 */
class VeriteForegroundService : Service() {

    private val repository: VeriteRepository
        get() = TmrDependencyContainer.veriteRepository

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickCollectorJob: Job? = null

    // ── Service-owned state ──────────────────────────────────────────────────

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _cueCount = MutableStateFlow(0)
    val cueCount: StateFlow<Int> = _cueCount.asStateFlow()

    private val _latestTick = MutableStateFlow<TickEvent?>(null)
    val latestTick: StateFlow<TickEvent?> = _latestTick.asStateFlow()

    val tickEvents: SharedFlow<TickEvent> get() = repository.tickEvents
    val cueEvents: SharedFlow<CueInfo> get() = repository.cueEvents
    val connectionState: StateFlow<VeriteWebSocket.ConnectionState>
        get() = repository.connectionState

    // ── Binder for Activity/ViewModel binding ────────────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): VeriteForegroundService = this@VeriteForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_STOP  -> stopSession()
        }
        return START_STICKY  // Restart if killed by system
    }

    override fun onDestroy() {
        stopSession()
        serviceScope.cancel()
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    // ── Session management ───────────────────────────────────────────────────

    fun startSession() {
        // Acquire wake lock — keeps CPU active during sleep
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            // 9 hours max — covers 8h session + buffer. Auto-releases if we crash.
            acquire(9 * 60 * 60 * 1000L)
        }

        // Start foreground with notification
        startForeground(NOTIFICATION_ID, buildNotification(0))

        // Connect WebSocket owned by service scope (survives Activity death)
        repository.connectWebSocket(serviceScope)
        _sessionActive.value = true

        // Collect ticks to update notification + state
        tickCollectorJob?.cancel()
        tickCollectorJob = serviceScope.launch {
            repository.tickEvents.collect { tick ->
                _latestTick.value = tick
                if (tick.cue != null) {
                    val newCount = _cueCount.value + 1
                    _cueCount.value = newCount
                    // Update notification every 5 cues to avoid battery drain
                    if (newCount % 5 == 0) {
                        updateNotification(newCount)
                    }
                }
            }
        }

        Log.i(TAG, "Session started with wake lock")
    }

    fun stopSession() {
        tickCollectorJob?.cancel()
        tickCollectorJob = null
        repository.disconnectWebSocket()
        _sessionActive.value = false
        _cueCount.value = 0

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Session stopped, wake lock released")
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TMR Session",
                NotificationManager.IMPORTANCE_LOW  // Low = no sound, just persistent icon
            ).apply {
                description = "Active TMR sleep session monitoring"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(cueCount: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VeriteForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vérité TMR Active")
            .setContentText(
                if (cueCount > 0) "$cueCount cues delivered"
                else "Monitoring sleep…"
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingTap)
            .addAction(0, "Stop Session", pendingStop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(cueCount: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(cueCount))
    }

    companion object {
        const val ACTION_START = "com.example.myapplication.tmr.action.START_SESSION"
        const val ACTION_STOP  = "com.example.myapplication.tmr.action.STOP_SESSION"

        fun startIntent(context: Context): Intent =
            Intent(context, VeriteForegroundService::class.java).apply {
                action = ACTION_START
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, VeriteForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
