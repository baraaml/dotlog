package com.example.dotlog.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dotlog.DotlogApplication
import com.example.dotlog.data.LocationRepository
import com.example.dotlog.data.Visit
import com.example.dotlog.data.VisitRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class MainState(
    val currentLocation: Location? = null,
    val currentPlaceName: String = "Searching...",
    val showHistoryOnMap: Boolean = false,
    val visits: List<Visit> = emptyList(),
    val zoomTarget: Location? = null
)

sealed interface MainAction {
    data object OnLogClick : MainAction
    data object OnToggleHistory : MainAction
    data class OnVisitClick(val latitude: Double, val longitude: Double) : MainAction
    data object OnZoomConsumed : MainAction
}

sealed interface MainEvent {
    data object VisitLogged : MainEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val locationRepository = LocationRepository(application)
    private val visitRepository = (application as DotlogApplication).repository
    private val poiRepository = (application as DotlogApplication).poiRepository

    private val _state = MutableStateFlow(MainState())

    val state: StateFlow<MainState> = combine(
        _state,
        visitRepository.allVisits
    ) { local, visits -> local.copy(visits = visits) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val initialLocation = withTimeoutOrNull(10_000) {
                locationRepository.getLocationUpdates(1000)
                    .filter { it.time > System.currentTimeMillis() - 30_000 && (it.accuracy ?: 999f) < 100f }
                    .first()
            } ?: locationRepository.requestSingleFreshLocation()
            _state.update { it.copy(currentLocation = initialLocation) }
            initialLocation?.let { resolvePoi(it.latitude, it.longitude) }
        }
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.OnLogClick -> logCurrentVisit()
            MainAction.OnToggleHistory -> _state.update { it.copy(showHistoryOnMap = !it.showHistoryOnMap) }
            is MainAction.OnVisitClick -> {
                val loc = Location("zoom").apply {
                    latitude = action.latitude
                    longitude = action.longitude
                }
                _state.update { it.copy(zoomTarget = loc, showHistoryOnMap = true) }
            }
            MainAction.OnZoomConsumed -> _state.update { it.copy(zoomTarget = null) }
        }
    }

    private fun resolvePoi(lat: Double, lon: Double) {
        viewModelScope.launch {
            _state.update { it.copy(currentPlaceName = "Resolving...") }
            val name = poiRepository.resolvePlaceName(lat, lon)
            _state.update { it.copy(currentPlaceName = name) }
        }
    }

    private fun logCurrentVisit() {
        val location = _state.value.currentLocation
        val placeName = _state.value.currentPlaceName
        if (location != null) {
            viewModelScope.launch {
                visitRepository.addVisit(
                    location.latitude, location.longitude, placeName, System.currentTimeMillis()
                )
                _events.send(MainEvent.VisitLogged)
            }
        }
    }
}
