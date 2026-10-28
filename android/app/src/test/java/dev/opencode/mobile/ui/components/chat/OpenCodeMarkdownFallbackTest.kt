package dev.opencode.mobile.ui.components

import dev.opencode.mobile.markdown.MarkdownBlock
import dev.opencode.mobile.markdown.MarkdownInline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeMarkdownFallbackTest {
    @Test
    fun fallbackRendersAgentBoldAndCodeSample() {
        val text =
            """
            **Fork:** `opencode-mobile` → `/tmp/opencode-mobile-fork`, branch `feature/android-notifications`.

            **Feature 1 — Android notifications (new code):**
            - `notification/AppVisibility.kt` — foreground + visible-session tracking
            """.trimIndent()
        val document = parseMarkdownFallbackOrNull(text)
        assertNotNull(document)
        val paragraph = document!!.blocks.filterIsInstance<MarkdownBlock.Paragraph>().firstOrNull()
        assertNotNull(paragraph)
        assertTrue(paragraph!!.inlines.any { it is MarkdownInline.Strong })
        assertTrue(paragraph.inlines.any { it is MarkdownInline.Code })
        assertTrue(document.blocks.any { it is MarkdownBlock.BulletList })
    }

    @Test
    fun fallbackReturnsNullForPlainText() {
        assertNull(parseMarkdownFallbackOrNull("Plain text stays native."))
        assertNull(parseMarkdownFallbackOrNull("   "))
    }

    @Test
    fun alertQuoteIsDetectedAndStripped() {
        val inlines =
            listOf(
                MarkdownInline.Text("[!NOTE] Read this first"),
            )
        val alert = parseAlertContent(inlines)
        assertNotNull(alert)
        assertEquals(MarkdownAlertKind.Note, alert!!.kind)
        assertTrue(alert.inlines.isNotEmpty())
    }

    @Test
    fun plainQuoteIsNotAnAlert() {
        val inlines = listOf(MarkdownInline.Text("Just a quote"))
        assertNull(parseAlertContent(inlines))
    }

    @Test
    fun taskItemsParseCheckedStates() {
        val unchecked = parseTaskItem(listOf(MarkdownInline.Text("[ ] write tests")))
        assertEquals(false, unchecked.checked)
        assertEquals("write tests", (unchecked.inlines.first() as MarkdownInline.Text).text)

        val checked = parseTaskItem(listOf(MarkdownInline.Text("[x] done")))
        assertEquals(true, checked.checked)

        val plain = parseTaskItem(listOf(MarkdownInline.Text("regular item")))
        assertNull(plain.checked)
    }
}
