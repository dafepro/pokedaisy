package com.pokedaisy.app.companion.data

/** Read-only companion data. A menu, invalid read or demo never masquerades as live play. */
enum class PinballPhase { PLAYING, MENU, UNAVAILABLE }

data class PinballSnapshot(
    val phase: PinballPhase,
    val tableLabel: String = "PINBALL",
    val modeLabel: String = "WAITING",
    val timerSeconds: Int? = null,
    val objective: String = "Start a game on either table.",
    val progress: String? = null,
    val ballSaverSeconds: Int? = null,
    val ball: Int? = null,
    val score: Long? = null,
    val areaLabel: String? = null,
    val detail: String? = null,
)
