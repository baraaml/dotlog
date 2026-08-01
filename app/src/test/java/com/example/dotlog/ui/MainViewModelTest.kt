package com.example.dotlog.ui

import android.app.Application
import android.content.SharedPreferences
import android.location.Location
import com.example.dotlog.data.GeocodingApi
import com.example.dotlog.data.GeocodingResult
import com.example.dotlog.data.LocationProvider
import com.example.dotlog.data.LocationRepository
import com.example.dotlog.data.OverpassApi
import com.example.dotlog.data.OverpassElement
import com.example.dotlog.data.OverpassResponse
import com.example.dotlog.data.PoiRepository
import com.example.dotlog.data.SearchRepository
import com.example.dotlog.data.Visit
import com.example.dotlog.data.VisitDao
import com.example.dotlog.data.VisitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var visitDao: FakeVisitDao
    private lateinit var poiApi: FakeOverpassApi
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        visitDao = FakeVisitDao()
        poiApi = FakeOverpassApi()
        val locationRepository = FakeLocationRepository()
        viewModel = MainViewModel(
            application = Application(),
            visitRepository = VisitRepository(visitDao),
            poiRepository = PoiRepository(poiApi),
            searchRepository = SearchRepository(FakeGeocodingApi()),
            locationRepository = locationRepository,
            prefs = FakeSharedPreferences()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `long press shows dialog immediately even if POI lookup is slow`() = runTest(dispatcher) {
        poiApi.delayMillis = 5_000

        viewModel.onAction(MainAction.OnMapLongClick(30.0, 31.0))

        val pending = viewModel._state.value.pendingLogLocation
        assertNotNull("Dialog must appear immediately, not wait for the network", pending)
        assertEquals("Resolving...", viewModel._state.value.pendingLogPlaceName)

        advanceUntilIdle()
        assertEquals("Cafe", viewModel._state.value.pendingLogPlaceName)
        assertNotNull(viewModel._state.value.pendingLogLocation)
    }

    @Test
    fun `long press updates name when POI resolution completes`() = runTest(dispatcher) {
        poiApi.resultName = "Central Park"

        viewModel.onAction(MainAction.OnMapLongClick(40.7, -73.9))

        assertEquals("Resolving...", viewModel._state.value.pendingLogPlaceName)
        advanceUntilIdle()
        assertEquals("Central Park", viewModel._state.value.pendingLogPlaceName)
        assertNotNull(viewModel._state.value.pendingLogLocation)
    }

    @Test
    fun `dismiss clears pending dialog state`() = runTest(dispatcher) {
        viewModel.onAction(MainAction.OnMapLongClick(1.0, 2.0))
        assertNotNull(viewModel._state.value.pendingLogLocation)

        viewModel.onAction(MainAction.OnDismissLogLocation)

        assertNull(viewModel._state.value.pendingLogLocation)
        assertEquals("", viewModel._state.value.pendingLogPlaceName)
    }

    @Test
    fun `resolution completing after dismiss does not resurrect dialog`() = runTest(dispatcher) {
        poiApi.delayMillis = 1_000

        viewModel.onAction(MainAction.OnMapLongClick(1.0, 2.0))
        viewModel.onAction(MainAction.OnDismissLogLocation)
        advanceUntilIdle()

        assertNull("A dismissed dialog must stay dismissed", viewModel._state.value.pendingLogLocation)
        assertEquals("", viewModel._state.value.pendingLogPlaceName)
    }

    @Test
    fun `confirm logs the visit and clears pending state`() = runTest(dispatcher) {
        viewModel.onAction(MainAction.OnMapLongClick(30.0, 31.0))
        advanceUntilIdle()

        viewModel.onAction(MainAction.OnConfirmLogLocation("My Spot", 12345L))
        advanceUntilIdle()

        assertEquals(1, visitDao.visits.size)
        assertEquals("My Spot", visitDao.visits[0].placeName)
        assertEquals(12345L, visitDao.visits[0].timestamp)
        assertNull(viewModel._state.value.pendingLogLocation)
        assertEquals("", viewModel._state.value.pendingLogPlaceName)
    }

    @Test
    fun `confirm with no pending location does nothing`() = runTest(dispatcher) {
        viewModel.onAction(MainAction.OnConfirmLogLocation("Ghost", 1L))
        advanceUntilIdle()

        assertEquals(0, visitDao.visits.size)
    }

    @Test
    fun `failed resolution falls back to Unnamed area`() = runTest(dispatcher) {
        poiApi.throwOnQuery = true

        viewModel.onAction(MainAction.OnMapLongClick(0.0, 0.0))
        advanceUntilIdle()

        assertEquals("Unnamed area", viewModel._state.value.pendingLogPlaceName)
        assertNotNull(viewModel._state.value.pendingLogLocation)
    }

    class FakeLocationRepository : LocationProvider {
        override fun getLocationUpdates(intervalMillis: Long): Flow<Location> =
            flow { awaitCancellation() }

        override suspend fun getCurrentLocation(): Location? = null
        override suspend fun requestSingleFreshLocation(): Location? = null
    }

    class FakeOverpassApi : OverpassApi {
        var delayMillis: Long = 0
        var resultName: String = "Cafe"
        var throwOnQuery: Boolean = false

        override suspend fun query(data: String): OverpassResponse {
            if (throwOnQuery) throw RuntimeException("overpass down")
            if (delayMillis > 0) delay(delayMillis)
            return OverpassResponse(
                listOf(OverpassElement("node", 1L, 0.0, 0.0, mapOf("name" to resultName)))
            )
        }
    }

    class FakeGeocodingApi : GeocodingApi {
        override suspend fun search(
            query: String,
            format: String,
            limit: Int
        ): List<GeocodingResult> = emptyList()
    }

    class FakeVisitDao : VisitDao {
        val visits = mutableListOf<Visit>()
        private val _visitsFlow = MutableStateFlow<List<Visit>>(emptyList())

        override fun getAllVisits(): Flow<List<Visit>> = _visitsFlow.asStateFlow()

        override suspend fun getLatestVisit(): Visit? = visits.lastOrNull()

        override suspend fun insert(visit: Visit) {
            visits.add(visit)
            _visitsFlow.value = visits.toList()
        }

        override suspend fun update(visit: Visit) {
            val index = visits.indexOfFirst { it.id == visit.id }
            if (index != -1) visits[index] = visit else visits[visits.size - 1] = visit
            _visitsFlow.value = visits.toList()
        }

        override suspend fun delete(visit: Visit) {
            visits.removeAll { it.id == visit.id }
            _visitsFlow.value = visits.toList()
        }

        override suspend fun insertAll(visits: List<Visit>) {
            this.visits.addAll(visits)
            _visitsFlow.value = this.visits.toList()
        }
    }

    class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()
        private val listeners =
            mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            map[key] as? String ?: defValue

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?
        ): MutableSet<String>? = @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            map[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            map[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            map[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            map[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener != null) listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener != null) listeners.remove(listener)
        }

        inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var cleared = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(
                key: String?,
                value: MutableSet<String>?
            ): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                cleared = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (cleared) map.clear()
                pending.forEach { (key, value) ->
                    if (value == null) map.remove(key) else map[key] = value
                }
                listeners.forEach {
                    it.onSharedPreferenceChanged(this@FakeSharedPreferences, "")
                }
            }
        }
    }
}
