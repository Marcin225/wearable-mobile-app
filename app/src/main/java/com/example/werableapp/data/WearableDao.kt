package com.example.werableapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Handles database operations for wearable measurements
 */
@Dao
interface WearableDao {

    // save a batch of wearable measurements
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(metrics: List<WearableData>)

    // remove measurements older than the given time
    @Query("DELETE FROM wearable_metrics WHERE timeStamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)
}