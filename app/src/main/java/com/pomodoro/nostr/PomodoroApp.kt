package com.pomodoro.nostr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PomodoroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)

        val timerChannel = NotificationChannel(
            TIMER_CHANNEL_ID,
            getString(R.string.timer_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.timer_notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(timerChannel)

        val levelChannel = NotificationChannel(
            LEVEL_CHANNEL_ID,
            getString(R.string.level_notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.level_notification_channel_desc)
        }
        manager.createNotificationChannel(levelChannel)
    }

    companion object {
        const val TIMER_CHANNEL_ID = "pomodoro_timer"
        const val LEVEL_CHANNEL_ID = "pomodoro_level"
    }
}
