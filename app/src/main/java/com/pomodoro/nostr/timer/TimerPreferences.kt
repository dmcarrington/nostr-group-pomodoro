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

    var dndEnabled: Boolean
        get() = prefs.getBoolean("dnd_enabled", false)
        set(value) = prefs.edit().putBoolean("dnd_enabled", value).apply()

    var allowCalls: Boolean
        get() = prefs.getBoolean("dnd_allow_calls", true)
        set(value) = prefs.edit().putBoolean("dnd_allow_calls", value).apply()

    var allowMessages: Boolean
        get() = prefs.getBoolean("dnd_allow_messages", false)
        set(value) = prefs.edit().putBoolean("dnd_allow_messages", value).apply()

    var allowAlarms: Boolean
        get() = prefs.getBoolean("dnd_allow_alarms", true)
        set(value) = prefs.edit().putBoolean("dnd_allow_alarms", value).apply()

    var allowReminders: Boolean
        get() = prefs.getBoolean("dnd_allow_reminders", false)
        set(value) = prefs.edit().putBoolean("dnd_allow_reminders", value).apply()

    var allowEvents: Boolean
        get() = prefs.getBoolean("dnd_allow_events", false)
        set(value) = prefs.edit().putBoolean("dnd_allow_events", value).apply()

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
