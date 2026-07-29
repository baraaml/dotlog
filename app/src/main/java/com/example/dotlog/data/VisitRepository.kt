package com.example.dotlog.data

import kotlinx.coroutines.flow.Flow

class VisitRepository(private val visitDao: VisitDao) {
    val allVisits: Flow<List<Visit>> = visitDao.getAllVisits()

    suspend fun addVisit(latitude: Double, longitude: Double, placeName: String, timestamp: Long) {
        val latestVisit = visitDao.getLatestVisit()

        if (latestVisit != null) {
            val distance = LocationUtils.calculateDistance(
                latestVisit.latitude, latestVisit.longitude,
                latitude, longitude
            )

            if (distance <= 100.0) {
                // Duplicate visit - update the existing one
                val updatedVisit = latestVisit.copy(
                    placeName = placeName,
                    timestamp = timestamp
                )
                visitDao.update(updatedVisit)
                return
            }
        }

        // New visit - insert
        val newVisit = Visit(
            latitude = latitude,
            longitude = longitude,
            placeName = placeName,
            timestamp = timestamp
        )
        visitDao.insert(newVisit)
    }
}
