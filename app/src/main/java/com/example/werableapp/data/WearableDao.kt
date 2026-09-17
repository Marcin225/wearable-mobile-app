package com.example.werableapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Handles database operations for wearable measurements
 */


// represents a single aggregated data point on a chart
data class ChartDataPoint(
    val timeBucket: Long,
    val avgValue: Float,
    val sampleCount: Long
)

data class MetricStats(
    val minValue: Int,
    val maxValue: Int,
    val avgValue: Double
)

@Dao
interface WearableDao {
    // save a batch of wearable measurements
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(metrics: List<WearableData>)

    // remove measurements older than the given time
    @Query("DELETE FROM wearable_metrics WHERE timeStamp < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    // groups valid heart rate data into time buckets and calculates the average and sample count for each
    @Query("""
        SELECT 
            (timeStamp / :bucketMillis) * :bucketMillis AS timeBucket,
            AVG(heartRate) AS avgValue,
            COUNT(*) AS sampleCount
        FROM wearable_metrics
        WHERE timeStamp >= :startTime
          AND heartRate > 0
        GROUP BY timeBucket
        ORDER BY timeBucket ASC
    """)
    suspend fun getHeartRateChartData(startTime: Long, bucketMillis: Long): List<ChartDataPoint>

    // groups valid SpO2 data into time buckets and calculates the average and sample count for each
    @Query("""
        SELECT 
            (timeStamp / :bucketMillis) * :bucketMillis AS timeBucket,
            MAX(spo2) AS avgValue,
            COUNT(*) AS sampleCount
        FROM wearable_metrics
        WHERE timeStamp >= :startTime
          AND spo2 > 0
        GROUP BY timeBucket
        ORDER BY timeBucket ASC
    """)
    suspend fun getSpo2ChartData(startTime: Long, bucketMillis: Long): List<ChartDataPoint>

    @Query("""
        SELECT AVG(spo2)
        FROM wearable_metrics
        WHERE timeStamp >= :startTime
          AND spo2 > 0
    """)
    suspend fun getAverageSpo2(startTime: Long): Double?

    @Query("DELETE FROM wearable_metrics")
    suspend fun clearAllData()
}

