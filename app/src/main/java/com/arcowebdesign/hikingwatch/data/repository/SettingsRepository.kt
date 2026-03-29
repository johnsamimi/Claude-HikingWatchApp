package com.arcowebdesign.hikingwatch.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.arcowebdesign.hikingwatch.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hiking_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
    private val GPS_RATE = longPreferencesKey("gps_sampling_rate")
    private val USER_AGE = intPreferencesKey("user_age")
    private val USER_WEIGHT = floatPreferencesKey("user_weight_kg")

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            distanceUnit = if (prefs[DISTANCE_UNIT] == "IMPERIAL") DistanceUnit.IMPERIAL else DistanceUnit.METRIC,
            gpsSamplingRate = prefs[GPS_RATE] ?: 5000L,
            userAge = prefs[USER_AGE] ?: 35,
            userWeightKg = prefs[USER_WEIGHT] ?: 70f
        )
    }

    suspend fun setDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { it[DISTANCE_UNIT] = unit.name }
    }
}
