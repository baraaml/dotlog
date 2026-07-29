package com.example.dotlog.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `test Haversine distance 100m`() {
        val distance = LocationUtils.calculateDistance(0.0, 0.0, 0.0009, 0.0)
        assertTrue("Distance should be around 100m", distance in 99.0..101.0)
    }

    @Test
    fun `test Haversine distance same point`() {
        val distance = LocationUtils.calculateDistance(30.0, 31.0, 30.0, 31.0)
        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `test Haversine distance antipodal`() {
        val distance = LocationUtils.calculateDistance(0.0, 0.0, 0.0, 180.0)
        assertTrue("Antipodal should be ~20M meters", distance > 20_000_000)
    }

    @Test
    fun `test addVisit inserts when no previous visits`() = runBlocking {
        repository.addVisit(0.0, 0.0, "Place A", 1000L)
        assertEquals(1, fakeDao.visits.size)
        assertEquals("Place A", fakeDao.visits[0].placeName)
    }

    @Test
    fun `test addVisit updates latest when within 100m`() = runBlocking {
        repository.addVisit(0.0, 0.0, "Place A", 1000L)
        repository.addVisit(0.0008, 0.0, "Place A Updated", 2000L)

        assertEquals(1, fakeDao.visits.size)
        assertEquals("Place A Updated", fakeDao.visits[0].placeName)
        assertEquals(2000L, fakeDao.visits[0].timestamp)
    }

    @Test
    fun `test addVisit inserts new when beyond 100m`() = runBlocking {
        repository.addVisit(0.0, 0.0, "Place A", 1000L)
        repository.addVisit(0.001, 0.0, "Place B", 2000L)

        assertEquals(2, fakeDao.visits.size)
        assertEquals("Place B", fakeDao.visits[1].placeName)
    }

    @Test
    fun `test deleteVisit removes from dao`() = runBlocking {
        val visit = Visit(latitude = 1.0, longitude = 2.0, placeName = "Test", timestamp = 100L)
        fakeDao.insert(visit)
        assertEquals(1, fakeDao.visits.size)
        repository.deleteVisit(visit)
        assertEquals(0, fakeDao.visits.size)
    }

    @Test
    fun `test updateVisit modifies place name`() = runBlocking {
        val visit = Visit(latitude = 1.0, longitude = 2.0, placeName = "Old", timestamp = 100L)
        fakeDao.insert(visit)
        repository.updateVisit(visit.copy(placeName = "New"))
        assertEquals("New", fakeDao.visits[0].placeName)
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
                visits[visits.size - 1] = visit
            }
            _visitsFlow.value = visits.toList()
        }

        override suspend fun delete(visit: Visit) {
            visits.removeAll { it.id == visit.id }
            _visitsFlow.value = visits.toList()
        }
    }
}
