package dev.opencode.mobile.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.opencode.mobile.domain.diagnostic.DiagnosticLog
import dev.opencode.mobile.domain.diagnostic.DiagnosticLogArea
import dev.opencode.mobile.domain.event.ForegroundEventStream
import dev.opencode.mobile.domain.event.OpencodeEvent
import dev.opencode.mobile.domain.event.WorldSignal
import dev.opencode.mobile.domain.event.WorldSignals
import dev.opencode.mobile.domain.live.SessionLiveStore
import dev.opencode.mobile.domain.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the foreground event stream for turn ends, failures, and
 * permission/question prompts, and posts system notifications when the
 * user is not looking at the session.
 *
 * The stream is foreground-first by design ([ProcessWorldSignals] stops it
 * ~5s after backgrounding). While any session runs, the monitor re-asserts
 * [ForegroundEventStream.start] after a background stop and keeps
 * [AgentMonitorService] alive so SSE keeps flowing; when nothing runs, the
 * stream stops and the service goes away.
 */
@Singleton
class AgentNotificationMonitor
    @Inject
    constructor(
        private val eventStream: ForegroundEventStream,
        private val worldSignals: WorldSignals,
        private val sessionLiveStore: SessionLiveStore,
        private val notificationHelper: AgentNotificationHelper,
        @ApplicationContext private val context: Context,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var collectJob: Job? = null
        private var signalsJob: Job? = null

        /** Sessions with an in-flight run, by id. */
        private val running = ConcurrentHashMap.newKeySet<String>()

        fun start() {
            if (collectJob?.isActive == true) return
            collectJob =
                scope.launch {
                    eventStream.events.collect { event -> onEvent(event) }
                }
            signalsJob =
                scope.launch {
                    worldSignals.signals.collect { signal -> onSignal(signal) }
                }
        }

        private fun onSignal(signal: WorldSignal) {
            when (signal) {
                // The stream stopped for backgrounding while runs are still
                // in flight: hold it open so completions still notify.
                WorldSignal.BackgroundStop -> {
                    if (running.isNotEmpty()) {
                        eventStream.start()
                        AgentMonitorService.start(context)
                    }
                }
                WorldSignal.Foreground -> {
                    // Re-assert the stream; the normal lifecycle already starts it.
                    if (running.isNotEmpty()) eventStream.start()
                }
                else -> Unit
            }
        }

        private suspend fun onEvent(event: OpencodeEvent) {
            val sessionId = event.sessionId?.takeIf { it.isNotBlank() } ?: return
            when (event.type) {
                "session.status" -> onStatus(sessionId, NotificationEventDecisions.statusType(event))
                "session.idle" -> finishAsCompleted(sessionId)
                "session.error" -> finishAsFailed(sessionId, event)
                "permission.asked", "question.asked" -> notifyAwaitingInput(sessionId, event)
                "permission.replied", "question.replied", "question.rejected" -> {
                    markRunning(sessionId)
                    notificationHelper.cancelSessionNotifications(sessionId)
                }
                else -> Unit
            }
        }

        private fun onStatus(
            sessionId: String,
            status: String?,
        ) {
            when (status) {
                "busy", "retry" -> {
                    markRunning(sessionId)
                    notificationHelper.cancelSessionNotifications(sessionId)
                }
                "idle" -> finishAsCompleted(sessionId)
                else -> Unit
            }
        }

        private suspend fun resolveSession(sessionId: String): Session? =
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                sessionLiveStore.observeKnownSession(sessionId).filterNotNull().first()
            }

        private fun markRunning(sessionId: String) {
            running += sessionId
            AgentMonitorService.start(context)
        }

        private fun finishAsCompleted(sessionId: String) {
            val wasRunning = running.remove(sessionId)
            if (running.isEmpty()) AgentMonitorService.stop(context)
            if (!wasRunning) return
            if (!AppVisibility.shouldNotify(sessionId)) return
            scope.launch {
                val session = resolveSession(sessionId)
                notificationHelper.showTaskCompleted(
                    sessionTitle = session?.title.orEmpty(),
                    sessionId = sessionId,
                    worktree = session?.directory.orEmpty(),
                )
                DiagnosticLog.info(DiagnosticLogArea.Sse, "notified task completed session=$sessionId")
            }
        }

        private fun finishAsFailed(
            sessionId: String,
            event: OpencodeEvent,
        ) {
            val wasRunning = running.remove(sessionId)
            if (running.isEmpty()) AgentMonitorService.stop(context)
            if (!wasRunning) return
            if (!AppVisibility.shouldNotify(sessionId)) return
            scope.launch {
                val session = resolveSession(sessionId)
                notificationHelper.showTaskError(
                    sessionTitle = session?.title.orEmpty(),
                    errorMessage = NotificationEventDecisions.errorText(event.rawProperties),
                    sessionId = sessionId,
                    worktree = session?.directory.orEmpty(),
                )
                DiagnosticLog.info(DiagnosticLogArea.Sse, "notified task failed session=$sessionId")
            }
        }

        private fun notifyAwaitingInput(
            sessionId: String,
            event: OpencodeEvent,
        ) {
            markRunning(sessionId)
            if (!AppVisibility.shouldNotify(sessionId)) return
            scope.launch {
                val session = resolveSession(sessionId)
                notificationHelper.showAwaitingInput(
                    sessionTitle = session?.title.orEmpty(),
                    prompt = NotificationEventDecisions.inputPrompt(event),
                    sessionId = sessionId,
                    worktree = session?.directory.orEmpty(),
                )
                DiagnosticLog.info(DiagnosticLogArea.Sse, "notified awaiting input session=$sessionId")
            }
        }

        companion object {
            private const val RESOLVE_TIMEOUT_MS = 2_000L
        }
    }

/**
 * Pure event parsing for notification copy. Kept out of the monitor so JVM
 * unit tests can cover the fallbacks without Android.
 */
internal object NotificationEventDecisions {
    fun statusType(event: OpencodeEvent): String? =
        (event.rawProperties?.get("status") as? JsonObject)
            ?.get("type")?.let { (it as? JsonPrimitive)?.contentOrNull }

    fun errorText(properties: JsonObject?): String {
        fun string(key: String) = (properties?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val nested = properties?.get("error") as? JsonObject
        fun nestedString(key: String) = (nested?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return nestedString("message") ?: string("message") ?: nestedString("type") ?: string("type") ?: "Session run failed"
    }

    fun inputPrompt(event: OpencodeEvent): String {
        val properties = event.rawProperties
        fun string(key: String) =
            (properties?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return when (event.type) {
            "permission.asked" -> string("title") ?: string("tool") ?: string("name") ?: "Permission needed"
            else -> string("question") ?: string("header") ?: string("title") ?: "Your input is needed"
        }
    }
}
