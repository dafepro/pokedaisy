package com.pokedaisy.app.companion.data

/** Read-only area catalog and run-party planning for the exact [PinballRsProfile].
 * Call on the emulator thread between frames, only after the caller has verified ROM identity.
 * All names, evolution metadata and encounter pools are read from the player's own ROM.
 * Nothing is cached across ROMs, reset, state loads, menus or failed samples.
 *
 * Primary evidence: pret/pokepinballrs @ 33ad42e8f2afca891831f03179820b96a3fdef6a:
 * include/main.h, include/global.h, include/variables.h, data/mon_locations.inc,
 * src/data.c + src/data/species.h, src/main_board_catch_hatch_picker.c.
 * Exact private-ROM verification confirms all 224 slots and 205 24-byte species records.
 * This is independently written decoding/rule code; no game source or data is bundled.
 */
object PinballCollectionReader {
    const val SPECIES_COUNT = 205
    const val DEX = PinballReader.MAIN + 0x74
    const val WILD_POOLS = 0x08055A84L
    const val SPECIES = 0x086A3700L
    const val SPECIES_BYTES = 24

    fun read(reader: MemoryReader): PinballCollectionSnapshot = try {
        val main = reader.exact(PinballReader.MAIN, 0x10)
        when {
            main.u8(2) !in 0..11 -> unavailable("Unrecognized game state.")
            main.u8(2) != 2 || main.u8(3) != 1 || main.u8(0x0E) != 0 || main.u8(0x0F) and 0x20 != 0 ->
                waiting("Start or resume a game to see your current area and collection.")
            main.u8(4) !in 0..7 -> unavailable("Unrecognized table.")
            main.u8(4) >= 2 -> waiting("Return to the main table to view its area collection.")
            // Pause leaves the catalog useful; a reset/banner/ball-end can contain mixed old/new state.
            main.u8(0x0F) and 0xFD != 0 -> waiting("Wait for the on-screen sequence to finish.")
            reader.exact(PinballReader.GAME_POINTER, 4).u32(0) != PinballReader.GAME ->
                unavailable("The gameplay memory pointer is not ready.")
            else -> decode(reader, main, reader.exact(PinballReader.GAME, PinballReader.GAME_BYTES))
        }
    } catch (_: Exception) {
        unavailable("Collection data could not be read. Waiting for a fresh sample.")
    }

    private fun decode(reader: MemoryReader, main: ByteArray, game: ByteArray): PinballCollectionSnapshot {
        val field = main.u8(4)
        val mode = game.s8(0x13)
        val transition = game.s8(0x16)
        val sub = game.s8(0x17)
        if (mode !in 0..8 || transition !in 0..2) return unavailable("Unrecognized board state.")
        if (mode == 0 || transition != 1) return waiting("Choose an area and wait for the mode to begin.")
        if (sub !in 0..MAX_SUBSTATE[mode]) return unavailable("Unrecognized mode phase.")
        val area = game.s8(0x35)
        if (area !in AREAS.indices || AREA_FIELDS[area] != field) return unavailable("Unrecognized current area.")
        val dex = reader.exact(DEX, SPECIES_COUNT)
        // Values 2/3 are shared entries, not captures. Unknown flags must not invent missing catches.
        require(dex.all { it.toInt() in 0..4 }) { "Unrecognized Pokedex status" }
        val species = decodeSpecies(reader.exact(SPECIES, SPECIES_COUNT * SPECIES_BYTES))
        val pool = reader.exact(WILD_POOLS + area * 32, 32)
        val two = (0 until 8).map { pool.u16(it * 2) }
        val three = (8 until 16).map { pool.u16(it * 2) }
        require((two + three).all { it in 0..SPECIES_COUNT }) { "Unrecognized encounter species" }
        val firstCatch = game.u16(0x5F0) == 0
        val previous = game.u16(0x59C)
        require(previous in 0..SPECIES_COUNT) { "Unrecognized previous species" }
        val catches = (two + three).filter { it < SPECIES_COUNT }.distinct().map { id ->
            PinballCatchEntry(
                speciesId = id,
                name = species[id].name,
                dexStatus = PinballDexStatus.entries[dex.u8(id)],
                twoArrows = id in two,
                threeArrows = id in three,
                firstCatchRequired = firstCatch && (id in RARE_SPECIES || species[id].evolutionTarget == SPECIES_COUNT),
                excludedPreviousTarget = id == previous,
            )
        }.sortedWith(compareBy<PinballCatchEntry> { it.caught }.thenBy { it.name })
        require(catches.isNotEmpty()) { "Empty encounter pools" }
        val partySize = game.s8(0x281)
        require(partySize in 0..16) { "Unrecognized evolution party" }
        val evolutions = (0 until partySize).map { slot ->
            val id = game.u8(0x270 + slot)
            require(id < SPECIES_COUNT && species[id].evolutionMethod != 0 && species[id].evolutionTarget < SPECIES_COUNT) {
                "Unrecognized evolution party member"
            }
            val targets = when (id) {
                13 -> listOf(14, 16) // Wurmple: one form, chosen at evolution time.
                88 -> listOf(if (field == 0) 89 else 90) // Gloom: table-specific.
                175 -> listOf(176, 177) // Clamperl: one form, chosen at evolution time.
                41 -> listOf(42, 43) // Nincada: both are registered on success.
                else -> listOf(species[id].evolutionTarget)
            }.map { target -> PinballCollectionSpecies(target, species[target].name, PinballDexStatus.entries[dex.u8(target)]) }
            PinballEvolutionEntry(slot, id, species[id].name, targets,
                when (id) { 13, 175 -> PinballEvolutionOutcome.ONE_OF; 41 -> PinballEvolutionOutcome.BOTH; else -> PinballEvolutionOutcome.FIXED })
        }
        val arrows = game.s8(0x72F).takeIf { mode == 1 && it in 0..3 }
        val evoArrows = game.s8(0x72E).takeIf { mode == 1 && it in 0..3 }
        return PinballCollectionSnapshot(
            phase = PinballPhase.PLAYING,
            tableLabel = if (field == 0) "RUBY" else "SAPPHIRE",
            areaLabel = AREAS[area],
            catchEntries = catches,
            evolutions = evolutions,
            catchArrows = arrows,
            evolutionReady = evoArrows?.let { it == 3 && evolutions.isNotEmpty() },
            specialEncounterPending = main.u8(7) != 0 || game.s8(0x12B) != 0,
        )
    }

    private data class Species(val name: String, val evolutionMethod: Int, val evolutionTarget: Int)

    private fun decodeSpecies(bytes: ByteArray): List<Species> = (0 until SPECIES_COUNT).map { id ->
        val at = id * SPECIES_BYTES
        val nameBytes = bytes.copyOfRange(at + 7, at + 17)
        require(nameBytes.all { it.toInt() in 32..90 }) { "Unrecognized species name" }
        val name = String(nameBytes, Charsets.US_ASCII).trimEnd()
        val method = bytes.u8(at + 0x14)
        val target = bytes.u8(at + 0x15)
        require(name.isNotBlank() && method in EVOLUTION_METHODS && target in 0..SPECIES_COUNT &&
            (method == 0) == (target == SPECIES_COUNT)) { "Unrecognized species metadata" }
        Species(name, method, target)
    }

    private fun waiting(detail: String) = PinballCollectionSnapshot(PinballPhase.MENU, detail = detail)
    private fun unavailable(detail: String) = PinballCollectionSnapshot(PinballPhase.UNAVAILABLE, detail = detail)
    private fun MemoryReader.exact(address: Long, size: Int) = readCoreMemory(address, size).also { require(it.size == size) }
    private fun ByteArray.u8(at: Int) = this[at].toInt() and 0xFF
    private fun ByteArray.s8(at: Int) = this[at].toInt()
    private fun ByteArray.u16(at: Int) = u8(at) or (u8(at + 1) shl 8)
    private fun ByteArray.u32(at: Int) = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

    private val MAX_SUBSTATE = intArrayOf(6, 0, 7, 8, 12, 8, 10, 7, 6)
    private val AREA_FIELDS = intArrayOf(0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1)
    private val AREAS = listOf("Forest", "Forest", "Plains", "Plains", "Ocean", "Ocean", "Cave", "Cave", "Safari Zone", "Volcano", "Lake", "Wilderness", "Ruins", "Ruins")
    private val EVOLUTION_METHODS = setOf(0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11)
    private val RARE_SPECIES = setOf(59, 114, 132, 134, 139, 141, 144, 151, 160)
}
