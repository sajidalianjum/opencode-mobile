package dev.opencode.mobile.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground service that keeps the app process (and SSE connection) alive
 * while agent tasks are running, so completion / input-requested events can
 * post notifications even when the app is in background.
 *
 * Crash hardening for ForegroundServiceDidNotStartInTimeException:
 * - The service is started with plain startService(), NEVER
 *   startForegroundService(): the latter arms a ~10s system deadline that
 *   kills the process if startForeground() is even slightly delayed (cold
 *   start, slow device). startService() has no such deadline, so the
 *   onCreate() promotion below can never trip the crash.
 * - onCreate() foregrounds synchronously with an inline minimal notification
 *   (no helper, no PendingIntent, no resource lookup).
 * - start() never queues a doomed background start: skips when the app is
 *   backgrounded or when the service is already running.
 */
class AgentMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        // Promote synchronously here with zero I/O so background
        // monitoring is active ASAP. Started via startService() (see
        // start()), so there is no startForegroundService() deadline — a
        // slow/failed promotion can never kill the process.
        startForegroundImmediate()
    }

    private fun startForegroundImmediate() {
        if (foregroundStarted) return
        try {
            val notification = buildMinimalNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    AgentNotificationHelper.MONITOR_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(
                    AgentNotificationHelper.MONITOR_NOTIFICATION_ID,
                    notification,
                )
            }
            foregroundStarted = true
        } catch (e: Exception) {
            // Platform startForeground failed (bad notification / wrong type).
            // Retry once with a bare notification; if that still fails the
            // service simply keeps running unforegrounded (no deadline to
            // miss since we were started with startService()).
            Log.w(TAG, "immediate startForeground failed: ${e.message}")
            try {
                val fallback = Notification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        AgentNotificationHelper.MONITOR_NOTIFICATION_ID,
                        fallback,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(AgentNotificationHelper.MONITOR_NOTIFICATION_ID, fallback)
                }
                foregroundStarted = true
            } catch (e2: Exception) {
                Log.w(TAG, "emergency startForeground failed: ${e2.message}")
            }
        }
    }

    /**
     * Inline minimal notification: no helper init, no
     * PendingIntent, no app-resource lookup (uses a system icon). Channel
     * creation is best-effort; the channel already exists from Application
     * init in the common case.
     */
    private fun buildMinimalNotification(): Notification {
        val channelId = AgentNotificationHelper.CHANNEL_MONITOR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Task Monitoring",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            } catch (_: Exception) {}
            return Notification.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("OpenCode Mobile is monitoring tasks")
                .setContentText("Listening for agent updates")
                .setOngoing(true)
                .build()
        }
        @Suppress("DEPRECATION")
        return Notification.Builder(this)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("OpenCode Mobile is monitoring tasks")
            .setContentText("Listening for agent updates")
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Foreground promotion was already done in onCreate; re-assert in case
        // the system recreated the service without calling onCreate promotion
        // (or the first attempt failed).
        startForegroundImmediate()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        foregroundStarted = false
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AgentMonitorService"

        @Volatile
        private var isRunning = false

        /** True while the service object exists; redundant starts are skipped. */
        fun isServiceRunning(): Boolean = isRunning

        fun start(context: Context) {
            // Every user prompt marks the session Running, which funnels here
            // — including a foreground "hi" in the chat screen. This MUST NOT
            // be able to crash the app.
            //
            // Started with plain startService(), NOT startForegroundService():
            // the service promotes itself via startForeground() in onCreate().
            // startForegroundService() arms a ~10s system deadline and any
            // delay in promotion (cold start, slow device, queued starts)
            // kills the process. startService() has no deadline, so the same
            // promotion can never crash.
            //
            // Android 12+ forbids starting a foreground service from the
            // background, so a background re-trigger is skipped: the service
            // is either already running (no-op needed) or the process can't
            // foreground — the foreground-initiated service keeps running
            // after the app is backgrounded, so monitoring is preserved.
            if (isRunning) return
            if (!AppVisibility.isForeground) {
                Log.d(TAG, "Skipping monitor start while app is in background")
                return
            }
            try {
                val appContext = context.applicationContext ?: context
                val intent = Intent(appContext, AgentMonitorService::class.java)
                appContext.startService(intent)
            } catch (e: SecurityException) {
                Log.w(TAG, "FGS start blocked by system policy, skipping: ${e.message}")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "FGS start illegal state, skipping: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start monitor service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AgentMonitorService::class.java))
            } catch (_: Exception) {}
        }
    }
}
