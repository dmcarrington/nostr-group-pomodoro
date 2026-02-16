package com.pomodoro.nostr.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pomodoro.nostr.nostr.SessionPublisher
import com.pomodoro.nostr.timer.DEFAULT_PRESETS
import com.pomodoro.nostr.timer.PomodoroPreset
import com.pomodoro.nostr.timer.TimerPhase
import com.pomodoro.nostr.timer.TimerPreferences
import com.pomodoro.nostr.timer.TimerService
import com.pomodoro.nostr.timer.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timerPreferences: TimerPreferences,
    private val sessionPublisher: SessionPublisher
) : ViewModel() {

    val timerState: StateFlow<TimerState> = TimerService.timerState

    val presets: List<PomodoroPreset> = DEFAULT_PRESETS

    private val _pendingAmberSessionSign = MutableStateFlow<Intent?>(null)
    val pendingAmberSessionSign: StateFlow<Intent?> = _pendingAmberSessionSign.asStateFlow()

    init {
        val preset = timerPreferences.getActivePreset()
        TimerService.initPreset(context, preset)

        // Observe session completions and publish to Nostr
        viewModelScope.launch {
            TimerService.sessionCompleted.collect { durationMinutes ->
                if (durationMinutes != null) {
                    TimerService.clearSessionCompleted()
                    val unsignedEvent = sessionPublisher.publishSession(durationMinutes)
                    if (unsignedEvent != null) {
                        // Amber user — need to sign via intent
                        // Store for UI to launch Amber
                        _pendingAmberSessionSign.value =
                            sessionPublisher.createAmberSignIntent(unsignedEvent)
                    }
                }
            }
        }
    }

    fun handleAmberSessionSigned(signedEventJson: String) {
        _pendingAmberSessionSign.value = null
        sessionPublisher.publishSignedEvent(signedEventJson)
    }

    fun clearPendingAmberSessionSign() {
        _pendingAmberSessionSign.value = null
    }

    fun start() {
        TimerService.sendAction(context, TimerService.ACTION_START)
    }

    fun pause() {
        TimerService.sendAction(context, TimerService.ACTION_PAUSE)
    }

    fun reset() {
        TimerService.sendAction(context, TimerService.ACTION_RESET)
    }

    fun skip() {
        TimerService.sendAction(context, TimerService.ACTION_SKIP)
    }

    fun selectPreset(index: Int) {
        val state = timerState.value
        if (state.phase != TimerPhase.IDLE && state.isRunning) return

        timerPreferences.selectedPresetIndex = index
        timerPreferences.isCustom = false
        val preset = DEFAULT_PRESETS[index]
        TimerService.initPreset(context, preset)
    }

    fun getSelectedPresetIndex(): Int {
        return if (timerPreferences.isCustom) -1 else timerPreferences.selectedPresetIndex
    }
}
