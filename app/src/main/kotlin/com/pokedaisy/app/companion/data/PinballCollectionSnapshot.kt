package com.pokedaisy.app.companion.data

/** Pinball's own 0-based species order, not the RPG internal species IDs or National Dex. */
enum class PinballDexStatus {
    UNSEEN, SEEN, SHARED, SHARED_AND_SEEN, CAUGHT;
    val caught: Boolean get() = this == CAUGHT
}

data class PinballCollectionSpecies(
    val speciesId: Int,
    val name: String,
    val dexStatus: PinballDexStatus,
) {
    val caught: Boolean get() = dexStatus.caught
}

/** A regular area-pool possibility, never a forecast of the next randomly selected target.
 * [firstCatchRequired] and [excludedPreviousTarget] describe current normal-pool exclusions.
 * Two and three arrows are separate pools; three arrows can remove a two-arrow species.
 */
data class PinballCatchEntry(
    val speciesId: Int,
    val name: String,
    val dexStatus: PinballDexStatus,
    val twoArrows: Boolean,
    val threeArrows: Boolean,
    val firstCatchRequired: Boolean = false,
    val excludedPreviousTarget: Boolean = false,
) {
    val caught: Boolean get() = dexStatus.caught
}

enum class PinballEvolutionOutcome {
    FIXED,
    /** One of the two listed forms; the game favors a form not caught yet. */
    ONE_OF,
    /** Both listed forms are registered by the same successful evolution. */
    BOTH,
}

/** An actual member of the current run's evolvable party, not every species in the save's Dex.
 * Duplicate party members retain their individual slots. Targets mean one evolution step only.
 */
data class PinballEvolutionEntry(
    val partySlot: Int,
    val speciesId: Int,
    val name: String,
    val targets: List<PinballCollectionSpecies>,
    val outcome: PinballEvolutionOutcome = PinballEvolutionOutcome.FIXED,
)

data class PinballCollectionSnapshot(
    val phase: PinballPhase,
    val tableLabel: String = "PINBALL",
    val areaLabel: String? = null,
    val catchEntries: List<PinballCatchEntry> = emptyList(),
    val evolutions: List<PinballEvolutionEntry> = emptyList(),
    /** Current lamps only during ordinary play; other modes temporarily clear/store them. */
    val catchArrows: Int? = null,
    val evolutionReady: Boolean? = null,
    val specialEncounterPending: Boolean = false,
    val detail: String? = null,
) {
    val missingCatchCount: Int get() = catchEntries.count { !it.caught }
}
