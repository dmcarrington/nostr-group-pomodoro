package com.pomodoro.nostr.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.pomodoro.nostr.nostr.KeyManager
import com.pomodoro.nostr.nostr.LevelCalculator
import com.pomodoro.nostr.nostr.NostrClient
import com.pomodoro.nostr.nostr.PomodoroLevel
import com.pomodoro.nostr.timer.DEFAULT_PRESETS
import com.pomodoro.nostr.timer.PomodoroPreset
import com.pomodoro.nostr.timer.SessionHistory
import com.pomodoro.nostr.timer.TimerPreferences
import com.pomodoro.nostr.timer.TimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
    private val nostrClient: NostrClient,
    private val timerPreferences: TimerPreferences,
    private val sessionHistory: SessionHistory,
    private val levelCalculator: LevelCalculator
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val presets: List<PomodoroPreset> = DEFAULT_PRESETS

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val authMethod = when {
            keyManager.isAmberConnected() -> "Amber"
            keyManager.hasLocalKeys() -> "Local Key"
            else -> "Not authenticated"
        }

        _uiState.value = SettingsUiState(
            npub = keyManager.getNpub(),
            authMethod = authMethod,
            selectedPresetIndex = timerPreferences.selectedPresetIndex,
            isCustom = timerPreferences.isCustom,
            customWorkMinutes = timerPreferences.customWorkMinutes,
            customShortBreakMinutes = timerPreferences.customShortBreakMinutes,
            customLongBreakMinutes = timerPreferences.customLongBreakMinutes,
            customSessionsBeforeLongBreak = timerPreferences.customSessionsBeforeLongBreak,
            todayCount = sessionHistory.getTodayCount(),
            weekCount = sessionHistory.getWeekCount(),
            monthCount = sessionHistory.getMonthCount(),
            activityData = sessionHistory.getDailySessionCounts(26),
            level = levelCalculator.calculateCurrentLevel(),
            sevenDayAverage = levelCalculator.calculateSevenDayAverage(),
            dndEnabled = timerPreferences.dndEnabled,
            allowCalls = timerPreferences.allowCalls,
            allowMessages = timerPreferences.allowMessages,
            allowAlarms = timerPreferences.allowAlarms,
            allowReminders = timerPreferences.allowReminders,
            allowEvents = timerPreferences.allowEvents
        )
    }

    fun refreshStats() {
        _uiState.value = _uiState.value.copy(
            todayCount = sessionHistory.getTodayCount(),
            weekCount = sessionHistory.getWeekCount(),
            monthCount = sessionHistory.getMonthCount(),
            activityData = sessionHistory.getDailySessionCounts(26),
            level = levelCalculator.calculateCurrentLevel(),
            sevenDayAverage = levelCalculator.calculateSevenDayAverage()
        )
    }

    fun selectPreset(index: Int) {
        timerPreferences.selectedPresetIndex = index
        timerPreferences.isCustom = false
        _uiState.value = _uiState.value.copy(
            selectedPresetIndex = index,
            isCustom = false
        )
        TimerService.initPreset(context, DEFAULT_PRESETS[index])
    }

    fun enableCustom() {
        timerPreferences.isCustom = true
        _uiState.value = _uiState.value.copy(isCustom = true, selectedPresetIndex = -1)
        applyCustomPreset()
    }

    fun updateCustomWorkMinutes(minutes: Int) {
        timerPreferences.customWorkMinutes = minutes
        _uiState.value = _uiState.value.copy(customWorkMinutes = minutes)
        if (timerPreferences.isCustom) applyCustomPreset()
    }

    fun updateCustomShortBreakMinutes(minutes: Int) {
        timerPreferences.customShortBreakMinutes = minutes
        _uiState.value = _uiState.value.copy(customShortBreakMinutes = minutes)
        if (timerPreferences.isCustom) applyCustomPreset()
    }

    fun updateCustomLongBreakMinutes(minutes: Int) {
        timerPreferences.customLongBreakMinutes = minutes
        _uiState.value = _uiState.value.copy(customLongBreakMinutes = minutes)
        if (timerPreferences.isCustom) applyCustomPreset()
    }

    fun updateCustomSessions(sessions: Int) {
        timerPreferences.customSessionsBeforeLongBreak = sessions
        _uiState.value = _uiState.value.copy(customSessionsBeforeLongBreak = sessions)
        if (timerPreferences.isCustom) applyCustomPreset()
    }

    private fun applyCustomPreset() {
        val preset = timerPreferences.getActivePreset()
        TimerService.initPreset(context, preset)
    }

    fun setDndEnabled(enabled: Boolean) {
        timerPreferences.dndEnabled = enabled
        _uiState.value = _uiState.value.copy(dndEnabled = enabled)
    }

    fun setAllowCalls(allowed: Boolean) {
        timerPreferences.allowCalls = allowed
        _uiState.value = _uiState.value.copy(allowCalls = allowed)
    }

    fun setAllowMessages(allowed: Boolean) {
        timerPreferences.allowMessages = allowed
        _uiState.value = _uiState.value.copy(allowMessages = allowed)
    }

    fun setAllowAlarms(allowed: Boolean) {
        timerPreferences.allowAlarms = allowed
        _uiState.value = _uiState.value.copy(allowAlarms = allowed)
    }

    fun setAllowReminders(allowed: Boolean) {
        timerPreferences.allowReminders = allowed
        _uiState.value = _uiState.value.copy(allowReminders = allowed)
    }

    fun setAllowEvents(allowed: Boolean) {
        timerPreferences.allowEvents = allowed
        _uiState.value = _uiState.value.copy(allowEvents = allowed)
    }

    fun logout() {
        nostrClient.disconnect()
        keyManager.clearKeys()
    }
}

data class SettingsUiState(
    val npub: String? = null,
    val authMethod: String = "",
    val selectedPresetIndex: Int = 0,
    val isCustom: Boolean = false,
    val customWorkMinutes: Int = 25,
    val customShortBreakMinutes: Int = 5,
    val customLongBreakMinutes: Int = 15,
    val customSessionsBeforeLongBreak: Int = 4,
    val showLogoutConfirmation: Boolean = false,
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val monthCount: Int = 0,
    val activityData: Map<LocalDate, Int> = emptyMap(),
    val level: PomodoroLevel = PomodoroLevel.BEGINNER,
    val sevenDayAverage: Double = 0.0,
    val dndEnabled: Boolean = false,
    val allowCalls: Boolean = true,
    val allowMessages: Boolean = false,
    val allowAlarms: Boolean = true,
    val allowReminders: Boolean = false,
    val allowEvents: Boolean = false
)
