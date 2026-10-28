package dev.opencode.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.modelFavoritesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "model_favorites")
