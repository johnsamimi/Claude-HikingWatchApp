package com.arcowebdesign.hikingwatch.presentation

import android.content.Context
import android.content.Intent
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
    @ApplicationContext private val context: Context,
    private val hikingRepository: HikingRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val stats: StateFlow<HikingStats> = HikingTrackingService.serviceStats
    val path: StateFlow<List<LatLng>> = HikingTrackingService.servicePath
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())
    val batteryLevel = MutableStateFlow(100)

    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.Idle)
    val trackingState: StateFlow<TrackingState> = _trackingState

    // Bug 3e fix: nullable StateFlow, null = not ready yet
    private val _sessionSummary = MutableStateFlow<SessionSummary?>(null)
    val sessionSummary: StateFlow<SessionSummary?> = _sessionSummary

    private var currentSessionId = -1L

    init {
        // Watch for service session completion
        viewModelScope.launch {
            HikingTrackingService.sessionComplete.collect { complete ->
                if (complete && currentSessionId >= 0) {
                    // Bug 3a fix: build summary BEFORE transitioning state
                    val summary = hikingRepository.buildSessionSummary(currentSessionId)
                    _sessionSummary.value = summary
                    _trackingState.value = TrackingState.Completed(currentSessionId)
                }
            }
        }
        viewModelScope.launch {
            HikingTrackingService.activeSessionId.collect { id ->
                if (id >= 0) currentSessionId = id
            }
        }
    }

    fun startHike() {
        _sessionSummary.value = null
        HikingTrackingService.servicePath.value = emptyList()
        _trackingState.value = TrackingState.Active
        sendServiceAction(HikingTrackingService.ACTION_START)
    }

    fun pauseHike() {
        _trackingState.value = TrackingState.Paused
        sendServiceAction(HikingTrackingService.ACTION_PAUSE)
    }

    fun resumeHike() {
        _trackingState.value = TrackingState.Active
        sendServiceAction(HikingTrackingService.ACTION_RESUME)
    }

    fun stopHike() {
        sendServiceAction(HikingTrackingService.ACTION_STOP)
        // State transitions to Completed via sessionComplete flow above
    }

    fun resetToIdle() {
        _trackingState.value = TrackingState.Idle
        _sessionSummary.value = null
        currentSessionId = -1L
        HikingTrackingService.sessionComplete.value = false
        HikingTrackingService.servicePath.value = emptyList()
    }

    fun exportGpx(sessionId: Long) {
        viewModelScope.launch {
            val gpx = hikingRepository.exportGpx(sessionId)
            val file = java.io.File(context.filesDir, "session_$sessionId.gpx")
            file.writeText(gpx)
        }
    }

    private fun sendServiceAction(action: String) {
        context.startService(Intent(context, HikingTrackingService::class.java).apply {
            this.action = action
        })
    }

    // Bug 3d fix: always returns a formatted string, never empty
    fun formatDistance(meters: Double, unit: DistanceUnit): String =
        if (unit == DistanceUnit.IMPERIAL) "${"%.2f".format(meters / 1609.344)} mi"
        else "${"%.2f".format(meters / 1000.0)} km"

    fun formatElapsedTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun formatSpeed(mps: Float, unit: DistanceUnit): String =
        if (unit == DistanceUnit.IMPERIAL) "${"%.1f".format(mps * 2.237f)} mph"
        else "${"%.1f".format(mps * 3.6f)} km/h"
}
