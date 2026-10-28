package dev.opencode.mobile.notification

import dev.opencode.mobile.domain.event.OpencodeEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEventDecisionsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun event(
        type: String,
        properties: String = "{}",
    ) = OpencodeEvent(
        type = type,
        sessionId = "ses_1",
        rawProperties = json.parseToJsonElement(properties) as JsonObject,
    )

    @Test
    fun statusType_readsProjectedStatus() {
        assertEquals(
            "busy",
            NotificationEventDecisions.statusType(event("session.status", """{"status":{"type":"busy"}}""")),
        )
        assertEquals(
            "idle",
            NotificationEventDecisions.statusType(event("session.status", """{"status":{"type":"idle"}}""")),
        )
        assertEquals(
            "retry",
            NotificationEventDecisions.statusType(event("session.status", """{"status":{"type":"retry"}}""")),
        )
        assertNull(NotificationEventDecisions.statusType(event("session.status")))
        assertNull(NotificationEventDecisions.statusType(event("session.idle")))
    }

    @Test
    fun errorText_prefersNestedMessage() {
        assertEquals(
            "boom",
            NotificationEventDecisions.errorText(
                json.parseToJsonElement(
                    """{"error":{"message":"boom","type":"provider.unknown"}}""",
                ) as kotlinx.serialization.json.JsonObject,
            ),
        )
        assertEquals(
            "plain",
            NotificationEventDecisions.errorText(
                json.parseToJsonElement("""{"message":"plain"}""") as JsonObject,
            ),
        )
        assertEquals(
            "Session run failed",
            NotificationEventDecisions.errorText(null),
        )
    }

    @Test
    fun inputPrompt_fallsBackPerType() {
        assertEquals(
            "Allow rm?",
            NotificationEventDecisions.inputPrompt(event("permission.asked", """{"title":"Allow rm?"}""")),
        )
        assertEquals(
            "Permission needed",
            NotificationEventDecisions.inputPrompt(event("permission.asked")),
        )
        assertEquals(
            "Pick one",
            NotificationEventDecisions.inputPrompt(event("question.asked", """{"question":"Pick one"}""")),
        )
        assertEquals(
            "Your input is needed",
            NotificationEventDecisions.inputPrompt(event("question.asked")),
        )
    }

    @Test
    fun visibility_suppressesOnlyTheOpenSession() {
        val previousForeground = AppVisibility.isForeground
        val previousVisible = AppVisibility.visibleSessionId
        try {
            AppVisibility.isForeground = true
            AppVisibility.visibleSessionId = "ses_1"
            assertFalse(AppVisibility.shouldNotify("ses_1"))
            assertTrue(AppVisibility.shouldNotify("ses_2"))
            AppVisibility.isForeground = false
            assertTrue(AppVisibility.shouldNotify("ses_1"))
        } finally {
            AppVisibility.isForeground = previousForeground
            AppVisibility.visibleSessionId = previousVisible
        }
    }
}
