package check

import com.pokedaisy.app.companion.data.MemoryReader
import com.pokedaisy.app.companion.data.PinballCollectionReader
import com.pokedaisy.app.companion.data.PinballReader
import com.pokedaisy.app.companion.data.PinballRsProfile
import java.io.File

/** Opt-in private capture check. Never copies ROM, RAM, saves or art into the repository. */
fun main(args: Array<String>) {
    require(args.size in 2..3 && (args.size == 2 || args[2] == "--collection-details")) {
        "Pass a private ROM path, named ewram.bin capture directory, and optional --collection-details."
    }
    val collectionDetails = args.size == 3
    val rom = File(args[0]).readBytes()
    val identityReader = object : MemoryReader {
        override fun readCoreMemory(addr: Long, size: Int): ByteArray {
            val offset = addr - 0x08000000L
            require(offset >= 0 && size >= 0 && offset + size <= rom.size)
            return rom.copyOfRange(offset.toInt(), offset.toInt() + size)
        }
    }
    require(rom.size >= 0xC0)
    val code = String(rom, 0xAC, 4, Charsets.US_ASCII)
    val revision = rom[0xBC].toInt() and 0xFF
    val sha1 = PinballRsProfile.sha1(identityReader, rom.size.toLong())
    require(PinballRsProfile.matches(code, revision, sha1)) { "Unsupported ROM identity: $code rev $revision SHA-1 $sha1" }
    println("Verified ROM: $code rev $revision SHA-1 $sha1")

    val root = File(args[1])
    val captures = root.listFiles()?.filter { it.isDirectory && File(it, "ewram.bin").isFile }?.sortedBy { it.name }.orEmpty()
    require(captures.isNotEmpty()) { "No named capture folders with ewram.bin found." }
    val observed = captures.associate { folder ->
        val ram = File(folder, "ewram.bin").readBytes()
        require(ram.size == 0x40000) { "${folder.name}: expected a complete 256 KiB EWRAM capture" }
        val reader = object : MemoryReader {
            override fun readCoreMemory(addr: Long, size: Int): ByteArray {
                if (addr >= 0x08000000L) return identityReader.readCoreMemory(addr, size)
                val offset = addr - PinballReader.GAME
                require(offset >= 0 && size >= 0 && offset + size <= ram.size)
                return ram.copyOfRange(offset.toInt(), offset.toInt() + size)
            }
        }
        val snapshot = PinballReader.read(reader)
        val collection = PinballCollectionReader.read(reader)
        val fields = linkedMapOf(
            "phase" to snapshot.phase.toString(), "table" to snapshot.tableLabel,
            "mode" to snapshot.modeLabel, "timer" to snapshot.timerSeconds.toString(),
            "saver" to snapshot.ballSaverSeconds.toString(), "balls" to snapshot.ball.toString(),
            "score" to snapshot.score.toString(), "area" to snapshot.areaLabel.toString(),
            "progress" to snapshot.progress.toString(), "objective" to snapshot.objective,
            "collection.phase" to collection.phase.toString(),
            "collection.table" to collection.tableLabel,
            "collection.area" to collection.areaLabel.toString(),
            "collection.catchCount" to collection.catchEntries.size.toString(),
            "collection.catchIds" to collection.catchEntries.map { it.speciesId }.sorted().joinToString(","),
            "collection.missingCount" to collection.missingCatchCount.toString(),
            "collection.partySize" to collection.evolutions.size.toString(),
            "collection.partyIds" to collection.evolutions.joinToString(",") { it.speciesId.toString() },
            "collection.empty" to (collection.catchEntries.isEmpty() && collection.evolutions.isEmpty()).toString(),
            "collection.catchArrows" to collection.catchArrows.toString(),
            "collection.evolutionReady" to collection.evolutionReady.toString(),
            "collection.specialEncounterPending" to collection.specialEncounterPending.toString(),
        )
        // Per-entry fields are always available to expected TSVs. Printing them is opt-in.
        // Numeric keys are Pinball species IDs and zero-based party slots, never RPG species IDs.
        collection.catchEntries.forEach { row ->
            val prefix = "collection.catch.${row.speciesId}"
            fields["$prefix.name"] = row.name
            fields["$prefix.status"] = row.dexStatus.toString()
            fields["$prefix.caught"] = row.caught.toString()
            fields["$prefix.twoArrows"] = row.twoArrows.toString()
            fields["$prefix.threeArrows"] = row.threeArrows.toString()
            fields["$prefix.firstCatchRequired"] = row.firstCatchRequired.toString()
            fields["$prefix.excludedPreviousTarget"] = row.excludedPreviousTarget.toString()
        }
        collection.evolutions.forEach { row ->
            val prefix = "collection.evolution.${row.partySlot}"
            fields["$prefix.species"] = row.speciesId.toString()
            fields["$prefix.name"] = row.name
            fields["$prefix.outcome"] = row.outcome.toString()
            fields["$prefix.targets"] = row.targets.joinToString(",") { it.speciesId.toString() }
            row.targets.forEach { target ->
                fields["$prefix.target.${target.speciesId}.name"] = target.name
                fields["$prefix.target.${target.speciesId}.status"] = target.dexStatus.toString()
                fields["$prefix.target.${target.speciesId}.caught"] = target.caught.toString()
            }
        }
        val printable = fields.filterKeys { collectionDetails ||
            !(it.startsWith("collection.catch.") || it.startsWith("collection.evolution.")) }
        println(folder.name + "\t" + printable.entries.joinToString("\t") { "${it.key}=${it.value}" })
        folder.name to fields
    }
    // Optional external assertions, authored from independently observed game screens / state.
    // Three tab-separated columns: capture folder, field name above, expected value (null is literal).
    fun checkExpectations(filename: String, label: String): Boolean {
        val expectations = File(root, filename)
        if (!expectations.isFile) return false
        var checked = 0
        expectations.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val parts = line.split('\t', limit = 3)
            require(parts.size == 3) { "Invalid $filename line" }
            val actual = requireNotNull(observed[parts[0]]) { "Missing capture ${parts[0]}" }
            require(actual.containsKey(parts[1])) { "Unknown field ${parts[1]}" }
            check(actual[parts[1]] == parts[2]) {
                "${parts[0]} ${parts[1]}: expected '${parts[2]}', read '${actual[parts[1]]}'"
            }
            checked++
        }
        require(checked > 0) { "$filename contains no assertions" }
        println("PASS: $checked $label assertions across ${captures.size} samples")
        return true
    }
    if (!checkExpectations("expected.tsv", "real-capture")) {
        println("Decoded ${captures.size} real captures; no expected.tsv assertions supplied.")
    }
    checkExpectations("collection-expected.tsv", "collection")
}
