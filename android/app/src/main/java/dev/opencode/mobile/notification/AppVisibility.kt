package dev.opencode.mobile.notification

import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide visibility state used to suppress notifications for the
 * chat the user is actively looking at, and to gate foreground-service
 * starts (Android 12+ forbids FGS starts from the background).
 *
 * Defaults to background (false): a process can be created in the
 * background (service restart, broadcast), and assuming foreground would
 * queue a doomed start that the system may reject.
 * [OpenCodeMobileApplication] flips this via ActivityLifecycleCallbacks on
 * the first started activity.
 */
object AppVisibility {
    @Volatile
    var isForeground: Boolean = false

    @Volatile
    var visibleSessionId: String? = null

    private val startedActivities = AtomicInteger(0)

    /** Synchronous: called from Application.registerActivityLifecycleCallbacks. */
    fun onActivityStarted() {
        startedActivities.incrementAndGet()
        isForeground = true
    }

    /** Synchronous: called from Application.registerActivityLifecycleCallbacks. */
    fun onActivityStopped() {
        if (startedActivities.decrementAndGet() <= 0) {
            startedActivities.set(0)
            isForeground = false
        }
    }

    fun shouldNotify(sessionId: String): Boolean {
        // User is looking at this exact session — in-app UI already shows it.
        if (isForeground && visibleSessionId == sessionId) return false
        return true
    }
}
