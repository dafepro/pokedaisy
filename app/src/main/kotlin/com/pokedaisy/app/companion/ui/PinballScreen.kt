package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.pokedaisy.app.companion.NewFeatures
import com.pokedaisy.app.companion.data.PinballPhase
import com.pokedaisy.app.companion.data.PinballSnapshot
import com.pokedaisy.app.companion.i18n.tr
import java.text.NumberFormat
import java.util.Locale

/**
 * Read-only, sampled Pinball status in the companion's OPTION look. The objective
 * stays above supporting numbers; this screen never starts its own timer or adds
 * gameplay advice to what the reader can establish. It contains no game art.
 */
@Composable
fun PinballScreen(state: PinballSnapshot, modifier: Modifier = Modifier) {
    val m = rememberGbaTextMetrics()
    val small = rememberGbaTextMetrics(1f)
    Column(
        modifier.fillMaxSize().testTag("pinball-panel"),
        verticalArrangement = Arrangement.spacedBy(m.u * 4),
    ) {
        OptionTitleWindow(tr("PINBALL"), m) {
            NewFeatures.badge(NewFeatures.PINBALL)?.let {
                OptionBadge(it, m, fill = OptionColors.newBadge, shadow = OptionColors.newBadgeShadow)
            }
        }
        OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(m.u * 4),
                verticalArrangement = Arrangement.spacedBy(m.u * 5),
            ) {
                if (state.tableLabel.isNotBlank()) {
                    GbaText(state.tableLabel, OptionColors.label, OptionColors.labelShadow, small,
                        maxLines = 2, modifier = Modifier.testTag("pinball-table"))
                }
                GbaText(state.modeLabel, OptionColors.value, OptionColors.valueShadow, m,
                    maxLines = 2, modifier = Modifier.testTag("pinball-mode"))
                GbaText(
                    state.objective,
                    OptionColors.label, OptionColors.labelShadow, m,
                    modifier = Modifier.fillMaxWidth().background(OptionColors.rowSelected)
                        .padding(m.u * 6).testTag("pinball-objective"),
                    maxLines = Int.MAX_VALUE,
                )
                // A menu or unsupported snapshot must not display stale play counters.
                if (state.phase == PinballPhase.PLAYING) {
                    state.timerSeconds?.let { seconds ->
                        PinballValue(tr("TIME LEFT"), pinballTime(seconds), m, "pinball-timer")
                    }
                    state.progress?.takeIf { it.isNotBlank() }?.let {
                        PinballValue(tr("PROGRESS"), it, small, "pinball-progress")
                    }
                    state.ballSaverSeconds?.let { seconds ->
                        PinballValue(tr("BALL SAVER"), if (seconds > 0) "${seconds}s" else tr("OFF"), small, "pinball-saver")
                    }
                    state.ball?.let { PinballValue(tr("BALLS LEFT"), it.toString(), small, "pinball-balls") }
                    state.score?.let {
                        PinballValue(tr("SCORE"), NumberFormat.getIntegerInstance(Locale.US).format(it), small, "pinball-score")
                    }
                    state.areaLabel?.takeIf { it.isNotBlank() }?.let {
                        PinballValue(tr("AREA"), it, small, "pinball-area")
                    }
                }
                state.detail?.takeIf { it.isNotBlank() }?.let {
                    GbaText(it, OptionColors.label, OptionColors.labelShadow, small,
                        maxLines = Int.MAX_VALUE, modifier = Modifier.testTag("pinball-detail"))
                }
            }
        }
        // Persistent but separate from the live objective. These reads are source-mapped,
        // and synthetic UI previews do not constitute validation against a running game.
        OptionListWindow(small, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = m.u * 4)) {
                GbaText(tr("EXPERIMENTAL"), OptionColors.label, OptionColors.labelShadow, small)
                GbaText(tr("Source-mapped; gameplay not validated."), OptionColors.label,
                    OptionColors.labelShadow, small, maxLines = 2)
            }
        }
    }
}

/** Non-interactive LABEL / VALUE rows, with wrapping instead of clipped counters. */
@Composable
private fun PinballValue(label: String, value: String, m: GbaTextMetrics, tag: String) {
    Row(Modifier.fillMaxWidth().testTag(tag), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(m.u * 6)) {
        GbaText(label, OptionColors.label, OptionColors.labelShadow, m,
            Modifier.weight(0.46f), maxLines = 2)
        GbaText(value, OptionColors.value, OptionColors.valueShadow, m,
            Modifier.weight(0.54f), maxLines = Int.MAX_VALUE)
    }
}

private fun pinballTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}
