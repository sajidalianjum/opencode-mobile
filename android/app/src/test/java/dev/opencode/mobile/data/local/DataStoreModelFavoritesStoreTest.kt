package dev.opencode.mobile.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.opencode.mobile.domain.model.ModelFavoritesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DataStoreModelFavoritesStoreTest {
    @Test
    fun toggleAndSet_roundTripThroughDataStore() =
        runTest {
            val store = store(backgroundScope)

            assertEquals(emptySet<String>(), store.favorites.first())

            assertTrue(store.toggleFavorite("openai:gpt-4"))
            assertTrue(store.isFavorite("openai:gpt-4"))

            store.setFavorite("anthropic:claude-sonnet", true)
            assertEquals(setOf("openai:gpt-4", "anthropic:claude-sonnet"), store.favorites.first())

            assertFalse(store.toggleFavorite("openai:gpt-4"))
            assertEquals(setOf("anthropic:claude-sonnet"), store.favorites.first())

            store.setFavorite("anthropic:claude-sonnet", false)
            assertEquals(emptySet<String>(), store.favorites.first())
        }

    @Test
    fun clearAll_removesEveryFavorite() =
        runTest {
            val store: ModelFavoritesStore = store(backgroundScope)
            store.setFavorite("openai:gpt-4", true)
            store.setFavorite("anthropic:claude-sonnet", true)

            store.clearAll()

            assertEquals(emptySet<String>(), store.favorites.first())
        }

    private fun store(scope: CoroutineScope) =
        DataStoreModelFavoritesStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { Files.createTempDirectory("model-favorites-store").resolve("preferences.preferences_pb").toFile() },
            ),
        )
}
