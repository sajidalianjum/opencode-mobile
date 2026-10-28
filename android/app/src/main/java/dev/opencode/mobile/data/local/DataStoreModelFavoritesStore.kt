package dev.opencode.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.opencode.mobile.domain.model.ModelFavoritesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Global device-level favorite models, stored as a set of
 * [dev.opencode.mobile.domain.model.ModelSelectionOption.id] (`"providerId:modelId"`) strings.
 */
class DataStoreModelFavoritesStore
    internal constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ModelFavoritesStore {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(context.modelFavoritesDataStore)

        override val favorites: Flow<Set<String>> =
            dataStore.data.map { prefs -> prefs[FavoriteIdsKey]?.toSet() ?: emptySet() }

        override suspend fun isFavorite(optionId: String): Boolean = optionId in favorites.first()

        override suspend fun setFavorite(
            optionId: String,
            favorite: Boolean,
        ) {
            dataStore.edit { prefs ->
                val current = prefs[FavoriteIdsKey]?.toMutableSet() ?: mutableSetOf()
                if (favorite) current.add(optionId) else current.remove(optionId)
                prefs[FavoriteIdsKey] = current
            }
        }

        override suspend fun toggleFavorite(optionId: String): Boolean {
            var next = false
            dataStore.edit { prefs ->
                val current = prefs[FavoriteIdsKey]?.toMutableSet() ?: mutableSetOf()
                next = optionId !in current
                if (next) current.add(optionId) else current.remove(optionId)
                prefs[FavoriteIdsKey] = current
            }
            return next
        }

        override suspend fun clearAll() {
            dataStore.edit { prefs -> prefs.remove(FavoriteIdsKey) }
        }

        private companion object {
            val FavoriteIdsKey = stringSetPreferencesKey("modelFavorites.v1.ids")
        }
    }
