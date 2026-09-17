package com.example.werableapp.data

import android.content.Context
import com.example.werableapp.ui.components.ChartMetric
import com.example.werableapp.ui.components.ChartSummary
import com.example.werableapp.ui.components.TimeRange
import kotlin.math.roundToInt

/**
 * Repository for fetching and processing chart data from the local database
 */
class ChartRepository(context: Context) {
    private val wearableDao = AppDatabase.getDatabase(context).wearableDao()
    suspend fun getChartSummary(metric: ChartMetric, timeRange: TimeRange): ChartSummary {
        val currentTime = System.currentTimeMillis()

        // calculate how far back in time we need to fetch data
        val timeOffsetMillis = when (timeRange) {
            TimeRange.HOUR_1 -> 1 * 60 * 60 * 1000L
            TimeRange.HOUR_2 -> 2 * 60 * 60 * 1000L
            TimeRange.HOUR_5 -> 5 * 60 * 60 * 1000L
            TimeRange.HOUR_10 -> 10 * 60 * 60 * 1000L
            TimeRange.DAY_1 -> 24 * 60 * 60 * 1000L
            TimeRange.DAY_7 -> 7 * 24 * 60 * 60 * 1000L
            TimeRange.DAY_14 -> 14 * 24 * 60 * 60 * 1000L
            TimeRange.DAY_30 -> 30 * 24 * 60 * 60 * 1000L
        }

        val startTime = currentTime - timeOffsetMillis

        // define aggregation intervals (buckets) to avoid rendering too many points on the chart
        val bucketMillis = when (timeRange) {
            TimeRange.HOUR_1 -> 30 * 1000L // 30 sec -> 120 points
            TimeRange.HOUR_2 -> 1 * 60 * 1000L // 1 min -> 120 points
            TimeRange.HOUR_5 -> 2 * 60 * 1000L + 30 * 1000L // 2.5 min -> 120 points
            TimeRange.HOUR_10 -> 5 * 60 * 1000L // 5 min -> 120 points
            TimeRange.DAY_1 -> 10 * 60 * 1000L // 10 min -> 144 points
            TimeRange.DAY_7 -> 2 * 60 * 60 * 1000L // 2 h -> 84 points
            TimeRange.DAY_14 -> 4 * 60 * 60 * 1000L // 4h -> 84 points
            TimeRange.DAY_30 -> 12 * 60 * 60 * 1000L // 12 h -> 60 points
        }

        val queryResult = if (metric == ChartMetric.HEART_RATE) {
            wearableDao.getHeartRateChartData(startTime, bucketMillis)
        } else {
            wearableDao.getSpo2ChartData(startTime, bucketMillis)
        }

        if (queryResult.isEmpty()) {
            return ChartSummary(min = 0, max = 0, average = 0, dataPoints = emptyList())
        }

        // extract values and calculate statistics for the summary card
        val dataPoints = queryResult.map { it.avgValue }
        val timestamps = queryResult.map { it.timeBucket }
        val min = queryResult.minOf { it.avgValue }.toInt()
        val max = queryResult.maxOf { it.avgValue }.toInt()
        val totalSamples = queryResult.sumOf { it.sampleCount }

        val avg = if (metric == ChartMetric.SPO2) {
            wearableDao.getAverageSpo2(startTime)?.roundToInt() ?: 0
        } else if (totalSamples > 0) {
            (queryResult.sumOf { it.avgValue.toDouble() * it.sampleCount } / totalSamples).roundToInt()
        } else {
            0
        }

        return ChartSummary(
            min = min,
            max = max,
            average = avg,
            dataPoints = dataPoints,
            timestamps = timestamps
        )
    }
}