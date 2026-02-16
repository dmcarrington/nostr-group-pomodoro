package com.pomodoro.nostr.timer

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionHistory @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("session_history", Context.MODE_PRIVATE)

    private val zone = ZoneId.systemDefault()

    fun recordSession() {
        val timestamps = getSessionTimestamps().toMutableList()
        timestamps.add(System.currentTimeMillis())
        saveTimestamps(timestamps)
    }

    fun getSessionTimestamps(): List<Long> {
        val json = prefs.getString("timestamps", "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getLong(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getTodayCount(): Int {
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        return getSessionTimestamps().count { it >= startOfDay }
    }

    fun getWeekCount(): Int {
        val today = LocalDate.now()
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return getSessionTimestamps().count { it >= startOfWeek }
    }

    fun getMonthCount(): Int {
        val startOfMonth = LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return getSessionTimestamps().count { it >= startOfMonth }
    }

    /**
     * Returns a map of date -> session count for the last N weeks.
     * Covers from (today - weeks*7 days) through today.
     */
    fun getDailySessionCounts(weeks: Int): Map<LocalDate, Int> {
        val today = LocalDate.now()
        val startDate = today.minusWeeks(weeks.toLong()).plusDays(1)
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()

        val counts = mutableMapOf<LocalDate, Int>()
        // Initialize all dates to 0
        var date = startDate
        while (!date.isAfter(today)) {
            counts[date] = 0
            date = date.plusDays(1)
        }

        // Count sessions per day
        getSessionTimestamps()
            .filter { it >= startMillis }
            .forEach { timestamp ->
                val sessionDate = Instant.ofEpochMilli(timestamp)
                    .atZone(zone)
                    .toLocalDate()
                if (counts.containsKey(sessionDate)) {
                    counts[sessionDate] = counts[sessionDate]!! + 1
                }
            }

        return counts
    }

    private fun saveTimestamps(timestamps: List<Long>) {
        val array = JSONArray()
        // Only keep last 365 days to prevent unbounded growth
        val cutoff = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        timestamps.filter { it >= cutoff }.forEach { array.put(it) }
        prefs.edit().putString("timestamps", array.toString()).apply()
    }
}
