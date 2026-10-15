package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.pokedaisy.app.companion.NewFeatures
import com.pokedaisy.app.companion.data.PinballCatchEntry
import com.pokedaisy.app.companion.data.PinballCollectionSnapshot
import com.pokedaisy.app.companion.data.PinballEvolutionEntry
import com.pokedaisy.app.companion.data.PinballEvolutionOutcome
import com.pokedaisy.app.companion.data.PinballPhase
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr

enum class PinballCollectionPage { CATCH, EVOLVE }
enum class PinballCatchPool { TWO_ARROWS, THREE_ARROWS }

/**
 * An on-demand, read-only catalog from Pinball's own ROM and current run. The two
 * regular Catch pools are deliberately separate: a third arrow can remove a
 * two-arrow species. Neither a pool nor a blocked-row note predicts the next catch.
 *
 * The initial selections support previews; remembered controls and scroll states
 * are not keyed to telemetry, so polling (or pausing the game) never resets browsing.
 * No RPG species IDs, sprites, input actions or memory writes are used here.
 */
@Composable
fun PinballCollectionScreen(
    state: PinballCollectionSnapshot,
    modifier: Modifier = Modifier,
    initialPage: PinballCollectionPage = PinballCollectionPage.CATCH,
    initialPool: PinballCatchPool = PinballCatchPool.TWO_ARROWS,
    initialMissingOnly: Boolean = false,
) {
    val m = rememberGbaTextMetrics()
    val small = rememberGbaTextMetrics(1f)
    var page by remember { mutableStateOf(initialPage) }
    var pool by remember { mutableStateOf(initialPool) }
    var missingOnly by remember { mutableStateOf(initialMissingOnly) }
    val twoArrowScroll = rememberLazyListState()
    val threeArrowScroll = rememberLazyListState()
    val evolutionScroll = rememberLazyListState()
    val browsing = state.phase == PinballPhase.PLAYING

    Column(
        modifier.fillMaxSize().testTag("pinball-collection-panel"),
        verticalArrangement = Arrangement.spacedBy(m.u * 4),
    ) {
        OptionTitleWindow(tk("COLLECTION"), m) {
            if (browsing && state.tableLabel.isNotBlank()) {
                GbaText(tr(state.tableLabel), OptionColors.label, OptionColors.labelShadow, small,
                    Modifier.padding(end = m.u * 4).testTag("pinball-collection-table"))
            }
            NewFeatures.badge(NewFeatures.PINBALL)?.let {
                OptionBadge(it, m, fill = OptionColors.newBadge, shadow = OptionColors.newBadgeShadow)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.u * 3)) {
            OptionButton(tk("CATCH"), small, onClick = { page = PinballCollectionPage.CATCH },
                emphasis = page == PinballCollectionPage.CATCH, enabled = browsing,
                modifier = Modifier.weight(1f).testTag("pinball-collection-catch")
                    .semantics { selected = page == PinballCollectionPage.CATCH })
            OptionButton(tk("EVOLVE"), small, onClick = { page = PinballCollectionPage.EVOLVE },
                emphasis = page == PinballCollectionPage.EVOLVE, enabled = browsing,
                modifier = Modifier.weight(1f).testTag("pinball-collection-evolve")
                    .semantics { selected = page == PinballCollectionPage.EVOLVE })
            OptionButton(if (missingOnly) tk("MISSING ONLY") else tk("ALL"), small,
                onClick = { missingOnly = !missingOnly }, emphasis = missingOnly, enabled = browsing,
                modifier = Modifier.weight(1.35f).testTag("pinball-collection-missing-toggle")
                    .semantics { selected = missingOnly })
        }
        OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
            // Even an inconsistent sample carrying old rows cannot expose them in a
            // menu, launch sequence, bonus table or failed-read state.
            when {
                !browsing -> PinballCollectionWaiting(state, m, small)
                page == PinballCollectionPage.CATCH -> PinballCatchCatalog(
                    state, pool, missingOnly,
                    if (pool == PinballCatchPool.TWO_ARROWS) twoArrowScroll else threeArrowScroll,
                    m, small, onPool = { pool = it },
                )
                else -> PinballEvolutionCatalog(state, missingOnly, evolutionScroll, m, small)
            }
        }
        OptionListWindow(small, Modifier.fillMaxWidth().testTag("pinball-collection-footer")) {
            // Keep only the validation disclosure fixed, including without fresh
            // data. Other notes scroll so an annotated row fits the shorter Duo.
            CollectionNote(tr("EXPERIMENTAL: limited gameplay validation."), small,
                "pinball-collection-experimental")
        }
    }
}

@Composable
private fun PinballCatchCatalog(
    state: PinballCollectionSnapshot,
    pool: PinballCatchPool,
    missingOnly: Boolean,
    scroll: LazyListState,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    onPool: (PinballCatchPool) -> Unit,
) {
    val poolEntries = state.catchEntries.filter {
        if (pool == PinballCatchPool.TWO_ARROWS) it.twoArrows else it.threeArrows
    }
    val visibleEntries = poolEntries.filter { !missingOnly || !it.caught }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(m.u * 3),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.u * 5)) {
            GbaText(state.areaLabel?.let { tr(it) }.orEmpty(), OptionColors.label,
                OptionColors.labelShadow, m, Modifier.weight(1f).testTag("pinball-collection-area"), maxLines = 2)
            state.catchArrows?.let {
                GbaText(tr("CURRENT ARROWS: {0}", it), OptionColors.label, OptionColors.labelShadow,
                    small, Modifier.testTag("pinball-current-arrows"), maxLines = 2)
            }
            GbaText(tr("{0} MISSING", poolEntries.count { !it.caught }), OptionColors.value,
                OptionColors.valueShadow, small, Modifier.testTag("pinball-pool-missing-count"))
        }
        // Keep focusable pool controls outside the lazy layout. Swapping a
        // focused lazy header between saved scroll states can double-release its
        // pinned item on Compose Desktop 1.5, and can hide controls while browsing.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.u * 3)) {
            OptionButton(tk("2 ARROWS"), small, onClick = { onPool(PinballCatchPool.TWO_ARROWS) },
                emphasis = pool == PinballCatchPool.TWO_ARROWS,
                modifier = Modifier.weight(1f).testTag("pinball-pool-2")
                    .semantics { selected = pool == PinballCatchPool.TWO_ARROWS })
            OptionButton(tk("3 ARROWS"), small, onClick = { onPool(PinballCatchPool.THREE_ARROWS) },
                emphasis = pool == PinballCatchPool.THREE_ARROWS,
                modifier = Modifier.weight(1f).testTag("pinball-pool-3")
                    .semantics { selected = pool == PinballCatchPool.THREE_ARROWS })
        }
        key(pool) {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).testTag("pinball-catch-list"), state = scroll,
                verticalArrangement = Arrangement.spacedBy(m.u * 3),
            ) {
                item(key = "scope") {
                    CollectionNote(tr("Area catalog only, not the next catch."), small, "pinball-collection-scope")
                }
                if (state.specialEncounterPending) item(key = "special") {
                    CollectionNote(tr("A special encounter can replace the regular pool."), small, "pinball-special-catch")
                }
                state.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    item(key = "detail") { CollectionNote(tr(detail), small, "pinball-collection-detail") }
                }
                if (visibleEntries.isEmpty()) item(key = "empty") {
                    CollectionNote(
                        if (poolEntries.isEmpty()) tr("No regular catches are listed for this pool.")
                        else tr("All species in this pool are caught."),
                        m, "pinball-catch-empty",
                    )
                }
                items(visibleEntries, key = { it.speciesId }) { entry ->
                    PinballCatchRow(entry, m, small)
                }
                item(key = "pool-note") {
                    CollectionNote(tr("3 arrows replace the 2-arrow pool."), small)
                }
                item(key = "special-scope") {
                    CollectionNote(tr("Special encounters are not listed."), small)
                }
            }
        }
    }
}

@Composable
private fun PinballCatchRow(entry: PinballCatchEntry, m: GbaTextMetrics, small: GbaTextMetrics) {
    Column(
        Modifier.fillMaxWidth().testTag("pinball-catch-${entry.speciesId}")
            .drawBehind { drawRowDivider(OptionColors.divider, m.u.toPx(), 0f) }
            .padding(vertical = m.u * 4),
        verticalArrangement = Arrangement.spacedBy(m.u * 2),
    ) {
        CollectionSpeciesRow(entry.name, entry.caught, m, small)
        if (entry.firstCatchRequired) {
            CollectionNote(tr("Catch a Pokémon in this run first."), small, "pinball-first-catch-${entry.speciesId}")
        }
        if (entry.excludedPreviousTarget) {
            CollectionNote(tr("Excluded from the next regular Catch."), small, "pinball-previous-target-${entry.speciesId}")
        }
    }
}

@Composable
private fun PinballEvolutionCatalog(
    state: PinballCollectionSnapshot,
    missingOnly: Boolean,
    scroll: LazyListState,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
) {
    // Keep both outcomes visible for a retained branch: hiding its caught form
    // would imply that the missing form is the sole possible result.
    val entries = state.evolutions.filter { !missingOnly || it.targets.any { target -> !target.caught } }
    LazyColumn(
        Modifier.fillMaxSize().testTag("pinball-evolution-list"), state = scroll,
        verticalArrangement = Arrangement.spacedBy(m.u * 3),
    ) {
        item(key = "party") {
            GbaText(tr("CURRENT RUN PARTY"), OptionColors.label, OptionColors.labelShadow, m, maxLines = 2)
        }
        item(key = "scope") {
            CollectionNote(tr("Only your current run's evolvable party is listed."), small, "pinball-collection-scope")
        }
        state.evolutionReady?.let { ready -> item(key = "ready") {
            GbaText(if (ready) tr("EVOLUTION READY") else tr("EVOLUTION NOT READY"),
                if (ready) OptionColors.success else OptionColors.label, OptionColors.labelShadow, small,
                Modifier.testTag("pinball-evolution-ready"), maxLines = 2)
        } }
        state.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            item(key = "detail") { CollectionNote(tr(detail), small, "pinball-collection-detail") }
        }
        if (entries.isEmpty()) item(key = "empty") {
            CollectionNote(
                if (state.evolutions.isEmpty()) tr("No evolvable Pokémon in this run's party.")
                else tr("All evolution outcomes are already caught."),
                m, "pinball-evolution-empty",
            )
        }
        items(entries, key = { it.partySlot }) { entry ->
            PinballEvolutionRow(entry, m, small)
        }
    }
}

@Composable
private fun PinballEvolutionRow(entry: PinballEvolutionEntry, m: GbaTextMetrics, small: GbaTextMetrics) {
    Column(
        Modifier.fillMaxWidth().testTag("pinball-evolution-${entry.partySlot}")
            .drawBehind { drawRowDivider(OptionColors.divider, m.u.toPx(), 0f) }
            .padding(vertical = m.u * 4),
        verticalArrangement = Arrangement.spacedBy(m.u * 2),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.u * 5)) {
            GbaText(entry.name, OptionColors.label, OptionColors.labelShadow, m,
                Modifier.weight(1f), maxLines = 2)
            GbaText(tr("SLOT {0}", entry.partySlot + 1), OptionColors.label,
                OptionColors.labelShadow, small)
        }
        CollectionNote(when (entry.outcome) {
            PinballEvolutionOutcome.FIXED -> tr("EVOLVES INTO")
            PinballEvolutionOutcome.ONE_OF -> tr("ONE OF THESE")
            PinballEvolutionOutcome.BOTH -> tr("BOTH ON SUCCESS")
        }, small, "pinball-evolution-outcome-${entry.partySlot}")
        entry.targets.forEach { target ->
            CollectionSpeciesRow(target.name, target.caught, small, small,
                Modifier.padding(start = m.u * 6).testTag("pinball-evolution-target-${entry.partySlot}-${target.speciesId}"))
        }
        if (entry.outcome == PinballEvolutionOutcome.ONE_OF) {
            CollectionNote(tr("A missing form is favored."), small)
        }
    }
}

@Composable
private fun CollectionSpeciesRow(
    name: String,
    caught: Boolean,
    m: GbaTextMetrics,
    small: GbaTextMetrics,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.u * 5)) {
        GbaText(name, OptionColors.label, OptionColors.labelShadow, m,
            Modifier.weight(1f), maxLines = 2)
        GbaText(if (caught) tr("CAUGHT") else tr("MISSING"),
            if (caught) OptionColors.success else OptionColors.value,
            if (caught) OptionColors.labelShadow else OptionColors.valueShadow,
            small, maxLines = 2)
    }
}

@Composable
private fun PinballCollectionWaiting(state: PinballCollectionSnapshot, m: GbaTextMetrics, small: GbaTextMetrics) {
    LazyColumn(Modifier.fillMaxSize().testTag("pinball-collection-waiting"),
        verticalArrangement = Arrangement.spacedBy(m.u * 5)) {
        item {
            GbaText(if (state.phase == PinballPhase.UNAVAILABLE) tr("UNAVAILABLE") else tr("WAITING"),
                OptionColors.value, OptionColors.valueShadow, m, maxLines = 2)
        }
        item {
            CollectionNote(state.detail?.takeIf { it.isNotBlank() }?.let { tr(it) }
                ?: if (state.phase == PinballPhase.UNAVAILABLE) tr("Waiting for fresh collection data.")
                else tr("Start or resume a game to view its collection."),
                small, "pinball-collection-detail")
        }
    }
}

@Composable
private fun CollectionNote(text: String, m: GbaTextMetrics, tag: String? = null) {
    GbaText(text, OptionColors.label, OptionColors.labelShadow, m,
        if (tag == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().testTag(tag),
        maxLines = Int.MAX_VALUE)
}
