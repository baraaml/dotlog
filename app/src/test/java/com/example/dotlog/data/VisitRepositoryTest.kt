package com.example.dotlog.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisitRepositoryTest {

    private lateinit var repository: VisitRepository
    private lateinit var fakeDao: FakeVisitDao

    @Before
    fun setUp() {
        fakeDao = FakeVisitDao()
        repository = VisitRepository(fakeDao)
    }

    @Test
    fun `test Haversine distance`() {
        // Distance between two points ~100m
        // Lat: 0.0, Lon: 0.0
        // Lat: 0.0009, Lon: 0.0 -> ~100.07m
        val distance = LocationUtils.calculateDistance(0.0, 0.0, 0.0009, 0.0)
        assertTrue("Distance should be around 100m", distance in 99.0..101.0)
    }

    @Test
    fun `test addVisit inserts new visit when no previous visits`() = runBlocking {
        repository.addVisit(0.0, 0.0, "Place A", 1000L)
        assertEquals(1, fakeDao.visits.size)
        assertEquals("Place A", fakeDao.visits[0].placeName)
    }

    @Test
    fun `test addVisit updates latest visit when within 100m`() = runBlocking {
        repository.addVisit(0.0, 0.0, "Place A", 1000L)
        
        // Within 100m (0.0008 degrees lat is ~89m)
        repository.addVisit(0.0008, 0.0, "Place A Updated", 2000L)
        
        assertEquals(1, fakeDao.visits.size)
        assertEquals("Place A Updated", fakeDao.visits[0].placeName)
        assertEquals(2000L, fakeDao.visits[0].timestamp)
    }

    @Test
    fun `test addVisit inserts new visit when beyond 100m`() = runBlocking {
        repository.addVisit(0.0, 0.0, "Place A", 1000L)
        
        // Beyond 100m (0.001 degrees lat is ~111m)
        repository.addVisit(0.001, 0.0, "Place B", 2000L)
        
        assertEquals(2, fakeDao.visits.size)
        assertEquals("Place B", fakeDao.visits[1].placeName)
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
            if (index != -1) {
                visits[index] = visit
            } else {
                // If ID is 0 (not yet assigned by Room), we might need to handle this differently in a real DAO
                // In our repo, we use the latestVisit which already has an ID if it came from the DB.
                // For this fake, we'll just replace the last one if it matches the latestVisit pattern
                visits[visits.size - 1] = visit
            }
            _visitsFlow.value = visits.toList()
        }
    }
}
