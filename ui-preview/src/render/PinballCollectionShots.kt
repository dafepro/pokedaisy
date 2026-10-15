@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package render

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.pokedaisy.app.companion.data.*

/** Fictional names and synthetic inputs, rendered through the real companion. No ROM art or RAM. */
fun pinballCollectionShots(): List<Shot> = buildList {
    val live = PinballSnapshot(PinballPhase.PLAYING, tableLabel = "SYNTHETIC / RUBY TABLE",
        modeLabel = "FREE PLAY", objective = "Light the Catch arrows.")
    val catalog = PinballCollectionSnapshot(
        PinballPhase.PLAYING, tableLabel = "SYNTHETIC / RUBY TABLE", areaLabel = "Forest", catchArrows = 2,
        catchEntries = listOf(
            PinballCatchEntry(10, "SAMPLE A", PinballDexStatus.SEEN, true, false),
            PinballCatchEntry(20, "SAMPLE B", PinballDexStatus.CAUGHT, true, true),
            PinballCatchEntry(30, "SAMPLE C", PinballDexStatus.UNSEEN, false, true, firstCatchRequired = true),
        ),
        evolutionReady = true,
        evolutions = listOf(
            PinballEvolutionEntry(0, 10, "SAMPLE A", listOf(
                PinballCollectionSpecies(11, "FORM A1", PinballDexStatus.CAUGHT),
                PinballCollectionSpecies(12, "FORM A2", PinballDexStatus.UNSEEN),
            ), PinballEvolutionOutcome.ONE_OF),
            PinballEvolutionEntry(1, 20, "SAMPLE B", listOf(
                PinballCollectionSpecies(21, "FORM B1", PinballDexStatus.UNSEEN),
                PinballCollectionSpecies(22, "FORM B2", PinballDexStatus.UNSEEN),
            ), PinballEvolutionOutcome.BOTH),
        ),
    )
    for ((device, size) in linkedMapOf(
        "thor" to Triple(1240, 1080, 2.625f), "duo-lite" to Triple(1280, 960, 2.625f),
        "wide" to Triple(1920, 1080, 2f),
    )) {
        fun shot(name: String, state: PinballCollectionSnapshot = catalog, action: androidx.compose.ui.test.SkikoComposeUiTest.() -> Unit = {}) {
            add(Shot("pinball-collection-synthetic-$name-$device", size.first, size.second, size.third,
                pinballCompanion({ live }, { state }, "COLLECT")) {
                onNodeWithTag("tab-COLLECT").assertIsDisplayed()
                onNodeWithTag("pinball-collection-panel").assertIsDisplayed()
                onNodeWithTag("pinball-collection-experimental").assertIsDisplayed()
                onNodeWithTag("pinball-panel").assertDoesNotExist()
                action()
            })
        }
        shot("catch") {
            onNodeWithTag("pinball-pool-2").assertIsSelected()
            onNodeWithTag("pinball-catch-10").assertExists()
            onNodeWithTag("pinball-catch-list").performScrollToKey(20)
            settleCollection()
            onNodeWithTag("pinball-catch-20").assertIsDisplayed()
            onNodeWithTag("pinball-catch-list").performScrollToIndex(0)
            settleCollection()
            onNodeWithTag("pinball-catch-30").assertDoesNotExist()
        }
        val longCatalog = catalog.copy(tableLabel = "SYNTHETIC / SAPPHIRE TABLE", areaLabel = "Wilderness",
            catchEntries = (0 until 8).map { i ->
                PinballCatchEntry(100 + i, "CREATURE ${i + 1}", if (i == 2) PinballDexStatus.CAUGHT else PinballDexStatus.UNSEEN,
                    twoArrows = true, threeArrows = true, firstCatchRequired = i == 0, excludedPreviousTarget = i == 7)
            })
        // Diagnostic baseline: capture the initial viewport before any scrolling.
        // Keep the separate long-pool interaction assertions below intact.
        shot("long-pool-initial", longCatalog)
        shot("long-pool", longCatalog) {
            onNodeWithTag("pinball-catch-list").performScrollToKey(107)
            settleCollection()
            onNodeWithTag("pinball-catch-107").assertIsDisplayed()
            onNodeWithTag("pinball-pool-2").assertIsDisplayed()
            onNodeWithTag("pinball-collection-area").assertIsDisplayed()
            onNodeWithTag("pinball-catch-list").performScrollToIndex(0)
            settleCollection()
        }
        shot("evolve") {
            onNodeWithTag("pinball-collection-evolve").performSemanticsAction(SemanticsActions.OnClick)
            settleCollection()
            onNodeWithTag("pinball-evolution-0").assertExists()
            onNodeWithTag("pinball-evolution-target-0-11").assertExists()
            onNodeWithTag("pinball-evolution-target-0-12").assertExists()
        }
        shot("waiting", catalog.copy(phase = PinballPhase.MENU,
            detail = "SYNTHETIC: Start or resume a game to view its collection.")) {
            onNodeWithTag("pinball-collection-waiting").assertIsDisplayed()
            onNodeWithTag("pinball-catch-10").assertDoesNotExist()
            onNodeWithTag("pinball-evolution-0").assertDoesNotExist()
        }
        shot("unavailable", catalog.copy(phase = PinballPhase.UNAVAILABLE,
            detail = "SYNTHETIC: Waiting for fresh collection data.")) {
            onNodeWithTag("pinball-collection-waiting").assertIsDisplayed()
            onNodeWithTag("pinball-catch-10").assertDoesNotExist()
            onNodeWithTag("pinball-evolution-0").assertDoesNotExist()
        }
        shot("empty", catalog.copy(evolutions = emptyList(), evolutionReady = false)) {
            onNodeWithTag("pinball-collection-evolve").performSemanticsAction(SemanticsActions.OnClick)
            settleCollection()
            onNodeWithTag("pinball-evolution-empty").assertIsDisplayed()
        }
        // User choices persist across telemetry updates; filtering never implies a branch guarantee.
        val sampled = mutableStateOf(catalog)
        add(Shot("pinball-collection-synthetic-interaction-$device", size.first, size.second, size.third,
            pinballCompanion({ live }, { sampled.value }, "COLLECT")) {
            onNodeWithTag("pinball-collection-missing-toggle").performClick()
            settleCollection()
            onNodeWithTag("pinball-collection-missing-toggle").assertIsSelected()
            onNodeWithTag("pinball-catch-10").assertExists()
            // Desktop 1.5 can retain a deactivated lazy item in its semantics tree.
            // Filtering must still remove it from the displayed catalog.
            awaitFilteredCatchHidden("pinball-catch-20")
            onNodeWithTag("pinball-pool-3").performClick()
            settleCollection()
            onNodeWithTag("pinball-pool-3").assertIsSelected()
            onNodeWithTag("pinball-pool-2").performClick()
            settleCollection()
            onNodeWithTag("pinball-pool-2").assertIsSelected()
            onNodeWithTag("pinball-pool-3").performClick()
            settleCollection()
            onNodeWithTag("pinball-catch-10").assertDoesNotExist()
            onNodeWithTag("pinball-catch-30").assertExists()
            runOnIdle { sampled.value = catalog.copy(catchArrows = 1) }
            settleCollection()
            onNodeWithTag("pinball-pool-3").assertIsSelected()
            onNodeWithTag("pinball-collection-missing-toggle").assertIsSelected()
            onNodeWithTag("pinball-collection-evolve").performClick()
            settleCollection()
            onNodeWithTag("pinball-evolution-target-0-11").assertExists()
            onNodeWithTag("pinball-evolution-target-0-12").assertExists()
            runOnIdle { sampled.value = catalog.copy(evolutionReady = false) }
            settleCollection()
            onNodeWithTag("pinball-collection-evolve").assertIsSelected()
            onNodeWithTag("pinball-evolution-list").performScrollToKey(1)
            settleCollection()
            onNodeWithTag("pinball-evolution-1").assertIsDisplayed()
            onNodeWithTag("pinball-evolution-outcome-1").assertExists()
            runOnIdle { sampled.value = catalog.copy(phase = PinballPhase.MENU) }
            settleCollection()
            onNodeWithTag("pinball-collection-waiting").assertIsDisplayed()
            onNodeWithTag("pinball-evolution-1").assertDoesNotExist()
            runOnIdle { sampled.value = catalog }
            settleCollection()
            onNodeWithTag("pinball-collection-evolve").assertIsSelected()
            onNodeWithTag("tab-PINBALL").performClick()
            settleCollection()
            onNodeWithTag("pinball-panel").assertIsDisplayed()
            onNodeWithTag("tab-COLLECT").performClick()
            settleCollection()
            onNodeWithTag("pinball-collection-panel").assertIsDisplayed()
        })
    }
}

/** Bound the visibility check; capture forces Desktop to finish a real layout/draw turn. */
private fun SkikoComposeUiTest.awaitFilteredCatchHidden(tag: String) {
    val deadline = System.nanoTime() + 5_000_000_000L
    do {
        mainClock.advanceTimeByFrame()
        onRoot().captureToImage()
        if (runCatching { onNodeWithTag(tag).assertIsNotDisplayed() }.isSuccess) break
        Thread.sleep(50)
    } while (System.nanoTime() < deadline)
    onAllNodesWithTag(tag).fetchSemanticsNodes().forEach { node ->
        println("collection filtered caught row: placed=${node.layoutInfo.isPlaced}, bounds=${node.boundsInRoot}")
    }
    // Still visibly present after the bounded wait is a real failure.
    onNodeWithTag(tag).assertIsNotDisplayed()
}

/**
 * Flush both the virtual animation clock and Desktop's real render/layout turns.
 * A single clock burst can update a button's selected semantics before the lazy
 * layout has removed its old rows; reading the root between turns drains both.
 */
private fun SkikoComposeUiTest.settleCollection() {
    repeat(4) {
        repeat(10) { mainClock.advanceTimeByFrame() }
        Thread.sleep(25)
        onRoot().fetchSemanticsNode()
        runOnIdle { }
    }
}
