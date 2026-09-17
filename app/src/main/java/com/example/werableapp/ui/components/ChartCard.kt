package com.example.werableapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollState
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * UI component for displaying historical data on a line chart
 * Includes a time range selector and a summary of min, max, and average values
 */
@Composable
fun ChartCard(
    title: String,
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    iconTint: Color,
    unit: String,
    summary: ChartSummary,
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    yAxisStep: Int,
    modifier: Modifier = Modifier
) {
    // state controlling the visibility of the DropdownMenu
    // must be remembered to survive recomposition
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // icon selection logic, custom icon takes priority
                    if (iconRes != null) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    } else if (iconVector != null) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedRange.displayName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        TimeRange.entries.forEach { range ->
                            DropdownMenuItem(
                                text = { Text(range.displayName) },
                                onClick = {
                                    onRangeSelected(range)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // protection against IndexOutOfBounds errors: limits data points
            // to the number of available timestamps
            val pointsCount = minOf(summary.dataPoints.size, summary.timestamps.size)

            if (pointsCount > 0) {
                val dataPoints = summary.dataPoints.take(pointsCount)
                val timestamps = summary.timestamps.take(pointsCount)

                // ensure that the Y-axis step is never 0 to prevent division by zero errors
                val safeYAxisStep = yAxisStep.coerceAtLeast(1)

                // compresses data points into 5 static segments to prevent x-axis shifting
                val xSegments = 5f

                val chartEntries = remember(dataPoints) {
                    dataPoints.mapIndexed { index, value ->
                        val x = if (dataPoints.size <= 1) 0f else index.toFloat() / (dataPoints.size - 1).toFloat() * xSegments
                        entryOf(x, value)
                    }
                }

                val chartEntryModel = remember(chartEntries) { entryModelOf(chartEntries) }
                val actualMin = dataPoints.minOrNull() ?: summary.min.toFloat()
                val actualMax = dataPoints.maxOrNull() ?: summary.max.toFloat()

                var calculatedMinY = floor(actualMin / safeYAxisStep) * safeYAxisStep - safeYAxisStep
                if (calculatedMinY < 0f) calculatedMinY = 0f

                var calculatedMaxY = ceil(actualMax / safeYAxisStep) * safeYAxisStep + safeYAxisStep
                if (unit == "%" && calculatedMaxY > 100f) calculatedMaxY = 100f
                if (calculatedMaxY <= calculatedMinY) calculatedMaxY = calculatedMinY + safeYAxisStep

                // calculates the number of intervals between the floor and the ceiling
                val yStepsCount = ((calculatedMaxY - calculatedMinY) / safeYAxisStep).toInt().coerceAtLeast(1) + 1

                // dynamic date formatting, uses Date for days/weeks, and Time for hours
                val dateFormatter = remember(selectedRange) {
                    val pattern = when (selectedRange) {
                        TimeRange.DAY_7, TimeRange.DAY_14, TimeRange.DAY_30 -> "dd.MM"
                        else -> "HH:mm"
                    }
                    SimpleDateFormat(pattern, Locale.getDefault())
                }

                // smooth gradient under the chart curve
                val backgroundBrush = Brush.verticalGradient(
                    colors = listOf(iconTint.copy(alpha = 0.5f), iconTint.copy(alpha = 0f))
                )

                val lineSpec = LineChart.LineSpec(
                    lineColor = iconTint.toArgb(),
                    lineThicknessDp = 2f,
                    lineBackgroundShader = DynamicShaders.fromBrush(backgroundBrush)
                )

                val scrollState = rememberChartScrollState()

                Box(modifier = Modifier.height(160.dp).fillMaxWidth()) {
                    Chart(
                        chart = lineChart(
                            lines = listOf(lineSpec),
                            axisValuesOverrider = AxisValuesOverrider.fixed(
                                minX = 0f,
                                maxX = xSegments,
                                minY = calculatedMinY,
                                maxY = calculatedMaxY
                            )
                        ),
                        model = chartEntryModel,
                        chartScrollState = scrollState,
                        isZoomEnabled = false,
                        horizontalLayout = HorizontalLayout.FullWidth(),
                        getXStep = { 1f },
                        startAxis = rememberStartAxis(
                            valueFormatter = { value, _ -> value.toInt().toString() },
                            itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = yStepsCount),
                            label = textComponent(color = MaterialTheme.colorScheme.onSurfaceVariant, textSize = 10.sp),
                            axis = null,
                            tick = null,
                            guideline = lineComponent(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                        ),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = { value, _ ->
                                if (timestamps.isEmpty()) {
                                    ""
                                } else {
                                    // recovers the true time from compressed axes by calculating
                                    // the percentage progress and mapping it to the timestamps array
                                    val progress = (value / xSegments).coerceIn(0f, 1f)
                                    val index = (progress * timestamps.lastIndex).roundToInt().coerceIn(0, timestamps.lastIndex)
                                    dateFormatter.format(Date(timestamps[index]))
                                }
                            },
                            itemPlacer = AxisItemPlacer.Horizontal.default(
                                spacing = 1,
                                shiftExtremeTicks = false,
                                addExtremeLabelPadding = true
                            ),
                            label = textComponent(color = MaterialTheme.colorScheme.onSurfaceVariant, textSize = 10.sp),
                            axis = lineComponent(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), thickness = 1.dp),
                            tick = null,
                            guideline = null
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(modifier = Modifier.height(160.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = "No data to display", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "Min", value = summary.min.toString(), unit = unit, valueColor = iconTint)
                StatItem(label = "Max", value = summary.max.toString(), unit = unit, valueColor = iconTint)
                StatItem(label = "Average", value = summary.average.toString(), unit = unit, valueColor = iconTint)
            }
        }
    }
}

// helper component rendering a single statistic value
@Composable
fun StatItem(label: String, value: String, unit: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(2.dp))
            Text(text = unit, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}