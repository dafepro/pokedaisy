package com.pokedaisy.app.companion.data

import org.junit.Assert.*
import org.junit.Test

/** Entirely synthetic bytes authored for decoder edge cases. Not captured gameplay or a save/ROM dump. */
class PinballReaderTest {
    private class Fixture : MemoryReader {
        val main = ByteArray(0x10)
        val game = ByteArray(PinballReader.GAME_BYTES)
        var pointer = PinballReader.GAME
        val reads = mutableListOf<Long>()
        init { main[2] = 2; main[3] = 1; game[0x13] = 1; game[0x16] = 1; game[0x30] = 2 }
        fun byte(at: Int, value: Int) { game[at] = value.toByte() }
        fun word(at: Int, value: Int) { byte(at, value); byte(at + 1, value ushr 8) }
        fun dword(at: Int, value: Long) { word(at, value.toInt()); word(at + 2, (value ushr 16).toInt()) }
        fun mode(mode: Int, sub: Int) { byte(0x13, mode); byte(0x17, sub) }
        fun sample() = PinballReader.read(this)
        override fun readCoreMemory(addr: Long, size: Int): ByteArray {
            reads += addr
            return when (addr) {
                PinballReader.MAIN -> main.copyOf()
                PinballReader.GAME -> game.copyOf()
                PinballReader.GAME_POINTER -> ByteArray(4) { (pointer ushr (it * 8)).toByte() }
                else -> error("Unexpected memory read")
            }
        }
    }

    @Test fun identityHashIncludesBytesAtTheRpgGpioHole() {
        val rom = ByteArray(0x100) { it.toByte() }
        val reader = object : MemoryReader {
            override fun readCoreMemory(addr: Long, size: Int) = rom.copyOfRange((addr - 0x08000000L).toInt(), (addr - 0x08000000L).toInt() + size)
        }
        val expected = java.security.MessageDigest.getInstance("SHA-1").digest(rom).joinToString("") { "%02x".format(it) }
        assertEquals(expected, PinballRsProfile.sha1(reader, rom.size.toLong()))
        val masked = rom.copyOf().apply { fill(0, 0xC4, 0xCA) }
        assertNotEquals(java.security.MessageDigest.getInstance("SHA-1").digest(masked).joinToString("") { "%02x".format(it) }, expected)
    }

    @Test fun exactIdentityIsRequired() {
        assertTrue(PinballRsProfile.matches("BPPE", 0, PinballRsProfile.SHA1))
        assertFalse(PinballRsProfile.matches("BPPJ", 0, PinballRsProfile.SHA1))
        assertFalse(PinballRsProfile.matches("BPPE", 1, PinballRsProfile.SHA1))
        assertFalse(PinballRsProfile.matches("BPPE", 0, "0".repeat(40)))
        assertFalse(PinballRsProfile.matches("BPPE", 0, null))
    }
    @Test fun menusAndAttractModeCannotLeakOldGameplay() {
        for (state in 0..11) if (state != 2) {
            val f = Fixture().apply { main[2] = state.toByte(); mode(4, 9); word(0x296, 600) }
            val s = f.sample()
            assertEquals(PinballPhase.MENU, s.phase)
            assertNull(s.timerSeconds); assertNull(s.progress); assertNull(s.score); assertNull(s.ballSaverSeconds)
            assertEquals(listOf(PinballReader.MAIN), f.reads)
        }
    }
    @Test fun gameplayInitializationDoesNotReadStaleState() {
        val f = Fixture().apply { main[3] = 0 }
        assertEquals(PinballPhase.MENU, f.sample().phase)
        assertEquals(listOf(PinballReader.MAIN), f.reads)
    }
    @Test fun exitAndGameOverSuppressLiveData() {
        for (exit in listOf(true, false)) {
            val f = Fixture().apply { if (exit) main[0x0E] = 1 else main[0x0F] = 0x20 }
            val s = f.sample(); assertEquals(PinballPhase.MENU, s.phase); assertNull(s.progress); assertNull(s.score)
        }
    }
    @Test fun invalidPointerFailsClosed() {
        val f = Fixture().apply { pointer += 4 }
        assertEquals(PinballPhase.UNAVAILABLE, f.sample().phase)
        assertFalse(f.reads.contains(PinballReader.GAME))
    }
    @Test fun invalidMainAndTableFailClosed() {
        val f = Fixture(); f.main[2] = 12
        assertEquals(PinballPhase.UNAVAILABLE, f.sample().phase)
        f.main[2] = 2; f.main[4] = 8
        assertEquals(PinballPhase.UNAVAILABLE, f.sample().phase)
    }
    @Test fun shortReadAndReadFailureAreUnavailable() {
        for (size in listOf(0, 3, 15)) assertEquals(PinballPhase.UNAVAILABLE, PinballReader.read(object : MemoryReader {
            override fun readCoreMemory(addr: Long, wanted: Int) = ByteArray(size)
        }).phase)
        assertEquals(PinballPhase.UNAVAILABLE, PinballReader.read(object : MemoryReader {
            override fun readCoreMemory(addr: Long, size: Int): ByteArray = error("core stopped")
        }).phase)
    }
    @Test fun invalidModeSubstateAndTransitionFailClosed() {
        for ((at, value) in listOf(0x13 to -1, 0x13 to 9, 0x16 to 3, 0x17 to 1)) {
            val f = Fixture().apply { byte(at, value) }
            assertEquals(PinballPhase.UNAVAILABLE, f.sample().phase)
        }
    }
    @Test fun bothMainTablesAreRecognized() {
        val f = Fixture(); assertEquals("RUBY", f.sample().tableLabel)
        f.main[4] = 1; assertEquals("SAPPHIRE", f.sample().tableLabel)
    }
    @Test fun bonusTablesNeverUseMainTableModesOrSaver() {
        for (field in 2..7) {
            val f = Fixture().apply { main[4] = field.toByte(); mode(4, 5); byte(0x16, 0); byte(0x625, 3); word(0x724, 300) }
            val s = f.sample()
            assertEquals("BONUS STAGE", s.modeLabel); assertNull(s.progress); assertNull(s.ballSaverSeconds)
            assertNull(s.ball); assertNull(s.areaLabel); assertTrue(s.objective.contains("bonus stage"))
        }
    }
    @Test fun bonusBoardDoesNotInterpretUnusedMainBoardStateFields() {
        val f = Fixture().apply { main[4] = 2; mode(-1, -1); byte(0x16, -1) }
        assertEquals(PinballPhase.PLAYING, f.sample().phase)
        assertTrue(f.sample().objective.contains("bonus stage"))
    }
    @Test fun timerMatchesGameTruncationAndDoesNotExtrapolate() {
        val f = Fixture().apply { byte(0x294, 2) }
        for ((ticks, seconds) in listOf(0 to 0, 1 to 0, 59 to 0, 60 to 1, 119 to 1, 120 to 2, 65535 to 1092)) {
            f.word(0x296, ticks); assertEquals(seconds, f.sample().timerSeconds)
        }
        f.byte(0x294, 1); assertEquals(1092, f.sample().timerSeconds)
        for (mode in listOf(0, 3, 4, -1)) { f.byte(0x294, mode); assertNull(f.sample().timerSeconds) }
    }
    @Test fun saverCounterIsAuthoritativeEvenWhenLampIsOff() {
        val f = Fixture().apply { byte(0x726, 0) }
        for ((ticks, seconds) in listOf(0 to 0, 1 to 1, 59 to 1, 60 to 1, 61 to 2, 600 to 10)) {
            f.word(0x724, ticks); assertEquals(seconds, f.sample().ballSaverSeconds)
        }
    }
    @Test fun scoreUsesBaseOneHundredMillionAndLongArithmetic() {
        val f = Fixture().apply { dword(0x44, 99_999_999); dword(0x48, 9999) }
        assertEquals(999_999_999_999L, f.sample().score)
        f.dword(0x44, 100_000_000); assertNull(f.sample().score)
        f.dword(0x44, 0); f.dword(0x48, 10000); assertNull(f.sample().score)
    }
    @Test fun ballsLeftIncludesActiveAndPreservesExtraBalls() {
        val f = Fixture(); assertEquals(3, f.sample().ball)
        f.byte(0x30, 0); assertEquals(1, f.sample().ball)
        f.byte(0x30, 5); assertEquals(6, f.sample().ball)
        f.byte(0x30, -1); assertNull(f.sample().ball)
    }
    @Test fun introDoesNotClaimAnAreaBeforeRouletteFinishes() {
        val f = Fixture().apply { mode(0, 4); byte(0x35, 9) }
        assertNull(f.sample().areaLabel)
        f.mode(1, 0); assertEquals("Volcano", f.sample().areaLabel)
        f.byte(0x35, -1); assertNull(f.sample().areaLabel)
    }
    @Test fun catchArrowsNeedTwoAndUseCorrectTableHole() {
        val f = Fixture().apply { byte(0x72F, 1) }
        assertTrue(f.sample().objective.startsWith("Light"))
        f.byte(0x72F, 2); assertTrue(f.sample().objective.contains("Sharpedo"))
        f.main[4] = 1; assertTrue(f.sample().objective.contains("Wailmer"))
    }
    @Test fun evolutionReadinessAlsoRequiresAnEligibleParty() {
        val f = Fixture().apply { byte(0x72E, 3) }
        assertTrue(f.sample().objective.startsWith("Light"))
        f.byte(0x281, 1); assertTrue(f.sample().objective.startsWith("Enter the Evolution"))
        f.byte(0x72F, 2); assertTrue(f.sample().objective.startsWith("Catch and Evolution ready"))
    }
    @Test fun normalBonusProgressIsBounded() {
        val f = Fixture().apply { byte(0x723, 2) }; assertEquals("Bonus progress 2/3", f.sample().progress)
        f.byte(0x723, -1); assertNull(f.sample().progress)
    }
    @Test fun catchRevealUsesTriangularAccumulator() {
        val f = Fixture().apply { mode(4, 5) }
        for ((raw, hits) in listOf(0 to 0, 1 to 1, 3 to 2, 6 to 3)) {
            f.byte(0x625, raw); assertEquals("Bumper hits $hits/3", f.sample().progress)
        }
        assertTrue(f.sample().objective.startsWith("Revealing"))
        f.byte(0x625, 2); assertNull(f.sample().progress)
    }
    @Test fun catchDoesNotLeakProgressIntoPreparationOrAppearance() {
        val f = Fixture().apply { byte(0x625, 6); byte(0x5A5, 3) }
        for (sub in 0..8) if (sub != 5) { f.mode(4, sub); assertNull(f.sample().progress) }
    }
    @Test fun threeCreatureHitsEnterCaptureAnimation() {
        val f = Fixture().apply { mode(4, 9); byte(0x5A5, 2) }
        assertEquals("Hits 2/3", f.sample().progress)
        f.byte(0x5A5, 3); assertEquals("Capture animation.", f.sample().objective)
        f.byte(0x5A5, 0); f.byte(0x5A4, 2); assertEquals("Hits 3/3", f.sample().progress)
    }
    @Test fun specialEncounterStopsCoachingHitsDuringItsCaptureCutscene() {
        val f = Fixture().apply { mode(8, 3); byte(0x5A5, 2) }
        assertEquals("Hit Jirachi to catch it.", f.sample().objective)
        f.byte(0x5A5, 3); assertEquals("Capture animation.", f.sample().objective)
        f.byte(0x5A5, 0); f.byte(0x5A4, 2)
        assertEquals("Capture animation.", f.sample().objective); assertEquals("Hits 3/3", f.sample().progress)
    }
    @Test fun catchEndingIsNotAssumedSuccessful() {
        val f = Fixture().apply { byte(0x5A5, 3) }
        for (sub in 10..12) { f.mode(4, sub); assertEquals("Catch mode ending.", f.sample().objective); assertNull(f.sample().progress) }
    }
    @Test fun evolutionCounterResetStillShowsThreeItemsAndCenterHole() {
        val f = Fixture().apply { mode(6, 2); byte(0x26B, 2) }
        assertEquals("Items 2/3", f.sample().progress)
        f.mode(6, 3); f.byte(0x26B, 0)
        assertEquals("Items 3/3", f.sample().progress); assertTrue(f.sample().objective.contains("center hole"))
        f.mode(6, 8); assertNull(f.sample().progress)
    }
    @Test fun pauseBannersAndTransitionsClearCoachingProgress() {
        val f = Fixture().apply { mode(4, 9); byte(0x5A5, 2) }
        for (flag in listOf(1, 2, 4, 8, 16, 64, 128)) {
            f.main[0x0F] = flag.toByte(); val s = f.sample()
            assertNull(s.progress); assertFalse(s.objective.startsWith("Hit the Pokémon"))
        }
        f.main[0x0F] = 0
        for (phase in listOf(0, 2)) { f.byte(0x16, phase); assertNull(f.sample().progress); assertTrue(f.sample().objective.startsWith("Wait")) }
    }
    @Test fun everyKnownModeSubstateProducesSafeGuidance() {
        val f = Fixture()
        for ((mode, max) in listOf(6, 0, 7, 8, 12, 8, 10, 7, 6).withIndex()) {
            for (sub in 0..max) {
                f.mode(mode, sub); val s = f.sample()
                assertEquals("mode=$mode sub=$sub", PinballPhase.PLAYING, s.phase)
                assertTrue(s.objective.isNotBlank())
            }
        }
    }
    @Test fun readFailureAfterGoodSampleDoesNotKeepStaleObjective() {
        val f = Fixture().apply { mode(4, 9) }
        assertTrue(f.sample().objective.startsWith("Hit"))
        f.pointer = 0
        val s = f.sample(); assertEquals(PinballPhase.UNAVAILABLE, s.phase); assertNull(s.timerSeconds); assertNull(s.progress)
    }
    @Test fun pinballDoesNotEnterTheRpgSnapshotCacheOrHudReadiness() {
        val dir = java.nio.file.Files.createTempDirectory("pinball-cache-test").toFile()
        try {
            val state = java.io.File(dir, "slot")
            val stale = SnapshotCache.fileFor(state)
            stale.writeText("stale RPG companion data")
            val view = SnapshotView(connected = true, pinball = Fixture().sample())
            assertFalse(view.hasData)
            SnapshotCache.write(state, view)
            assertFalse(stale.exists())
            assertNull(SnapshotCache.read(state))
        } finally { dir.deleteRecursively() }
    }

}
