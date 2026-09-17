package com.example.werableapp.ui.components

/**
* Data structures for chart configuration and data representation
*/

enum class TimeRange(val displayName: String) {
    HOUR_1("Last 1h"),
    HOUR_2("Last 2h"),
    HOUR_5("Last 5h"),
    HOUR_10("Last 10h"),
    DAY_1("Today"),
    DAY_7("7 Days"),
    DAY_14("14 Days"),
    DAY_30("30 Days")
}

// holds the aggregated statistics and raw data points needed to draw a chart
data class ChartSummary(
    val min: Int = 0,
    val max: Int = 0,
    val average: Int = 0,
    val dataPoints: List<Float> = emptyList(),
    val timestamps: List<Long> = emptyList()
)

enum class ChartMetric {
    HEART_RATE, SPO2
}