package dev.opencode.mobile

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import dev.opencode.mobile.domain.diagnostic.DiagnosticLogBuffer
import dev.opencode.mobile.domain.diagnostic.MainThreadHangWatchdog
import dev.opencode.mobile.domain.featureflags.FeatureFlags
import dev.opencode.mobile.domain.preferences.AnalyticsConsentPreferences
import dev.opencode.mobile.domain.preferences.AppLockPreferences
import dev.opencode.mobile.domain.preferences.AppOnboardingPreferences
import dev.opencode.mobile.domain.preferences.DeveloperToolsPreferences
import dev.opencode.mobile.domain.preferences.HomeDisplayPreferences
import dev.opencode.mobile.domain.preferences.MarkdownHighlightPreferences
import dev.opencode.mobile.domain.preferences.PerformanceOverlayPreferences
import dev.opencode.mobile.domain.preferences.SessionChatDisplayPreferences
import dev.opencode.mobile.domain.preferences.SplitPanePreferences
import dev.opencode.mobile.domain.textmate.TextMateProcessPressureObserver
import dev.opencode.mobile.markdown.OpenCodeAndroidMarkdownAssets
import dev.opencode.mobile.notification.AgentNotificationHelper
import dev.opencode.mobile.notification.AgentNotificationMonitor
import dev.opencode.mobile.notification.AppVisibility
import dev.opencode.mobile.platform.FrameworkSdkLevel
import dev.opencode.mobile.theme.ThemeManager

@HiltAndroidApp
class OpenCodeMobileApplication : Application() {
    override fun attachBaseContext(base: Context) {
        FrameworkSdkLevel.alignToFramework()
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(visibilityCallbacks)
        // Creates the alert/monitor channels before any event can notify.
        AgentNotificationHelper(this)
        EntryPointAccessors
            .fromApplication(this, NotificationMonitorEntryPoint::class.java)
            .agentNotificationMonitor()
            .start()
        ThemeManager.initialize(this)
        HomeDisplayPreferences.initialize(this)
        MarkdownHighlightPreferences.initialize(this)
        AnalyticsConsentPreferences.initialize(this)
        AppLockPreferences.initialize(this)
        AppOnboardingPreferences.initialize(this)
        DeveloperToolsPreferences.initialize(this)
        PerformanceOverlayPreferences.initialize(this)
        SessionChatDisplayPreferences.initialize(this)
        SplitPanePreferences.initialize(this)
        FeatureFlags.initialize(this)
        OpenCodeAndroidMarkdownAssets.init(this)
        OpenCodeReleaseObservability.initialize(this)
        EntryPointAccessors
            .fromApplication(this, TextMatePressureEntryPoint::class.java)
            .textMateProcessPressureObserver()
            .start()
        val hangWatchdogEntry =
            EntryPointAccessors.fromApplication(this, HangWatchdogEntryPoint::class.java)
        hangWatchdogEntry.diagnosticLogBuffer()
        hangWatchdogEntry.mainThreadHangWatchdog().start()
    }

    private companion object {
        val visibilityCallbacks =
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) = AppVisibility.onActivityStarted()

                override fun onActivityStopped(activity: Activity) = AppVisibility.onActivityStopped()

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit
            }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationMonitorEntryPoint {
    fun agentNotificationMonitor(): AgentNotificationMonitor
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TextMatePressureEntryPoint {
    fun textMateProcessPressureObserver(): TextMateProcessPressureObserver
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HangWatchdogEntryPoint {
    fun diagnosticLogBuffer(): DiagnosticLogBuffer

    fun mainThreadHangWatchdog(): MainThreadHangWatchdog
}
