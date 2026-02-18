package com.pomodoro.nostr.nostr

object SessionEvents {
    const val KIND_POMODORO_SESSION: UShort = 8808u

    fun createSessionTags(durationMinutes: Int, level: String): List<List<String>> {
        return listOf(
            listOf("t", "pomodoro"),
            listOf("duration", durationMinutes.toString()),
            listOf("level", level)
        )
    }
}
