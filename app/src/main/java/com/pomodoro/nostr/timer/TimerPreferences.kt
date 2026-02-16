package com.pomodoro.nostr.timer

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE)

    var selectedPresetIndex: Int
        get() = prefs.getInt("preset_index", 0)
        set(value) = prefs.edit().putInt("preset_index", value).apply()

    var customWorkMinutes: Int
        get() = prefs.getInt("custom_work", 25)
        set(value) = prefs.edit().putInt("custom_work", value).apply()

    var customShortBreakMinutes: Int
        get() = prefs.getInt("custom_short_break", 5)
        set(value) = prefs.edit().putInt("custom_short_break", value).apply()

    var customLongBreakMinutes: Int
        get() = prefs.getInt("custom_long_break", 15)
        set(value) = prefs.edit().putInt("custom_long_break", value).apply()

    var customSessionsBeforeLongBreak: Int
        get() = prefs.getInt("custom_sessions", 4)
        set(value) = prefs.edit().putInt("custom_sessions", value).apply()

    var isCustom: Boolean
        get() = prefs.getBoolean("is_custom", false)
        set(value) = prefs.edit().putBoolean("is_custom", value).apply()

    fun getActivePreset(): PomodoroPreset {
        return if (isCustom) {
            PomodoroPreset(
                name = "Custom",
                workMinutes = customWorkMinutes,
                shortBreakMinutes = customShortBreakMinutes,
                longBreakMinutes = customLongBreakMinutes,
                sessionsBeforeLongBreak = customSessionsBeforeLongBreak
            )
        } else {
            DEFAULT_PRESETS.getOrElse(selectedPresetIndex) { DEFAULT_PRESETS[0] }
        }
    }
}
