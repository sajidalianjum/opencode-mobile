package dev.opencode.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.opencode.mobile.markdown.MarkdownDocument
import dev.opencode.mobile.markdown.OpenCodeMarkdownDetector
import dev.opencode.mobile.markdown.OpenCodeMarkdownParser
import dev.opencode.mobile.markdown.OpenCodeSemanticFallbackHighlighter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UI-layer markdown fallback so bubbles never show raw `**` / `` ` `` markers.
 *
 * The pipeline ([MessageContentPreparer]) can legitimately return null
 * (detector miss, streaming size gate, history plain-first, parse failure,
 * per-block cache miss). When that happens the bubble fell back to plain
 * [Text], which is what the agent-formatting bug looked like.
 *
 * This parses synchronously for small messages and off the main thread for
 * large ones, always with the fast semantic-fallback highlighter (no TextMate).
 * Returns null when the text is not markdown — callers keep their plain path.
 */
internal fun parseMarkdownFallbackOrNull(text: String): MarkdownDocument? {
    if (text.isBlank()) return null
    if (!OpenCodeMarkdownDetector.shouldRender(text)) return null
    return runCatching {
        OpenCodeMarkdownParser.parse(
            markdown = text,
            codeHighlighter = OpenCodeSemanticFallbackHighlighter,
        )
    }.getOrNull()
}

private const val FALLBACK_SYNC_PARSE_CHARACTER_THRESHOLD = 2_000

@Composable
internal fun rememberFallbackMarkdownDocument(text: String): MarkdownDocument? {
    if (text.isBlank()) return null
    if (text.length < FALLBACK_SYNC_PARSE_CHARACTER_THRESHOLD) {
        return remember(text) { parseMarkdownFallbackOrNull(text) }
    }
    val parsed by produceState<MarkdownDocument?>(initialValue = null, text) {
        value =
            withContext(Dispatchers.Default) {
                parseMarkdownFallbackOrNull(text)
            }
    }
    return parsed
}
