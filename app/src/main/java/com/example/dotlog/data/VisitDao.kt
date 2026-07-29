package com.example.dotlog.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY timestamp DESC")
    fun getAllVisits(): Flow<List<Visit>>

    @Query("SELECT * FROM visits ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestVisit(): Visit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: Visit)

    @Update
    suspend fun update(visit: Visit)

    @Delete
    suspend fun delete(visit: Visit)
}
