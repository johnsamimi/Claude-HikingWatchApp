package com.arcowebdesign.hikingwatch.presentation

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcowebdesign.hikingwatch.data.repository.HikingRepository
import com.arcowebdesign.hikingwatch.data.repository.SettingsRepository
import com.arcowebdesign.hikingwatch.data.service.HikingTrackingService
import com.arcowebdesign.hikingwatch.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TrackingState {
    object Idle : TrackingState()
    object Active : TrackingState()
    object Paused : TrackingState()
    data class Completed(val sessionId: Long) : TrackingState()
}

@HiltViewModel
class HikingViewModel @Inject constructor(
    private val hikingRepository: HikingRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val stats: StateFlow<HikingStats> = HikingTrackingService.serviceStats
    val path: StateFlow<List<LatLng>> = HikingTrackingService.servicePath
    val settings = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.arcowebdesign.hikingwatch.data.repository.UserSettings()
    )

    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.Idle)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    private val _sessionSummary = MutableStateFlow<SessionSummary?>(null)
    val sessionSummary: StateFlow<SessionSummary?> = _sessionSummary.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    init {
        updateBatteryLevel()
    }

    fun startHike() {
        sendServiceAction(HikingTrackingService.ACTION_START)
        _trackingState.value = TrackingState.Active
    }

    fun pauseHike() {
        sendServiceAction(HikingTrackingService.ACTION_PAUSE)
        _trackingState.value = TrackingState.Paused
    }

    fun resumeHike() {
        sendServiceAction(HikingTrackingService.ACTION_RESUME)
        _trackingState.value = TrackingState.Active
    }

    fun stopHike() {
        viewModelScope.launch {
            val sessionId = HikingTrackingService.activeSessionId.value
            sendServiceAction(HikingTrackingService.ACTION_STOP)
            if (sessionId > 0) {
                val summary = hikingRepository.buildSessionSummary(sessionId)
                _sessionSummary.value = summary
                _trackingState.value = TrackingState.Completed(sessionId)
            } else {
                _trackingState.value = TrackingState.Idle
            }
        }
    }

    fun exportGpx(sessionId: Long) {
        viewModelScope.launch {
            hikingRepository.exportToGpx(sessionId)
        }
    }

    fun resetToIdle() {
        _trackingState.value = TrackingState.Idle
        _sessionSummary.value = null
    }

    fun formatDistance(meters: Double, unit: DistanceUnit): String {
        return if (unit == DistanceUnit.METRIC) {
            if (meters < 1000) "${meters.toInt()}m"
            else "${"%.2f".format(meters / 1000)}km"
        } else {
            val miles = meters / 1609.344
            if (miles < 0.1) "${(meters * 3.28084).toInt()}ft"
            else "${"%.2f".format(miles)}mi"
        }
    }

    fun formatSpeed(mps: Float, unit: DistanceUnit): String {
        return if (unit == DistanceUnit.METRIC) {
            "${"%.1f".format(mps * 3.6)} km/h"
        } else {
            "${"%.1f".format(mps * 2.237)} mph"
        }
    }

    fun formatElapsedTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    private fun sendServiceAction(action: String) {
        Intent(context, HikingTrackingService::class.java).also {
            it.action = action
            context.startForegroundService(it)
        }
    }

    private fun updateBatteryLevel() {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        _batteryLevel.value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
