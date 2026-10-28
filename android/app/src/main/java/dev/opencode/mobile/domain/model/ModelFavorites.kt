package dev.opencode.mobile.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Global (device-level, not per-connection) favorite models for the Session Chat model picker.
 * Favorites always appear at the top of the picker so frequently used models stay one tap away.
 * Entries are [ModelSelectionOption.id] (`"providerId:modelId"`) strings; ids of models that
 * no longer exist in the catalog are ignored when rendering.
 */
interface ModelFavoritesStore {
    /** Current favorite option ids; emits on every change. */
    val favorites: Flow<Set<String>>

    suspend fun isFavorite(optionId: String): Boolean

    suspend fun setFavorite(
        optionId: String,
        favorite: Boolean,
    )

    /** Flips the flag and returns the new state. */
    suspend fun toggleFavorite(optionId: String): Boolean

    suspend fun clearAll()
}

/** In-memory [ModelFavoritesStore]; the default for previews and unit tests. */
class InMemoryModelFavoritesStore(
    initial: Set<String> = emptySet(),
) : ModelFavoritesStore {
    private val state = MutableStateFlow(initial.toSet())

    override val favorites: Flow<Set<String>> = state.asStateFlow()

    override suspend fun isFavorite(optionId: String): Boolean = optionId in state.value

    override suspend fun setFavorite(
        optionId: String,
        favorite: Boolean,
    ) {
        state.update { current ->
            if (favorite) current + optionId else current - optionId
        }
    }

    override suspend fun toggleFavorite(optionId: String): Boolean {
        var next = false
        state.update { current ->
            next = optionId !in current
            if (next) current + optionId else current - optionId
        }
        return next
    }

    override suspend fun clearAll() {
        state.value = emptySet()
    }
}

/** Whether this catalog option is starred. */
fun ModelSelectionOption.isFavorite(favorites: Set<String>): Boolean = id in favorites

/**
 * Favorite options of this catalog, in catalog order, skipping ids that no longer exist.
 * Callers render each with [ModelSelectionOption.displayLabel] (`model · provider`) so the
 * same model name from different providers stays unambiguous.
 */
fun ModelSelectionCatalog.favoriteOptions(favorites: Set<String>): List<ModelSelectionOption> {
    if (favorites.isEmpty()) return emptyList()
    return options.filter { it.id in favorites }
}

/** Models of one provider with favorites first (catalog order otherwise; stable sort). */
fun List<ModelSelectionOption>.sortedWithFavoritesFirst(favorites: Set<String>): List<ModelSelectionOption> {
    if (favorites.isEmpty() || none { it.id in favorites }) return this
    return sortedBy { it.id !in favorites }
}

/** Providers with at least one favorite first, catalog order otherwise (stable sort). */
@JvmName("sortedProvidersWithFavoritesFirst")
fun List<ModelSelectionProvider>.sortedWithFavoritesFirst(favorites: Set<String>): List<ModelSelectionProvider> {
    if (favorites.isEmpty()) return this
    return sortedBy { provider -> provider.models.none { it.id in favorites } }
}
