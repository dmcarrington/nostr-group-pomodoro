package com.pomodoro.nostr.timer

data class PomodoroPreset(
    val name: String,
    val workMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val sessionsBeforeLongBreak: Int = 4
)

val DEFAULT_PRESETS = listOf(
    PomodoroPreset("Classic", 25, 5, 15),
    PomodoroPreset("Long Focus", 50, 10, 30),
    PomodoroPreset("Short Sprint", 15, 3, 10),
)

enum class TimerPhase {
    WORK, SHORT_BREAK, LONG_BREAK, IDLE
}

data class TimerState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val remainingMillis: Long = 0L,
    val totalMillis: Long = 0L,
    val isRunning: Boolean = false,
    val completedSessions: Int = 0,
    val currentPreset: PomodoroPreset = DEFAULT_PRESETS[0]
)
