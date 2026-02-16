package com.pomodoro.nostr.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodoro.nostr.ui.theme.NeonCyan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ActivityMatrix(
    activityData: Map<LocalDate, Int>,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    weeks: Int = 26
) {
    val today = LocalDate.now()

    // Build the grid: columns (weeks) x 7 rows (Mon=0 to Sun=6)
    // Most recent week is on the right
    val grid = remember(activityData, today) {
        buildGrid(today, activityData, weeks)
    }

    // Month labels along the top
    val monthLabels = remember(today) {
        buildMonthLabels(today, weeks)
    }

    Column(modifier = modifier) {
        // Month labels row
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp)
        ) {
            monthLabels.forEach { (label, span) ->
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        modifier = Modifier.width((span * 12).dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width((span * 12).dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Grid with day labels
        Row(modifier = Modifier.fillMaxWidth()) {
            // Day labels column
            Column(
                modifier = Modifier.width(24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val dayLabels = listOf("M", "", "W", "", "F", "", "")
                dayLabels.forEach { label ->
                    Box(
                        modifier = Modifier.size(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Matrix cells
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                grid.forEach { weekColumn ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        weekColumn.forEach { cell ->
                            val alpha = when {
                                cell == null -> 0f // future date or out of range
                                cell == 0 -> 0.06f
                                cell <= 2 -> 0.25f
                                cell <= 4 -> 0.5f
                                else -> 1.0f
                            }

                            Canvas(modifier = Modifier.size(10.dp)) {
                                drawRoundRect(
                                    color = if (cell != null) accentColor.copy(alpha = alpha) else Color.Transparent,
                                    cornerRadius = CornerRadius(2.dp.toPx()),
                                    size = Size(size.width, size.height)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds the grid as a list of week columns (oldest first).
 * Each column has 7 entries (Mon=0 to Sun=6).
 * Null means the date is in the future or outside the range.
 */
private fun buildGrid(
    today: LocalDate,
    data: Map<LocalDate, Int>,
    weeks: Int
): List<List<Int?>> {
    // Find the Monday of the week containing today
    val todayDow = today.dayOfWeek.value // 1=Mon, 7=Sun
    val endOfWeekMonday = today.minusDays(todayDow.toLong() - 1)

    val grid = mutableListOf<List<Int?>>()

    for (weekOffset in (weeks - 1) downTo 0) {
        val weekMonday = endOfWeekMonday.minusWeeks(weekOffset.toLong())
        val column = (0 until 7).map { dayIndex ->
            val date = weekMonday.plusDays(dayIndex.toLong())
            when {
                date.isAfter(today) -> null
                else -> data[date] ?: 0
            }
        }
        grid.add(column)
    }

    return grid
}

/**
 * Builds month labels with span counts for positioning above the grid.
 * Returns a list of (label or null, column span).
 */
private fun buildMonthLabels(today: LocalDate, weeks: Int): List<Pair<String?, Int>> {
    val todayDow = today.dayOfWeek.value
    val endOfWeekMonday = today.minusDays(todayDow.toLong() - 1)

    val labels = mutableListOf<Pair<String?, Int>>()
    var currentMonth: Int? = null
    var currentSpan = 0

    for (weekOffset in (weeks - 1) downTo 0) {
        val weekMonday = endOfWeekMonday.minusWeeks(weekOffset.toLong())
        val month = weekMonday.monthValue

        if (month != currentMonth) {
            if (currentSpan > 0) {
                labels.add(Pair(
                    if (currentMonth != null) {
                        LocalDate.of(today.year, currentMonth, 1)
                            .month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    } else null,
                    currentSpan
                ))
            }
            currentMonth = month
            currentSpan = 1
        } else {
            currentSpan++
        }
    }

    // Add last group
    if (currentSpan > 0 && currentMonth != null) {
        labels.add(Pair(
            LocalDate.of(today.year, currentMonth, 1)
                .month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            currentSpan
        ))
    }

    return labels
}
