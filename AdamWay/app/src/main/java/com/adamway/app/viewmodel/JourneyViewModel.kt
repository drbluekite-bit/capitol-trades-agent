package com.adamway.app.viewmodel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamway.app.data.Address
import com.adamway.app.data.AddressRepository
import com.adamway.app.journey.JourneyManager
import com.adamway.app.journey.JourneyWindow
import com.adamway.app.location.AddressLocationResolver
import com.adamway.app.location.ResolvedStop
import com.adamway.app.maps.MapsLauncher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class JourneyUiState {
    object Idle : JourneyUiState()
    object Resolving : JourneyUiState()
    data class Active(
        val window: JourneyWindow,
        val totalStops: Int,
        val warnings: List<String>,
    ) : JourneyUiState()
    object Finished : JourneyUiState()
}

class JourneyViewModel(
    private val addressRepository: AddressRepository,
    private val locationResolver: AddressLocationResolver,
    private val journeyManager: JourneyManager = JourneyManager(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<JourneyUiState>(JourneyUiState.Idle)
    val uiState: StateFlow<JourneyUiState> = _uiState.asStateFlow()

    private var fullQueueSnapshot: List<Address> = emptyList()

    fun beginJourney(context: Context) {
        viewModelScope.launch {
            _uiState.value = JourneyUiState.Resolving
            // Take a one-off snapshot of the queue as it stands right now; the
            // journey then runs against this fixed ordering even if the queue
            // screen is edited later.
            fullQueueSnapshot = addressRepository.observeQueue().first()
            val window = journeyManager.beginJourney(fullQueueSnapshot)
            if (window == null) {
                _uiState.value = JourneyUiState.Idle
                return@launch
            }
            launchWindow(context, window)
        }
    }

    fun arrivedNextStop(context: Context) {
        val current = (_uiState.value as? JourneyUiState.Active)?.window ?: return
        viewModelScope.launch {
            addressRepository.markVisited(fullQueueSnapshot[current.startIndex].id)

            if (current.isFinalStop) {
                _uiState.value = JourneyUiState.Finished
                return@launch
            }

            _uiState.value = JourneyUiState.Resolving
            val result = journeyManager.advance(fullQueueSnapshot, current)
            if (result == null) {
                _uiState.value = JourneyUiState.Finished
                return@launch
            }
            if (result.addedNewStop) {
                launchWindow(context, result.window)
            } else {
                // Nothing new to send: Maps already has the rest of the route loaded.
                _uiState.value = JourneyUiState.Active(result.window, fullQueueSnapshot.size, emptyList())
            }
        }
    }

    fun openCurrentStopInWaze(context: Context) {
        val current = (_uiState.value as? JourneyUiState.Active)?.window ?: return
        viewModelScope.launch {
            val nextStop = fullQueueSnapshot[current.startIndex]
            when (val resolved = locationResolver.resolve(nextStop)) {
                is ResolvedStop.Coordinates -> MapsLauncher.openWazeCoordinates(context, resolved.latLng.lat, resolved.latLng.lng)
                is ResolvedStop.TextQuery -> MapsLauncher.openWaze(context, resolved.query)
                is ResolvedStop.Unresolved -> {
                    if (nextStop.what3words.isNotBlank()) {
                        MapsLauncher.openWhat3Words(context, nextStop.what3words)
                    } else {
                        Toast.makeText(context, "No location details for this stop", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun endJourney() {
        _uiState.value = JourneyUiState.Idle
    }

    private suspend fun launchWindow(context: Context, window: JourneyWindow) {
        val resolved = locationResolver.resolveAll(window.stops)
        val stopStrings = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        for (stop in resolved) {
            when (stop) {
                is ResolvedStop.Coordinates -> stopStrings += "${stop.latLng.lat},${stop.latLng.lng}"
                is ResolvedStop.TextQuery -> stopStrings += stop.query
                is ResolvedStop.Unresolved -> warnings += "${stop.address.displayLabel}: ${stop.reason}"
            }
        }
        _uiState.value = JourneyUiState.Active(window, fullQueueSnapshot.size, warnings)
        if (stopStrings.isNotEmpty()) {
            MapsLauncher.openGoogleMapsRoute(context, stopStrings)
        }
    }
}
