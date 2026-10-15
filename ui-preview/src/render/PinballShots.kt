@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.pokedaisy.app.companion.CompanionSettings
import com.pokedaisy.app.companion.StateSlots
import com.pokedaisy.app.companion.data.PinballPhase
import com.pokedaisy.app.companion.data.PinballSnapshot
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.ui.CompanionBack
import com.pokedaisy.app.companion.ui.CompanionScreen

/**
 * Synthetic inputs into the real companion, never captured RAM or a gameplay test.
 * Every panel says SYNTHETIC; no ROM, game art, real species or saved-state pictures
 * are used. Fixed device densities also check the short Duo Lite and swapped screen.
 */
fun pinballShots(): List<Shot> = buildList {
    val devices = listOf(
        PinballDevice("thor", 1240, 1080, 2.625f),
        PinballDevice("duo-lite", 1280, 960, 2.625f),
        PinballDevice("wide", 1920, 1080, 2f),
    )
    for (device in devices) {
        for ((name, state) in syntheticPinballStates) {
            add(Shot("pinball-synthetic-$name-${device.name}", device.w, device.h, device.density,
                pinballCompanion(state)) {
                assertPinball(state)
                if (state.phase != PinballPhase.PLAYING) assertNoPinballCounters()
            })
        }
        add(Shot("pinball-synthetic-back-navigation-${device.name}", device.w, device.h, device.density,
            pinballCompanion(syntheticPinballStates.getValue("catch"))) {
            assertPinball(syntheticPinballStates.getValue("catch"))
            repeat(4) {
                onNodeWithContentDescription("Settings").performClick()
                settlePinball()
                onAllNodesWithText("OPTION").onFirst().assertExists()
                onNodeWithText("FF SPEED").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
                settlePinball()
                onAllNodesWithText("INFINITE").onFirst().assertExists()
                pressBack()
                settlePinball()
                onAllNodesWithText("INFINITE").assertCountEquals(0)
                onAllNodesWithText("OPTION").onFirst().assertExists()
                onNodeWithText("HOTKEYS").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
                settlePinball()
                onAllNodesWithText("SAVE STATE").onFirst().assertExists()
                pressBack()
                settlePinball()
                onAllNodesWithText("SAVE STATE").assertCountEquals(0)
                onAllNodesWithText("OPTION").onFirst().assertExists()
                pressBack()
                settlePinball()
                onAllNodesWithText("OPTION").onFirst().assertExists()
                onNodeWithTag("tab-STATES").performClick()
                settlePinball()
                onAllNodesWithText("SAVE STATES").onFirst().assertExists()
                pressBack()
                settlePinball()
                onAllNodesWithText("SAVE STATES").onFirst().assertExists()
                onNodeWithTag("tab-PINBALL").performClick()
                settlePinball()
                assertPinball(syntheticPinballStates.getValue("catch"))
            }
        })
        // Repeated samples must not pull the user out of SETTINGS or STATES.
        val sampled = mutableStateOf(syntheticPinballStates.getValue("catch"))
        add(Shot("pinball-synthetic-sampling-navigation-${device.name}", device.w, device.h, device.density,
            pinballCompanion { sampled.value }) {
            repeat(4) {
                onNodeWithContentDescription("Settings").performClick()
                settlePinball()
                runOnIdle { sampled.value = syntheticPinballStates.getValue("evolution") }
                settlePinball()
                onAllNodesWithText("OPTION").onFirst().assertExists()
                onNodeWithTag("pinball-panel").assertDoesNotExist()
                onNodeWithTag("tab-STATES").performClick()
                settlePinball()
                runOnIdle { sampled.value = syntheticPinballStates.getValue("normal") }
                settlePinball()
                onAllNodesWithText("SAVE STATES").onFirst().assertExists()
                onNodeWithTag("pinball-panel").assertDoesNotExist()
                onNodeWithTag("tab-PINBALL").performClick()
                settlePinball()
                assertPinball(syntheticPinballStates.getValue("normal"))
            }
        })
    }
}

private data class PinballDevice(val name: String, val w: Int, val h: Int, val density: Float)

private val syntheticCatch = PinballSnapshot(
    phase = PinballPhase.PLAYING,
    tableLabel = "SYNTHETIC / RUBY TABLE",
    modeLabel = "CATCH MODE",
    timerSeconds = 57,
    objective = "Hit the target to catch EMBERPUP.",
    progress = "Target hits: 1 / 3",
    ballSaverSeconds = 9,
    ball = 3,
    score = 12_345_670L,
    areaLabel = "MEADOW",
)

private val syntheticPinballStates = linkedMapOf(
    "catch" to syntheticCatch,
    "evolution" to syntheticCatch.copy(
        tableLabel = "SYNTHETIC / SAPPHIRE TABLE", modeLabel = "EVOLUTION MODE",
        timerSeconds = 78, objective = "Collect the last evolution item.",
        progress = "Items: 2 / 3", ballSaverSeconds = 0, ball = 2,
    ),
    "normal" to syntheticCatch.copy(
        modeLabel = "NORMAL PLAY", timerSeconds = null,
        objective = "Keep the ball in play.", progress = "Catch charge: 1 / 3", ballSaverSeconds = null,
    ),
    // Deliberately retain invalid/stale counters. The UI must hide ALL of them.
    "menu" to syntheticCatch.copy(
        phase = PinballPhase.MENU, modeLabel = "MENU",
        objective = "Start a game on either table.", detail = "Synthetic menu data; not a gameplay capture.",
    ),
    "unavailable" to syntheticCatch.copy(
        phase = PinballPhase.UNAVAILABLE, modeLabel = "UNAVAILABLE",
        objective = "Companion data is unavailable.",
        detail = "Synthetic invalid read; waiting for a supported screen.",
    ),
)

/** Empty local slots, rather than FakeSlots whose thumbnails may use extracted ROM art. */
private object PinballSlots : StateSlots {
    override fun list() = (0..9).map { StateSlots.Slot(it, false, 0L, null) }
    override val currentIndex = 0
    override fun requestSave(index: Int) {}
    override fun requestLoad(index: Int) {}
    override fun requestUndoSave() {}
    override fun requestUndoLoad() {}
}

private fun pinballCompanion(state: PinballSnapshot) = pinballCompanion { state }

private fun pinballCompanion(state: () -> PinballSnapshot): () -> (@Composable () -> Unit) = {
    val back = CompanionBack().also { previewBack = it }
    val settings = object : CompanionSettings by FakeSettings() {
        override val gameName = "Synthetic Pinball preview"
        override val romFileName = "synthetic-pinball.gba"
        override val fieldReadable = false
        override val fieldMovesReadable = false
        override val runIndoorsSupported = false
        override val wildEncountersSupported = false
    }
    val content: @Composable () -> Unit = {
        // Deliberately leave initialTab at its default to test automatic PINBALL selection.
        CompanionScreen(SnapshotView(connected = true, game = null, pinball = state()),
            slots = PinballSlots, settings = settings, back = back)
    }
    content
}

private fun SkikoComposeUiTest.settlePinball() {
    repeat(20) { mainClock.advanceTimeByFrame() }
    waitForIdle()
}

private fun SkikoComposeUiTest.assertPinball(state: PinballSnapshot) {
    onNodeWithTag("pinball-panel").assertIsDisplayed()
    onNodeWithTag("pinball-objective").assertIsDisplayed()
    onAllNodesWithText(state.tableLabel).onFirst().assertExists()
    onAllNodesWithText(state.modeLabel).onFirst().assertExists()
    onAllNodesWithText(state.objective).onFirst().assertExists()
    onNodeWithTag("tab-PINBALL").assertIsDisplayed()
    onNodeWithTag("tab-STATES").assertIsDisplayed()
    listOf("PARTY", "ITEMS", "MAP", "BATTLE", "DEX", "GUIDE", "CARD").forEach {
        onNodeWithTag("tab-$it").assertDoesNotExist()
    }
    val timer = state.timerSeconds
    if (state.phase == PinballPhase.PLAYING && timer != null) {
        onNodeWithTag("pinball-timer").assertIsDisplayed()
        val seconds = timer.coerceAtLeast(0)
        onAllNodesWithText("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}").onFirst().assertExists()
    } else {
        onNodeWithTag("pinball-timer").assertDoesNotExist()
    }
    if (state.phase == PinballPhase.PLAYING) {
        onAllNodesWithText("BALLS LEFT").onFirst().assertExists()
        onAllNodesWithText("12,345,670").onFirst().assertExists()
        if (state.ballSaverSeconds == null) onNodeWithTag("pinball-saver").assertDoesNotExist()
    }
}

private fun SkikoComposeUiTest.assertNoPinballCounters() {
    listOf("timer", "progress", "saver", "balls", "score", "area").forEach {
        onNodeWithTag("pinball-$it").assertDoesNotExist()
    }
}
