package com.arcowebdesign.hikingwatch.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.arcowebdesign.hikingwatch.domain.model.DistanceUnit
import com.arcowebdesign.hikingwatch.domain.model.GpsSamplingRate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hiking_settings")

data class UserSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val gpsSamplingRate: GpsSamplingRate = GpsSamplingRate.HIGH_ACCURACY,
    val mapStyle: String = "standard",
    val userAge: Int = 30,       // for heart rate zones
    val userWeightKg: Float = 70f // for calorie calculation
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val GPS_SAMPLING = stringPreferencesKey("gps_sampling")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val USER_AGE = intPreferencesKey("user_age")
        val USER_WEIGHT = floatPreferencesKey("user_weight_kg")
    }

    val settings: Flow<UserSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            UserSettings(
                distanceUnit = DistanceUnit.valueOf(
                    prefs[Keys.DISTANCE_UNIT] ?: DistanceUnit.METRIC.name
                ),
                gpsSamplingRate = GpsSamplingRate.valueOf(
                    prefs[Keys.GPS_SAMPLING] ?: GpsSamplingRate.HIGH_ACCURACY.name
                ),
                mapStyle = prefs[Keys.MAP_STYLE] ?: "standard",
                userAge = prefs[Keys.USER_AGE] ?: 30,
                userWeightKg = prefs[Keys.USER_WEIGHT] ?: 70f
            )
        }

    suspend fun updateDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { it[Keys.DISTANCE_UNIT] = unit.name }
    }

    suspend fun updateGpsSampling(rate: GpsSamplingRate) {
        context.dataStore.edit { it[Keys.GPS_SAMPLING] = rate.name }
    }

    suspend fun updateMapStyle(style: String) {
        context.dataStore.edit { it[Keys.MAP_STYLE] = style }
    }

    suspend fun updateUserAge(age: Int) {
        context.dataStore.edit { it[Keys.USER_AGE] = age }
    }

    suspend fun updateUserWeight(weightKg: Float) {
        context.dataStore.edit { it[Keys.USER_WEIGHT] = weightKg }
    }
}
