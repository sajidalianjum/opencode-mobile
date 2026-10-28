package dev.opencode.mobile.domain.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFavoritesTest {
    private val openRouterSonnet =
        ModelSelectionOption(
            providerId = "openrouter",
            providerName = "OpenRouter",
            modelId = "claude-sonnet",
            modelName = "Claude Sonnet",
        )
    private val openRouterOss =
        ModelSelectionOption(
            providerId = "openrouter",
            providerName = "OpenRouter",
            modelId = "gpt-oss",
            modelName = "GPT OSS",
        )
    private val anthropicOpus =
        ModelSelectionOption(
            providerId = "anthropic",
            providerName = "Anthropic",
            modelId = "claude-opus",
            modelName = "Claude Opus",
        )
    private val catalog =
        ModelSelectionCatalog(
            options = listOf(openRouterSonnet, anthropicOpus, openRouterOss),
            serverDefault = null,
        )

    @Test
    fun inMemoryStore_toggleFlipsAndEmits() =
        runTest {
            val store: ModelFavoritesStore = InMemoryModelFavoritesStore()

            assertTrue(store.toggleFavorite("openai:gpt-4"))
            assertTrue(store.isFavorite("openai:gpt-4"))
            assertFalse(store.toggleFavorite("openai:gpt-4"))
            assertEquals(emptySet<String>(), store.favorites.first())
        }

    @Test
    fun favoriteOptions_returnsCatalogOrderAndSkipsUnknownIds() {
        val favorites = catalog.favoriteOptions(setOf("anthropic:claude-opus", "missing:model", "openrouter:claude-sonnet"))

        assertEquals(listOf(openRouterSonnet, anthropicOpus), favorites)
    }

    @Test
    fun favoriteOptions_emptyWhenNoFavorites() {
        assertEquals(emptyList<ModelSelectionOption>(), catalog.favoriteOptions(emptySet()))
    }

    @Test
    fun modelsSortedWithFavoritesFirst_keepsCatalogOrderOtherwise() {
        val sorted = listOf(openRouterSonnet, anthropicOpus, openRouterOss).sortedWithFavoritesFirst(setOf("anthropic:claude-opus"))

        assertEquals(listOf("claude-opus", "claude-sonnet", "gpt-oss"), sorted.map { it.modelId })
    }

    @Test
    fun providersSortedWithFavoritesFirst_bubblesProvidersWithFavorites() {
        val sorted = catalog.providers.sortedWithFavoritesFirst(setOf("anthropic:claude-opus"))

        assertEquals(listOf("anthropic", "openrouter"), sorted.map { it.id })
    }
}
