package check

import com.pokedaisy.app.companion.data.MemoryReader
import com.pokedaisy.app.companion.data.PinballReader
import com.pokedaisy.app.companion.data.PinballRsProfile
import java.io.File

/** Opt-in private capture check. Never copies ROM, RAM, saves or art into the repository. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Pass a private ROM path and a directory containing named ewram.bin capture folders." }
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
        val snapshot = PinballReader.read(object : MemoryReader {
            override fun readCoreMemory(addr: Long, size: Int): ByteArray {
                val offset = addr - PinballReader.GAME
                require(offset >= 0 && size >= 0 && offset + size <= ram.size)
                return ram.copyOfRange(offset.toInt(), offset.toInt() + size)
            }
        })
        val fields = linkedMapOf(
            "phase" to snapshot.phase.toString(), "table" to snapshot.tableLabel,
            "mode" to snapshot.modeLabel, "timer" to snapshot.timerSeconds.toString(),
            "saver" to snapshot.ballSaverSeconds.toString(), "balls" to snapshot.ball.toString(),
            "score" to snapshot.score.toString(), "area" to snapshot.areaLabel.toString(),
            "progress" to snapshot.progress.toString(), "objective" to snapshot.objective,
        )
        println(folder.name + "\t" + fields.entries.joinToString("\t") { "${it.key}=${it.value}" })
        folder.name to fields
    }
    // Optional external assertions, authored from independently observed game screens / state.
    // Three tab-separated columns: capture folder, field name above, expected value (null is literal).
    val expectations = File(root, "expected.tsv")
    if (expectations.isFile) {
        var checked = 0
        expectations.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val parts = line.split('\t', limit = 3)
            require(parts.size == 3) { "Invalid expected.tsv line" }
            val actual = requireNotNull(observed[parts[0]]) { "Missing capture ${parts[0]}" }
            require(actual.containsKey(parts[1])) { "Unknown field ${parts[1]}" }
            check(actual[parts[1]] == parts[2]) {
                "${parts[0]} ${parts[1]}: expected '${parts[2]}', read '${actual[parts[1]]}'"
            }
            checked++
        }
        require(checked > 0) { "expected.tsv contains no assertions" }
        println("PASS: $checked real-capture assertions across ${captures.size} samples")
    } else {
        println("Decoded ${captures.size} real captures; no expected.tsv assertions supplied.")
    }
}
