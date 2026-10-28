package dev.opencode.mobile.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A tap on a session notification lands on its chat. */
data class PendingSessionOpen(
    val sessionId: String,
    val title: String,
    val worktree: String,
)

/**
 * Foreground entry point for notification taps. [MainActivity] records the
 * open request; [MainNavigation] consumes it and pushes the SessionChat route.
 */
@Singleton
class NotificationDeepLink
    @Inject
    constructor() {
        private val _pending = MutableStateFlow<PendingSessionOpen?>(null)
        val pending: StateFlow<PendingSessionOpen?> = _pending.asStateFlow()

        fun open(
            sessionId: String,
            title: String,
            worktree: String,
        ) {
            _pending.value = PendingSessionOpen(sessionId = sessionId, title = title, worktree = worktree)
        }

        fun consume(): PendingSessionOpen? {
            synchronized(this) {
                val pending = _pending.value
                _pending.value = null
                return pending
            }
        }
    }
