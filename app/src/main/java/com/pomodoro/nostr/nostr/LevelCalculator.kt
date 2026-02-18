package com.pomodoro.nostr.nostr

import android.content.Context
import android.content.SharedPreferences
import com.pomodoro.nostr.R
import com.pomodoro.nostr.timer.SessionHistory
import javax.inject.Inject
import javax.inject.Singleton

enum class PomodoroLevel(
    val displayName: String,
    val tag: String,
    val drawableRes: Int,
    val ordinalRank: Int
) {
    BEGINNER("Beginner", "beginner", R.drawable.ic_level_beginner, 0),
    PRACTITIONER("Practitioner", "practitioner", R.drawable.ic_level_practitioner, 1),
    MASTER("Master", "master", R.drawable.ic_level_master, 2);

    companion object {
        fun fromTag(tag: String): PomodoroLevel {
            return entries.find { it.tag == tag } ?: BEGINNER
        }

        fun fromAverage(avgSessionsPerDay: Double): PomodoroLevel {
            return when {
                avgSessionsPerDay >= 4.0 -> MASTER
                avgSessionsPerDay >= 2.0 -> PRACTITIONER
                else -> BEGINNER
            }
        }
    }
}

@Singleton
class LevelCalculator @Inject constructor(
    private val sessionHistory: SessionHistory
) {
    private var prefs: SharedPreferences? = null

    constructor(context: Context) : this(SessionHistory(context)) {
        prefs = context.getSharedPreferences("level_prefs", Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("level_prefs", Context.MODE_PRIVATE)
        }
    }

    fun calculateCurrentLevel(): PomodoroLevel {
        val avg = calculateSevenDayAverage()
        return PomodoroLevel.fromAverage(avg)
    }

    fun calculateSevenDayAverage(): Double {
        val dailyCounts = sessionHistory.getDailySessionCounts(1) // last 7 days
        if (dailyCounts.isEmpty()) return 0.0
        return dailyCounts.values.sum().toDouble() / dailyCounts.size
    }

    /**
     * Checks if the level has increased since last check.
     * Returns the new level if leveled up, null otherwise.
     * Updates the stored level on level-up.
     */
    fun checkLevelUp(): PomodoroLevel? {
        val currentLevel = calculateCurrentLevel()
        val storedTag = prefs?.getString("last_level", PomodoroLevel.BEGINNER.tag)
            ?: PomodoroLevel.BEGINNER.tag
        val storedLevel = PomodoroLevel.fromTag(storedTag)

        return if (currentLevel.ordinalRank > storedLevel.ordinalRank) {
            prefs?.edit()?.putString("last_level", currentLevel.tag)?.apply()
            currentLevel
        } else {
            // Update stored level even on same/lower to stay in sync
            if (currentLevel.tag != storedTag) {
                prefs?.edit()?.putString("last_level", currentLevel.tag)?.apply()
            }
            null
        }
    }
}
