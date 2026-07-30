package com.example.dotlog.ui

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dotlog.DotlogApplication
import com.example.dotlog.data.LocationRepository
import com.example.dotlog.data.SearchResult
import com.example.dotlog.data.Visit
import com.example.dotlog.data.VisitRepository
import com.example.dotlog.data.parseCsvLine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class MainState(
    val currentLocation: Location? = null,
    val currentPlaceName: String = "Searching...",
    val showHistoryOnMap: Boolean = false,
    val visits: List<Visit> = emptyList(),
    val zoomTarget: Location? = null,
    val searchQuery: String = "",
    val pendingLogLocation: Location? = null,
    val pendingLogPlaceName: String = "",
    val isDarkMode: Boolean = false,
    val locationSearchQuery: String = "",
    val locationSearchResults: List<SearchResult> = emptyList(),
    val isLocationSearching: Boolean = false,
    val recentSearches: List<String> = emptyList()          // NEW
)

sealed interface MainAction {
    data object OnLogClick : MainAction
    data object OnToggleHistory : MainAction
    data class OnVisitClick(val latitude: Double, val longitude: Double) : MainAction
    data class OnEditVisit(val visit: Visit) : MainAction
    data class OnDeleteVisit(val visit: Visit) : MainAction
    data object OnRefreshLocation : MainAction
    data class OnSearchQueryChange(val query: String) : MainAction
    data object OnExportVisits : MainAction
    data class OnImportVisits(val csvContent: String) : MainAction
    data object OnZoomConsumed : MainAction
    data class OnMapLongClick(val latitude: Double, val longitude: Double) : MainAction
    data class OnConfirmLogLocation(val placeName: String, val timestamp: Long) : MainAction
    data object OnDismissLogLocation : MainAction
    data object OnToggleDarkMode : MainAction
    data class OnLocationSearchQueryChange(val query: String) : MainAction
    data object OnClearLocationSearch : MainAction
    data class OnLocationSearchResultClick(val result: SearchResult) : MainAction
    data object OnClearRecentSearches : MainAction            // NEW
    data class OnRecentSearchClick(val query: String) : MainAction // NEW

}

sealed interface MainEvent {
    data object VisitLogged : MainEvent
    data class ExportReady(val csvContent: String) : MainEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("dotlog_prefs", Context.MODE_PRIVATE)
    private val locationRepository = LocationRepository(application)
    private val visitRepository = (application as DotlogApplication).repository
    private val poiRepository = (application as DotlogApplication).poiRepository
    private val searchRepository = (application as DotlogApplication).searchRepository

    private val _state = MutableStateFlow(MainState())
    private val locationSearchQueryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val state: StateFlow<MainState> = combine(
        _state,
        visitRepository.allVisits
    ) { local, visits ->
        val filtered = if (local.searchQuery.isBlank()) visits
        else visits.filter { it.placeName.contains(local.searchQuery, ignoreCase = true) }
        local.copy(visits = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val savedDarkMode = prefs.getBoolean("dark_mode", false)
        val savedRecents = loadRecentSearches()
        _state.update { it.copy(isDarkMode = savedDarkMode, recentSearches = savedRecents) }

        viewModelScope.launch {
            locationSearchQueryFlow
                .debounce(500)
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collectLatest { query ->
                    _state.update { it.copy(isLocationSearching = true) }
                    val results = searchRepository.search(query)
                    _state.update { it.copy(locationSearchResults = results, isLocationSearching = false) }
                }
        }

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
            is MainAction.OnEditVisit -> {
                viewModelScope.launch { visitRepository.updateVisit(action.visit) }
            }
            is MainAction.OnDeleteVisit -> {
                viewModelScope.launch { visitRepository.deleteVisit(action.visit) }
            }
            MainAction.OnRefreshLocation -> refreshLocation()
            is MainAction.OnSearchQueryChange -> _state.update { it.copy(searchQuery = action.query) }
            MainAction.OnExportVisits -> exportVisits()
            is MainAction.OnImportVisits -> importVisits(action.csvContent)
            MainAction.OnZoomConsumed -> _state.update { it.copy(zoomTarget = null) }
            is MainAction.OnMapLongClick -> {
                val loc = Location("longPress").apply {
                    latitude = action.latitude
                    longitude = action.longitude
                }
                viewModelScope.launch {
                    val name = poiRepository.resolvePlaceName(action.latitude, action.longitude)
                    _state.update { it.copy(pendingLogLocation = loc, pendingLogPlaceName = name) }
                }
            }
            is MainAction.OnConfirmLogLocation -> {
                val location = _state.value.pendingLogLocation
                val placeName = action.placeName
                if (location != null) {
                    viewModelScope.launch {
                        visitRepository.addVisit(
                            location.latitude, location.longitude, placeName, action.timestamp
                        )
                        _events.send(MainEvent.VisitLogged)
                    }
                }
                _state.update { it.copy(pendingLogLocation = null, pendingLogPlaceName = "") }
            }
            MainAction.OnDismissLogLocation -> {
                _state.update { it.copy(pendingLogLocation = null, pendingLogPlaceName = "") }
            }
            MainAction.OnToggleDarkMode -> {
                val newMode = !_state.value.isDarkMode
                prefs.edit().putBoolean("dark_mode", newMode).apply()
                _state.update { it.copy(isDarkMode = newMode) }
            }
            is MainAction.OnLocationSearchQueryChange -> {
                _state.update { it.copy(locationSearchQuery = action.query) }
                if (action.query.length >= 2) {
                    locationSearchQueryFlow.tryEmit(action.query)
                } else {
                    _state.update { it.copy(locationSearchResults = emptyList(), isLocationSearching = false) }
                }
            }
            MainAction.OnClearLocationSearch -> {
                _state.update { it.copy(locationSearchQuery = "", locationSearchResults = emptyList(), isLocationSearching = false) }
            }
            is MainAction.OnLocationSearchResultClick -> {
                val loc = Location("search").apply {
                    latitude = action.result.latitude
                    longitude = action.result.longitude
                }
                var updatedRecents: List<String> = emptyList()
                _state.update {
                    updatedRecents = listOf(action.result.displayName) +
                            it.recentSearches.filter { v -> v != action.result.displayName }.take(4)
                    it.copy(
                        currentLocation = loc,
                        zoomTarget = loc,
                        currentPlaceName = action.result.displayName,
                        locationSearchQuery = "",
                        locationSearchResults = emptyList(),
                        recentSearches = updatedRecents
                    )
                }
                saveRecentSearches(updatedRecents)
            }

            MainAction.OnClearRecentSearches -> {
                saveRecentSearches(emptyList())
                _state.update { it.copy(recentSearches = emptyList()) }
            }

            is MainAction.OnRecentSearchClick -> {
                _state.update { it.copy(locationSearchQuery = action.query) }
                if (action.query.length >= 2) {
                    locationSearchQueryFlow.tryEmit(action.query)
                }
            }
        }
    }


    private fun loadRecentSearches(): List<String> {
        return prefs.getString("recent_searches", "")
            ?.split("|||")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun saveRecentSearches(searches: List<String>) {
        prefs.edit().putString("recent_searches", searches.joinToString("|||")).apply()
    }
    private fun resolvePoi(lat: Double, lon: Double) {
        viewModelScope.launch {
            _state.update { it.copy(currentPlaceName = "Resolving...") }
            val name = poiRepository.resolvePlaceName(lat, lon)
            _state.update { it.copy(currentPlaceName = name) }
        }
    }

    private fun refreshLocation() {
        viewModelScope.launch {
            _state.update { it.copy(currentPlaceName = "Refreshing...") }
            val location = locationRepository.requestSingleFreshLocation()
            _state.update { it.copy(currentLocation = location, zoomTarget = location) }
            location?.let { resolvePoi(it.latitude, it.longitude) }
        }
    }

    private fun exportVisits() {
        viewModelScope.launch {
            val visits = visitRepository.allVisits.first()
            val header = "latitude,longitude,placeName,timestamp"
            val rows = visits.joinToString("\n") {
                val name = if (it.placeName.contains(',') || it.placeName.contains('"') || it.placeName.contains('\n')) {
                    "\"${it.placeName.replace("\"", "\"\"")}\""
                } else it.placeName
                "${it.latitude},${it.longitude},$name,${it.timestamp}"
            }
            _events.send(MainEvent.ExportReady("$header\n$rows"))
        }
    }

    private fun importVisits(csvContent: String) {
        viewModelScope.launch {
            val lines = csvContent.lines().filter { it.isNotBlank() }
            val visits = mutableListOf<Visit>()
            for ((i, line) in lines.withIndex()) {
                if (i == 0 && line.startsWith("latitude,")) continue
                val parts = parseCsvLine(line)
                if (parts.size < 4) continue
                val lat = parts[0].toDoubleOrNull() ?: continue
                val lon = parts[1].toDoubleOrNull() ?: continue
                val name = parts[2]
                val ts = parts[3].toLongOrNull() ?: continue
                visits.add(Visit(latitude = lat, longitude = lon, placeName = name, timestamp = ts))
            }
            if (visits.isNotEmpty()) {
                visitRepository.addVisits(visits)
            }
        }
    }

    private fun logCurrentVisit() {
        val location = _state.value.currentLocation
        val placeName = _state.value.currentPlaceName
        if (location != null) {
            val now = System.currentTimeMillis()
            viewModelScope.launch {
                visitRepository.addVisit(
                    location.latitude, location.longitude, placeName, now
                )
                _events.send(MainEvent.VisitLogged)
            }
        }
    }
}
