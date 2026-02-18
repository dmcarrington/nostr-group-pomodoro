package com.pomodoro.nostr.timer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pomodoro.nostr.MainActivity
import com.pomodoro.nostr.PomodoroApp
import com.pomodoro.nostr.R
import com.pomodoro.nostr.nostr.LevelCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickJob: Job? = null
    private lateinit var sessionHistory: SessionHistory
    private lateinit var levelCalculator: LevelCalculator

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionHistory = SessionHistory(applicationContext)
        levelCalculator = LevelCalculator(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESET -> resetTimer()
            ACTION_SKIP -> skipToNext()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickJob?.cancel()
        instance = null
        super.onDestroy()
    }

    private fun startTimer() {
        val state = _timerState.value

        // If idle, start a new work session
        if (state.phase == TimerPhase.IDLE) {
            val workMillis = state.currentPreset.workMinutes * 60_000L
            _timerState.value = state.copy(
                phase = TimerPhase.WORK,
                remainingMillis = workMillis,
                totalMillis = workMillis,
                isRunning = true
            )
        } else {
            _timerState.value = state.copy(isRunning = true)
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startTicking()
    }

    private fun pauseTimer() {
        tickJob?.cancel()
        _timerState.value = _timerState.value.copy(isRunning = false)
        updateNotification()
    }

    private fun resetTimer() {
        tickJob?.cancel()
        _timerState.value = TimerState(currentPreset = _timerState.value.currentPreset)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun skipToNext() {
        tickJob?.cancel()
        advancePhase()
        if (_timerState.value.isRunning) {
            startTicking()
        }
        updateNotification()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = serviceScope.launch {
            while (_timerState.value.isRunning && _timerState.value.remainingMillis > 0) {
                delay(100L)
                val current = _timerState.value
                val newRemaining = (current.remainingMillis - 100L).coerceAtLeast(0L)
                _timerState.value = current.copy(remainingMillis = newRemaining)

                // Update notification every second
                if (newRemaining % 1000L < 100L) {
                    updateNotification()
                }

                if (newRemaining == 0L) {
                    advancePhase()
                    // Auto-start next phase
                    if (_timerState.value.phase != TimerPhase.IDLE) {
                        startTicking()
                    }
                    return@launch
                }
            }
        }
    }

    private fun advancePhase() {
        val state = _timerState.value
        val preset = state.currentPreset

        when (state.phase) {
            TimerPhase.WORK -> {
                sessionHistory.recordSession()
                _sessionCompleted.value = preset.workMinutes
                checkAndNotifyLevelUp()
                val newCompleted = state.completedSessions + 1
                val isLongBreak = newCompleted % preset.sessionsBeforeLongBreak == 0
                val breakMinutes = if (isLongBreak) preset.longBreakMinutes else preset.shortBreakMinutes
                val breakPhase = if (isLongBreak) TimerPhase.LONG_BREAK else TimerPhase.SHORT_BREAK
                val breakMillis = breakMinutes * 60_000L

                _timerState.value = state.copy(
                    phase = breakPhase,
                    remainingMillis = breakMillis,
                    totalMillis = breakMillis,
                    completedSessions = newCompleted,
                    isRunning = true
                )
            }
            TimerPhase.SHORT_BREAK, TimerPhase.LONG_BREAK -> {
                val workMillis = preset.workMinutes * 60_000L
                _timerState.value = state.copy(
                    phase = TimerPhase.WORK,
                    remainingMillis = workMillis,
                    totalMillis = workMillis,
                    isRunning = true
                )
            }
            TimerPhase.IDLE -> { /* No-op */ }
        }

        updateNotification()
    }

    private fun checkAndNotifyLevelUp() {
        val newLevel = levelCalculator.checkLevelUp() ?: return
        try {
            val contentIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, PomodoroApp.LEVEL_CHANNEL_ID)
                .setContentTitle("Level Up!")
                .setContentText("Congratulations, you are now a Pomodoro ${newLevel.displayName}")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.notify(LEVEL_NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun buildNotification(): Notification {
        val state = _timerState.value
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val phaseLabel = when (state.phase) {
            TimerPhase.WORK -> "Focus"
            TimerPhase.SHORT_BREAK -> "Break"
            TimerPhase.LONG_BREAK -> "Long Break"
            TimerPhase.IDLE -> "Ready"
        }

        val timeText = formatTime(state.remainingMillis)
        val pauseResumeAction = if (state.isRunning) {
            NotificationCompat.Action.Builder(
                0, "Pause",
                createActionPendingIntent(ACTION_PAUSE)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                0, "Resume",
                createActionPendingIntent(ACTION_START)
            ).build()
        }

        return NotificationCompat.Builder(this, PomodoroApp.TIMER_CHANNEL_ID)
            .setContentTitle("$phaseLabel - $timeText")
            .setContentText("Session ${state.completedSessions + 1}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(pauseResumeAction)
            .addAction(
                NotificationCompat.Action.Builder(
                    0, "Skip",
                    createActionPendingIntent(ACTION_SKIP)
                ).build()
            )
            .build()
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
            // Notification update failed, not critical
        }
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_START = "com.pomodoro.nostr.START"
        const val ACTION_PAUSE = "com.pomodoro.nostr.PAUSE"
        const val ACTION_RESET = "com.pomodoro.nostr.RESET"
        const val ACTION_SKIP = "com.pomodoro.nostr.SKIP"
        private const val NOTIFICATION_ID = 1
        private const val LEVEL_NOTIFICATION_ID = 2

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        /** Emits work duration in minutes when a WORK session completes. Consumed by TimerViewModel. */
        private val _sessionCompleted = MutableStateFlow<Int?>(null)
        val sessionCompleted: StateFlow<Int?> = _sessionCompleted.asStateFlow()

        fun clearSessionCompleted() {
            _sessionCompleted.value = null
        }

        private var instance: TimerService? = null

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, TimerService::class.java).apply {
                this.action = action
            }
            if (action == ACTION_START) {
                // Only START needs startForegroundService (it calls startForeground())
                context.startForegroundService(intent)
            } else {
                // PAUSE/RESET/SKIP only work when service is already running
                instance?.let { context.startService(intent) }
            }
        }

        /**
         * Update the preset directly on the shared StateFlow.
         * No need to start the service just to change preset config.
         */
        fun initPreset(context: Context, preset: PomodoroPreset) {
            val currentState = _timerState.value
            _timerState.value = currentState.copy(
                currentPreset = preset,
                totalMillis = if (currentState.phase == TimerPhase.IDLE) preset.workMinutes * 60_000L else currentState.totalMillis,
                remainingMillis = if (currentState.phase == TimerPhase.IDLE) preset.workMinutes * 60_000L else currentState.remainingMillis
            )
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000 // Round up
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
