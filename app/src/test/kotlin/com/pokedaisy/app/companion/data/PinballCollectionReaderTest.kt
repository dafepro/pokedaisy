package com.pokedaisy.app.companion.data

import org.junit.Assert.*
import org.junit.Test

/** Authored synthetic bytes, never copied ROM, RAM, save data or real captured gameplay. */
class PinballCollectionReaderTest {
    private class Fixture : MemoryReader {
        val main = ByteArray(0x10)
        val game = ByteArray(PinballReader.GAME_BYTES)
        val dex = ByteArray(PinballCollectionReader.SPECIES_COUNT)
        val species = ByteArray(PinballCollectionReader.SPECIES_COUNT * PinballCollectionReader.SPECIES_BYTES)
        val pools = ByteArray(14 * 32)
        val reads = mutableListOf<Pair<Long, Int>>()
        var pointer = PinballReader.GAME
        var shortAt: Long? = null
        var throwAt: Long? = null
        init {
            main[2] = 2; main[3] = 1
            game[0x13] = 1; game[0x16] = 1
            word(0x59C, 205)
            for (id in 0 until 205) mon(id, "MON${id.toString().padStart(3, '0')}")
            for (area in 0 until 14) pool(area, listOf(0), listOf(0, 9))
            mon(0, "TREECKO", 1); mon(1, "GROVYLE", 2); mon(2, "SCEPTILE")
            mon(9, "POOCHYENA", 10); mon(10, "MIGHTYENA")
            mon(13, "WURMPLE", 14); mon(14, "SILCOON", 15); mon(15, "BEAUTIFLY")
            mon(16, "CASCOON", 17); mon(17, "DUSTOX")
            mon(41, "NINCADA", 42); mon(42, "NINJASK"); mon(43, "SHEDINJA")
            mon(59, "NOSEPASS")
            mon(88, "GLOOM", 89); mon(89, "VILEPLUME"); mon(90, "BELLOSSOM")
            mon(132, "LILEEP", 133); mon(133, "CRADILY")
            mon(175, "CLAMPERL", 176); mon(176, "HUNTAIL"); mon(177, "GOREBYSS")
        }
        fun mon(id: Int, name: String, target: Int = 205, method: Int = if (target == 205) 0 else 1) {
            val at = id * PinballCollectionReader.SPECIES_BYTES
            name.padEnd(10).take(10).toByteArray(Charsets.US_ASCII).copyInto(species, at + 7)
            species[at + 0x14] = method.toByte(); species[at + 0x15] = target.toByte()
        }
        fun pool(area: Int, two: List<Int>, three: List<Int>) {
            require(two.size <= 8 && three.size <= 8)
            (two + List(8 - two.size) { 205 } + three + List(8 - three.size) { 205 }).forEachIndexed { index, id ->
                val at = area * 32 + index * 2
                pools[at] = id.toByte(); pools[at + 1] = (id ushr 8).toByte()
            }
        }
        fun byte(at: Int, value: Int) { game[at] = value.toByte() }
        fun word(at: Int, value: Int) { byte(at, value); byte(at + 1, value ushr 8) }
        fun party(vararg ids: Int) { byte(0x281, ids.size); ids.forEachIndexed { i, id -> byte(0x270 + i, id) } }
        fun sample() = PinballCollectionReader.read(this)
        override fun readCoreMemory(addr: Long, size: Int): ByteArray {
            reads += addr to size
            if (addr == throwAt) error("Stopped core")
            val result = when {
                addr == PinballReader.MAIN -> main.copyOf()
                addr == PinballReader.GAME_POINTER -> ByteArray(4) { (pointer ushr (it * 8)).toByte() }
                addr == PinballReader.GAME -> game.copyOf()
                addr == PinballCollectionReader.DEX -> dex.copyOf()
                addr == PinballCollectionReader.SPECIES -> species.copyOf()
                addr in PinballCollectionReader.WILD_POOLS until PinballCollectionReader.WILD_POOLS + pools.size -> {
                    val offset = (addr - PinballCollectionReader.WILD_POOLS).toInt()
                    pools.copyOfRange(offset, offset + size)
                }
                else -> error("Unexpected read")
            }
            return if (addr == shortAt) result.copyOf(result.size - 1) else result
        }
    }

    @Test fun ownSpeciesZeroAndNoneSentinelAreNotRpgIds() {
        val result = Fixture().sample()
        assertEquals(PinballPhase.PLAYING, result.phase)
        assertEquals("TREECKO", result.catchEntries.single { it.speciesId == 0 }.name)
        assertEquals(setOf(0, 9), result.catchEntries.map { it.speciesId }.toSet())
        assertFalse(result.catchEntries.any { it.speciesId == 205 })
    }

    @Test fun dexFlagsDistinguishSharedSeenAndCaught() {
        val f = Fixture().apply { pool(0, (0..4).toList(), (0..4).toList()) }
        for (id in 0..4) f.dex[id] = id.toByte()
        val result = f.sample()
        for (id in 0..4) {
            val row = result.catchEntries.single { it.speciesId == id }
            assertEquals(PinballDexStatus.entries[id], row.dexStatus)
            assertEquals(id == 4, row.caught)
        }
        assertEquals(4, result.missingCatchCount)
        assertTrue(result.catchEntries.last().caught)
    }

    @Test fun invalidDexStatusDoesNotPretendMissing() {
        for (flag in listOf(5, 127, 255)) {
            val f = Fixture(); f.dex[204] = flag.toByte()
            assertUnavailable(f.sample())
        }
    }

    @Test fun twoAndThreeArrowsAreDifferentPoolsNotAnUpgradeUnion() {
        val f = Fixture().apply { pool(0, listOf(0, 1), listOf(1, 2)); byte(0x72F, 3) }
        val rows = f.sample().catchEntries.associateBy { it.speciesId }
        assertTrue(rows.getValue(0).twoArrows); assertFalse(rows.getValue(0).threeArrows)
        assertTrue(rows.getValue(1).twoArrows); assertTrue(rows.getValue(1).threeArrows)
        assertFalse(rows.getValue(2).twoArrows); assertTrue(rows.getValue(2).threeArrows)
        assertEquals(3, f.sample().catchArrows)
    }

    @Test fun duplicatePoolSlotsProduceOneChecklistRow() {
        val f = Fixture().apply { pool(0, listOf(0, 0, 9), listOf(0, 9, 9)) }
        assertEquals(2, f.sample().catchEntries.size)
    }

    @Test fun firstCatchRuleIncludesRareEvolvingSpecies() {
        val f = Fixture().apply { pool(0, listOf(0, 2, 59, 132), listOf(0)) }
        var rows = f.sample().catchEntries.associateBy { it.speciesId }
        assertFalse(rows.getValue(0).firstCatchRequired)
        for (id in listOf(2, 59, 132)) assertTrue(rows.getValue(id).firstCatchRequired)
        f.word(0x5F0, 1)
        rows = f.sample().catchEntries.associateBy { it.speciesId }
        assertFalse(rows.values.any { it.firstCatchRequired })
    }

    @Test fun previousTargetIsFlaggedNotRemovedOrPredicted() {
        val f = Fixture().apply { word(0x59C, 0) }
        val rows = f.sample().catchEntries
        assertTrue(rows.single { it.speciesId == 0 }.excludedPreviousTarget)
        assertFalse(rows.single { it.speciesId == 9 }.excludedPreviousTarget)
        f.word(0x59C, 205)
        assertFalse(f.sample().catchEntries.any { it.excludedPreviousTarget })
        for (invalid in listOf(206, 0xFFFF)) { f.word(0x59C, invalid); assertUnavailable(f.sample()) }
    }

    @Test fun onlyTheRunPartyIsEligibleNotTheWholeCaughtDex() {
        val f = Fixture().apply { dex.fill(4); party(0) }
        val result = f.sample()
        assertEquals(listOf(0), result.evolutions.map { it.speciesId })
        assertEquals(listOf(1), result.evolutions.single().targets.map { it.speciesId })
        assertTrue(result.evolutions.single().targets.single().caught)
    }

    @Test fun duplicatePartyMembersKeepTheirSlots() {
        val result = Fixture().apply { party(0, 0, 9) }.sample()
        assertEquals(listOf(0, 1, 2), result.evolutions.map { it.partySlot })
        assertEquals(listOf(0, 0, 9), result.evolutions.map { it.speciesId })
    }

    @Test fun maximumPartyIsAcceptedAndStaleUnusedSlotsIgnored() {
        val f = Fixture().apply { party(*IntArray(16) { 0 }) }
        assertEquals(16, f.sample().evolutions.size)
        f.byte(0x281, 0); f.byte(0x270, 255)
        assertEquals(emptyList<PinballEvolutionEntry>(), f.sample().evolutions)
    }

    @Test fun invalidPartySizeOrMemberFailsClosed() {
        for (size in listOf(-1, 17)) assertUnavailable(Fixture().apply { byte(0x281, size) }.sample())
        for (id in listOf(2, 205, 255)) assertUnavailable(Fixture().apply { party(id) }.sample())
        assertUnavailable(Fixture().apply { party(0); mon(0, "TREECKO", 1, 0) }.sample())
    }

    @Test fun ordinaryEvolutionUsesRomMetadataWithOneStepOnly() {
        val result = Fixture().apply { party(0); mon(0, "CUSTOMMON", 9) }.sample().evolutions.single()
        assertEquals("CUSTOMMON", result.name)
        assertEquals(PinballEvolutionOutcome.FIXED, result.outcome)
        assertEquals(listOf(9), result.targets.map { it.speciesId })
    }

    @Test fun branchingEvolutionsNeverPredictTheFrameDependentChoice() {
        val f = Fixture().apply { party(13, 175); dex[14] = 4; dex[177] = 4 }
        val rows = f.sample().evolutions
        assertEquals(listOf(14, 16), rows[0].targets.map { it.speciesId })
        assertEquals(listOf(176, 177), rows[1].targets.map { it.speciesId })
        assertTrue(rows.all { it.outcome == PinballEvolutionOutcome.ONE_OF })
        assertEquals(listOf(true, false), rows[0].targets.map { it.caught })
        assertEquals(listOf(false, true), rows[1].targets.map { it.caught })
    }

    @Test fun gloomEvolutionDependsOnCurrentTable() {
        val f = Fixture().apply { party(88) }
        assertEquals(89, f.sample().evolutions.single().targets.single().speciesId)
        f.main[4] = 1; f.byte(0x35, 1)
        assertEquals(90, f.sample().evolutions.single().targets.single().speciesId)
    }

    @Test fun nincadaRegistersBothOutcomes() {
        val row = Fixture().apply { party(41) }.sample().evolutions.single()
        assertEquals(PinballEvolutionOutcome.BOTH, row.outcome)
        assertEquals(listOf(42, 43), row.targets.map { it.speciesId })
    }

    @Test fun readinessRequiresActualPartyAndThreeEvolutionArrows() {
        val f = Fixture().apply { byte(0x72E, 3) }
        assertEquals(false, f.sample().evolutionReady)
        f.party(0); assertEquals(true, f.sample().evolutionReady)
        f.byte(0x72E, 2); assertEquals(false, f.sample().evolutionReady)
        f.byte(0x72E, 4); assertNull(f.sample().evolutionReady)
    }

    @Test fun modeStoredArrowsAreNotMistakenForCurrentReadiness() {
        for (mode in 2..8) {
            val result = Fixture().apply { byte(0x13, mode); byte(0x72E, 3); byte(0x72F, 3); party(0) }.sample()
            assertEquals(PinballPhase.PLAYING, result.phase)
            assertNull(result.catchArrows); assertNull(result.evolutionReady)
        }
    }

    @Test fun specialOverridesAreMarkedButDoNotInjectExtraPredictions() {
        val f = Fixture(); val normal = f.sample().catchEntries
        f.main[7] = 1
        assertTrue(f.sample().specialEncounterPending); assertEquals(normal, f.sample().catchEntries)
        f.main[7] = 0; f.byte(0x12B, 1)
        assertTrue(f.sample().specialEncounterPending); assertEquals(normal, f.sample().catchEntries)
    }

    @Test fun allAreaTablePairsAreChecked() {
        val fields = intArrayOf(0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1)
        for (area in fields.indices) {
            val f = Fixture().apply { main[4] = fields[area].toByte(); byte(0x35, area) }
            assertEquals("area $area", PinballPhase.PLAYING, f.sample().phase)
            f.main[4] = (1 - fields[area]).toByte()
            assertUnavailable(f.sample())
        }
        for (area in listOf(-1, 14)) assertUnavailable(Fixture().apply { byte(0x35, area) }.sample())
    }

    @Test fun menusDemoInitializationAndGameOverDoNotReadCatalog() {
        for (state in 0..11) if (state != 2) {
            val f = Fixture().apply { main[2] = state.toByte() }
            assertWaiting(f.sample()); assertEquals(listOf(PinballReader.MAIN), f.reads.map { it.first })
        }
        for (field in listOf(3 to 0, 0x0E to 1, 0x0F to 0x20)) {
            val f = Fixture().apply { main[field.first] = field.second.toByte() }
            assertWaiting(f.sample()); assertEquals(1, f.reads.size)
        }
    }

    @Test fun bonusTablesSuppressReusedMainTableMemory() {
        for (field in 2..7) {
            val f = Fixture().apply { main[4] = field.toByte(); party(0) }
            assertWaiting(f.sample()); assertEquals(1, f.reads.size)
        }
    }

    @Test fun pauseKeepsBrowsingButOtherTransitionsSuppressData() {
        val f = Fixture(); f.main[0x0F] = 2
        assertEquals(PinballPhase.PLAYING, f.sample().phase)
        for (flag in listOf(1, 4, 8, 0x10, 0x40, 0x80, 3)) {
            f.main[0x0F] = flag.toByte(); assertWaiting(f.sample())
        }
        f.main[0x0F] = 0; f.byte(0x13, 0); assertWaiting(f.sample())
        f.byte(0x13, 1)
        for (transition in listOf(0, 2)) { f.byte(0x16, transition); assertWaiting(f.sample()) }
    }

    @Test fun invalidMainPointerOrBoardStateFailsClosed() {
        for ((at, value) in listOf(2 to 12, 4 to 8)) assertUnavailable(Fixture().apply { main[at] = value.toByte() }.sample())
        assertUnavailable(Fixture().apply { pointer += 4 }.sample())
        for ((at, value) in listOf(0x13 to -1, 0x13 to 9, 0x16 to 3, 0x17 to 1))
            assertUnavailable(Fixture().apply { byte(at, value) }.sample())
    }

    @Test fun invalidRomMetadataOrPoolFailsClosed() {
        for ((at, value) in listOf(7 to 0, 0x14 to 8, 0x14 to 12, 0x15 to 206)) {
            val f = Fixture(); f.species[at] = value.toByte(); assertUnavailable(f.sample())
        }
        assertUnavailable(Fixture().apply { mon(0, "") }.sample())
        assertUnavailable(Fixture().apply { pool(0, listOf(206), listOf(0)) }.sample())
        assertUnavailable(Fixture().apply { pool(0, emptyList(), emptyList()) }.sample())
    }

    @Test fun eachShortOrFailedReadIsUnavailableAndNeverKeepsOldRows() {
        val addresses = listOf(PinballReader.MAIN, PinballReader.GAME_POINTER, PinballReader.GAME,
            PinballCollectionReader.DEX, PinballCollectionReader.SPECIES, PinballCollectionReader.WILD_POOLS)
        for (address in addresses) {
            val f = Fixture()
            assertEquals(PinballPhase.PLAYING, f.sample().phase)
            f.shortAt = address; assertUnavailable(f.sample())
            f.shortAt = null; f.throwAt = address; assertUnavailable(f.sample())
        }
    }

    @Test fun freshSamplesReflectDexPartyAndAreaChangesWithoutCache() {
        val f = Fixture().apply { party(0) }
        val first = f.sample()
        f.dex[0] = 4; f.party(9); f.byte(0x35, 2); f.pool(2, listOf(0), listOf(0))
        val second = f.sample()
        assertFalse(first.catchEntries.single { it.speciesId == 0 }.caught)
        assertTrue(second.catchEntries.single().caught)
        assertEquals("Plains", second.areaLabel)
        assertEquals(listOf(9), second.evolutions.map { it.speciesId })
        f.main[2] = 3; assertWaiting(f.sample())
    }

    @Test fun readsOnlyBoundedMemoryAndCurrentAreaRomPool() {
        val f = Fixture().apply { byte(0x35, 2) }
        assertEquals(PinballPhase.PLAYING, f.sample().phase)
        assertEquals(listOf(PinballReader.MAIN to 16, PinballReader.GAME_POINTER to 4,
            PinballReader.GAME to PinballReader.GAME_BYTES, PinballCollectionReader.DEX to 205,
            PinballCollectionReader.SPECIES to 4920, (PinballCollectionReader.WILD_POOLS + 64) to 32), f.reads)
        assertEquals(7017, f.reads.sumOf { it.second })
    }

    private fun assertUnavailable(snapshot: PinballCollectionSnapshot) {
        assertEquals(PinballPhase.UNAVAILABLE, snapshot.phase)
        assertTrue(snapshot.catchEntries.isEmpty()); assertTrue(snapshot.evolutions.isEmpty())
    }
    private fun assertWaiting(snapshot: PinballCollectionSnapshot) {
        assertEquals(PinballPhase.MENU, snapshot.phase)
        assertTrue(snapshot.catchEntries.isEmpty()); assertTrue(snapshot.evolutions.isEmpty())
    }
}
