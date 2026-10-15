package com.pokedaisy.app.companion.data

/** Exact, unmodified English BPPE revision 0 only. Header matching alone is unsafe.
 * Evidence: pret/pokepinballrs @ 33ad42e8f2afca891831f03179820b96a3fdef6a, README + Makefile.
 * No ROM, game art or game source is bundled. See docs/PINBALL.md for mapping and validation limits.
 */
object PinballRsProfile {
    const val CODE = "BPPE"
    const val REVISION = 0
    const val SHA1 = "9fec81ce2c5df589e0371a0bf2f92a5fe8db730b"
    const val TITLE = "Pokémon Pinball: Ruby & Sapphire"

    /** Raw cartridge bytes: Pinball has no RPG RTC hole to zero before hashing. */
    internal fun sha1(reader: MemoryReader, size: Long): String {
        require(size in 0xC0L..0x2000000L)
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        var offset = 0L
        while (offset < size) {
            val length = minOf(1 shl 20, (size - offset).toInt())
            val bytes = reader.readCoreMemory(0x08000000L + offset, length)
            require(bytes.size == length) { "Incomplete ROM read" }
            digest.update(bytes)
            offset += length
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(code: String, revision: Int, sha1: String?): Boolean =
        code == CODE && revision == REVISION && sha1 == SHA1
}

/** Small, side-effect-free reads between emulated frames. Never changes physics, inputs or saves.
 * All offsets describe the exact profile above; the caller must verify it before calling [read].
 * This is a source-mapped experimental reader, not a claim of live-ROM validation.
 */
object PinballReader {
    const val GAME = 0x02000000L
    const val MAIN = 0x0200B0C0L
    const val GAME_POINTER = 0x020314E0L
    const val GAME_BYTES = 0x730

    fun read(reader: MemoryReader): PinballSnapshot = try {
        decode(reader)
    } catch (_: Exception) {
        unavailable("Live memory could not be read. Waiting for a fresh sample.")
    }

    private fun unavailable(detail: String) = PinballSnapshot(
        phase = PinballPhase.UNAVAILABLE, modeLabel = "WAITING",
        objective = "Follow the game screen while live guidance is unavailable.", detail = detail,
    )

    private fun decode(reader: MemoryReader): PinballSnapshot {
        val main = reader.exact(MAIN, 0x10)
        val mainState = main.u8(2)
        if (mainState !in 0..11) return unavailable("Unrecognized game state.")
        // STATE_GAME_IDLE (3) is attract/demo playback. Old RAM can otherwise look like a live run.
        if (mainState != 2 || main.u8(3) != 1 || main.u8(0x0E) != 0 || main.u8(0x0F) and 0x20 != 0) {
            return PinballSnapshot(PinballPhase.MENU, modeLabel = if (mainState == 3) "DEMO" else "MENU",
                objective = "Start or resume a game on either table.",
                detail = "Live objectives appear during your game. Menus and demo play are excluded.")
        }
        if (reader.exact(GAME_POINTER, 4).u32(0) != GAME) return unavailable("The gameplay memory pointer is not ready.")
        val field = main.u8(4)
        if (field !in 0..7) return unavailable("Unrecognized table.")
        val game = reader.exact(GAME, GAME_BYTES)
        val mode = game.s8(0x13)
        val transition = game.s8(0x16)
        val sub = game.s8(0x17)
        if (transition !in 0..2 || mode !in 0..8) return unavailable("Unrecognized board state.")
        val scoreLow = game.u32(0x44)
        val scoreHigh = game.u32(0x48)
        val score = if (scoreLow < 100_000_000L && scoreHigh <= 9999) scoreHigh * 100_000_000L + scoreLow else null
        val timerMode = game.s8(0x294)
        // The game HUD truncates ticks / 60. Do not extrapolate wall-clock time through pause or slomo.
        val timer = if (timerMode == 1 || timerMode == 2) game.u16(0x296) / 60 else null
        val mainTable = field < 2
        val saverTicks = game.u16(0x724)
        val flags = main.u8(0x0F)
        var result = PinballSnapshot(
            phase = PinballPhase.PLAYING,
            tableLabel = TABLES[field],
            modeLabel = if (mainTable) MODES[mode] else "BONUS STAGE",
            timerSeconds = timer,
            objective = "Follow the bonus stage objective on the game screen.",
            // Round UP here: a positive saver counter must never display OFF. The lamp can blink off.
            ballSaverSeconds = if (mainTable) (saverTicks + 59) / 60 else null,
            // numLives is RESERVES. Extra balls mean it cannot be turned into a current-ball ordinal.
            ball = if (mainTable) game.s8(0x30).takeIf { it >= 0 }?.plus(1) else null,
            score = score,
            areaLabel = if (mainTable && mode != 0) AREAS.getOrNull(game.s8(0x35)) else null,
        )
        // Pauses, banners and resets freeze or interrupt the mode. Never keep a stale target/progress.
        if (flags != 0) return result.copy(
            modeLabel = if (flags and 2 != 0) "PAUSED" else "TRANSITION",
            objective = when {
                flags and 2 != 0 -> "Resume the game when you're ready."
                flags and 8 != 0 -> "Ball saver is returning the ball."
                flags and 0x10 != 0 -> "Ball ended. Wait for the next ball."
                else -> "Wait for the on-screen sequence to finish."
            },
        )
        if (transition != 1) return result.copy(objective = "Wait for the new mode to begin.")
        // Bonus tables reuse mode/substate IDs with different meanings. Do not apply main-table advice.
        if (!mainTable) return result
        if (sub !in 0..MAX_SUBSTATE[mode]) return unavailable("Unrecognized mode phase.")
        val advice = when (mode) {
            0 -> "Launch the ball and choose an area." to null
            1 -> normal(game, field)
            2 -> (if (game.s8(0x25) == 4) "Bonus entry sequence." else "Shoot the center hole for the bonus roulette.") to null
            3 -> (if (game.s8(0x25) == 4) "Bonus entry sequence." else "Shoot the center hole to enter the bonus stage.") to null
            4 -> catchAdvice(game, sub)
            5 -> (if (sub == 3) "Hit the hatched Pokémon to catch it." else "Hatch sequence in progress.") to null
            6 -> evolution(game, sub)
            7 -> when (sub) {
                0, 1 -> "Hit a lit travel lane." to null
                2, 3 -> "Shoot the center hole to travel." to null
                4, 5 -> "Travel sequence in progress." to null
                else -> "Travel mode ending." to null
            }
            else -> if (sub == 3) "Hit Jirachi to catch it." to hits(game) else "Special encounter sequence in progress." to null
        }
        result = result.copy(objective = advice.first, progress = advice.second)
        return result
    }

    private fun normal(game: ByteArray, field: Int): Pair<String, String?> {
        val catchArrows = game.s8(0x72F).takeIf { it in 0..3 }
        val evo = game.s8(0x72E).takeIf { it in 0..3 }
        val party = game.s8(0x281).takeIf { it in 0..16 }
        val catchReady = catchArrows != null && catchArrows >= 2
        val evoReady = evo == 3 && party != null && party > 0
        val objective = when {
            catchReady && evoReady -> "Catch and Evolution ready. Choose a mode."
            catchReady -> "Shoot ${if (field == 0) "Sharpedo" else "Wailmer"} to start Catch mode."
            evoReady -> "Enter the Evolution hole to choose a Pokémon."
            else -> "Light Catch or Evolution arrows."
        }
        val progress = game.s8(0x723).takeIf { it in 0..3 }?.let { "Bonus progress $it/3" }
        return objective to progress
    }

    private fun catchAdvice(game: ByteArray, sub: Int): Pair<String, String?> = when (sub) {
        in 0..4 -> "Preparing Catch mode." to null
        5 -> {
            // The reveal accumulator is triangular (0,1,3,6), not six required bumper hits.
            val hits = when (game.s8(0x625)) { 0 -> 0; 1 -> 1; 3 -> 2; 6 -> 3; else -> null }
            (if (hits == 3) "Revealing the Pokémon." else "Hit the bumpers to reveal the Pokémon.") to
                hits?.let { "Bumper hits $it/3" }
        }
        in 6..8 -> "Pokémon appearing." to null
        9 -> if (game.u8(0x5A4) == 2 || game.u8(0x5A5) == 3) "Capture animation." to "Hits 3/3"
            else "Hit the Pokémon to catch it." to hits(game)
        // Timeout takes the same ending path as success; do not declare a catch here.
        else -> "Catch mode ending." to null
    }

    private fun hits(game: ByteArray): String? = game.u8(0x5A5).takeIf { it in 0..3 }?.let { "Hits $it/3" }

    private fun evolution(game: ByteArray, sub: Int): Pair<String, String?> = when (sub) {
        0 -> "Preparing Evolution mode." to null
        1, 2 -> "Collect the evolution item." to game.s8(0x26B).takeIf { it in 0..2 }?.let { "Items $it/3" }
        // The counter resets after item three; the substate is the authority from this point on.
        3, 4 -> "Shoot the center hole to evolve." to "Items 3/3"
        in 5..7 -> "Evolution sequence in progress." to "Items 3/3"
        else -> "Evolution mode ending." to null
    }

    private fun MemoryReader.exact(address: Long, size: Int): ByteArray = readCoreMemory(address, size).also {
        require(it.size == size) { "Incomplete memory sample" }
    }
    private fun ByteArray.u8(at: Int) = this[at].toInt() and 0xFF
    private fun ByteArray.s8(at: Int) = this[at].toInt()
    private fun ByteArray.u16(at: Int) = u8(at) or (u8(at + 1) shl 8)
    private fun ByteArray.u32(at: Int) = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

    private val TABLES = listOf("RUBY", "SAPPHIRE", "DUSCLOPS", "KECLEON", "KYOGRE", "GROUDON", "RAYQUAZA", "SPHEAL")
    private val MODES = listOf("LAUNCH", "FREE PLAY", "BONUS ROULETTE", "BONUS ENTRY", "CATCH", "HATCH", "EVOLVE", "TRAVEL", "JIRACHI")
    private val MAX_SUBSTATE = intArrayOf(6, 0, 7, 8, 12, 8, 10, 7, 6)
    private val AREAS = listOf("Forest", "Forest", "Plains", "Plains", "Ocean", "Ocean", "Cave", "Cave", "Safari Zone", "Volcano", "Lake", "Wilderness", "Ruins", "Ruins")
}
