package dev.opencode.mobile.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.opencode.mobile.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * System notifications for agent activity while the app is backgrounded.
 * Foreground sessions are owned by in-app UI; this helper never posts while
 * the user looks at the session ([AppVisibility.shouldNotify]).
 */
@Singleton
class AgentNotificationHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        init {
            createNotificationChannels()
        }

        private fun createNotificationChannels() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val alertsChannel =
                    NotificationChannel(
                        CHANNEL_ALERTS,
                        "Agent Task Alerts",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = "Notifications for completed tasks, errors, and input prompts"
                        enableVibration(true)
                    }
                notificationManager.createNotificationChannel(alertsChannel)

                val monitorChannel =
                    NotificationChannel(
                        CHANNEL_MONITOR,
                        "Task Monitoring",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "Ongoing status while agent tasks are running"
                        setShowBadge(false)
                    }
                notificationManager.createNotificationChannel(monitorChannel)
            }
        }

        fun canPost(): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
            return true
        }

        private fun chatIntent(
            sessionId: String,
            title: String,
            worktree: String,
        ): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    // Unique action per session: extras are ignored for PendingIntent
                    // equality, so without this the system may reuse a previous
                    // PendingIntent and deliver a stale session_id.
                    action = "dev.opencode.mobile.OPEN_SESSION_$sessionId"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    putExtra(EXTRA_SESSION_TITLE, title)
                    putExtra(EXTRA_WORKTREE, worktree)
                }
            return PendingIntent.getActivity(
                context,
                sessionId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun smallIcon(): Int {
            // App launcher icon exists (see manifest @mipmap/ic_launcher).
            // Fall back to a system icon only if resource lookup fails.
            return try {
                val id =
                    context.resources.getIdentifier(
                        "ic_launcher",
                        "mipmap",
                        context.packageName,
                    )
                if (id != 0) id else android.R.drawable.stat_notify_chat
            } catch (_: Exception) {
                android.R.drawable.stat_notify_chat
            }
        }

        fun showTaskCompleted(
            sessionTitle: String,
            sessionId: String,
            worktree: String,
        ) {
            if (!canPost()) return
            // Foreground: in-app UI owns this; never post a system banner.
            if (AppVisibility.isForeground) return
            if (!AppVisibility.shouldNotify(sessionId)) return
            val title = sessionTitle.ifBlank { "Session" }
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ALERTS)
                    .setSmallIcon(smallIcon())
                    .setContentTitle("Task completed")
                    .setContentText("OpenCode finished working on: $title")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("OpenCode finished working on: $title"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setContentIntent(chatIntent(sessionId, title, worktree))
                    .build()
            notificationManager.notify(sessionId.hashCode(), notification)
        }

        fun showTaskError(
            sessionTitle: String,
            errorMessage: String,
            sessionId: String,
            worktree: String,
        ) {
            if (!canPost()) return
            // Foreground: in-app UI owns this; never post a system banner.
            if (AppVisibility.isForeground) return
            if (!AppVisibility.shouldNotify(sessionId)) return
            val title = sessionTitle.ifBlank { "Session" }
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ALERTS)
                    .setSmallIcon(smallIcon())
                    .setContentTitle("Task failed")
                    .setContentText("$title: $errorMessage")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$title: $errorMessage"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setContentIntent(chatIntent(sessionId, title, worktree))
                    .build()
            notificationManager.notify(sessionId.hashCode() + 1, notification)
        }

        fun showAwaitingInput(
            sessionTitle: String,
            prompt: String,
            sessionId: String,
            worktree: String,
        ) {
            if (!canPost()) return
            // Foreground: in-app UI owns this; never post a system banner.
            if (AppVisibility.isForeground) return
            if (!AppVisibility.shouldNotify(sessionId)) return
            val title = sessionTitle.ifBlank { "Session" }
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ALERTS)
                    .setSmallIcon(smallIcon())
                    .setContentTitle("Awaiting input")
                    .setContentText("$title: $prompt")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$title: $prompt"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .setAutoCancel(true)
                    .setContentIntent(chatIntent(sessionId, title, worktree))
                    .build()
            notificationManager.notify(sessionId.hashCode() + 2, notification)
        }

        fun cancelSessionNotifications(sessionId: String) {
            notificationManager.cancel(sessionId.hashCode())
            notificationManager.cancel(sessionId.hashCode() + 1)
            notificationManager.cancel(sessionId.hashCode() + 2)
        }

        fun buildMonitorNotification(runningCount: Int): Notification {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    REQUEST_MONITOR,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val text =
                if (runningCount <= 1) {
                    "Listening for agent updates"
                } else {
                    "Monitoring $runningCount running tasks"
                }
            return NotificationCompat.Builder(context, CHANNEL_MONITOR)
                .setSmallIcon(smallIcon())
                .setContentTitle("OpenCode Mobile is monitoring tasks")
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(pendingIntent)
                .build()
        }

        companion object {
            const val CHANNEL_ALERTS = "opencode_alerts"
            const val CHANNEL_MONITOR = "opencode_monitor"
            const val EXTRA_SESSION_ID = "session_id"
            const val EXTRA_SESSION_TITLE = "session_title"
            const val EXTRA_WORKTREE = "session_worktree"
            const val MONITOR_NOTIFICATION_ID = 1001
            private const val REQUEST_MONITOR = 9001

            /**
             * Minimal notification with no resource lookups or PendingIntent work.
             * Used as a last resort so startForeground() can never be delayed or
             * fail due to builder I/O during cold start.
             */
            fun fallbackMonitorNotification(context: Context): Notification {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val manager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    try {
                        manager.createNotificationChannel(
                            NotificationChannel(
                                CHANNEL_MONITOR,
                                "Task Monitoring",
                                NotificationManager.IMPORTANCE_LOW,
                            ).apply { setShowBadge(false) },
                        )
                    } catch (_: Exception) {}
                }
                return NotificationCompat.Builder(context, CHANNEL_MONITOR)
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .setContentTitle("OpenCode Mobile is monitoring tasks")
                    .setContentText("Listening for agent updates")
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
            }
        }
    }
