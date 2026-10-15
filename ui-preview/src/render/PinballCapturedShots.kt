@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package render

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.pokedaisy.app.companion.data.*
import java.io.File

/** Opt-in UI render driven by a real private RAM capture. This is not a live Android/device test. */
fun pinballCapturedShots(): List<Shot> {
    val romPath = System.getProperty("pinballRom").orEmpty()
    val capturePath = System.getProperty("pinballCapture").orEmpty()
    if (romPath.isBlank() && capturePath.isBlank()) return emptyList()
    require(romPath.isNotBlank() && capturePath.isNotBlank()) { "Supply both private Pinball ROM and EWRAM capture paths." }
    val rom = File(romPath).readBytes()
    val ram = File(capturePath).readBytes()
    require(ram.size == 0x40000) { "Expected a complete private EWRAM capture." }
    val reader = object : MemoryReader {
        override fun readCoreMemory(addr: Long, size: Int): ByteArray {
            val base = if (addr >= 0x08000000L) 0x08000000L else 0x02000000L
            val bytes = if (base == 0x08000000L) rom else ram
            val at = addr - base
            require(at >= 0 && size >= 0 && at + size <= bytes.size)
            return bytes.copyOfRange(at.toInt(), at.toInt() + size)
        }
    }
    require(rom.size >= 0xC0 && PinballRsProfile.matches(
        String(rom, 0xAC, 4, Charsets.US_ASCII), rom[0xBC].toInt() and 255,
        java.security.MessageDigest.getInstance("SHA-1").digest(rom).joinToString("") { "%02x".format(it) },
    )) { "Unsupported Pinball ROM identity." }
    val live = PinballReader.read(reader)
    val decoded = PinballCollectionReader.read(reader)
    require(decoded.phase == PinballPhase.PLAYING) { "Capture has no usable collection catalog." }
    // The marker describes the screenshot's provenance; every other value is decoded unchanged.
    val collection = decoded.copy(tableLabel = "CAPTURED / ${decoded.tableLabel}")
    return listOf("catch", "evolve").map { page ->
        Shot("pinball-collection-captured-$page-thor", 1240, 1080, 2.625f,
            pinballCompanion({ live }, { collection }, "COLLECT")) {
            onNodeWithTag("pinball-collection-panel").assertIsDisplayed()
            onNodeWithTag("pinball-collection-experimental").assertIsDisplayed()
            if (page == "evolve") {
                onNodeWithTag("pinball-collection-evolve").performClick()
                settlePinball()
                check(collection.evolutions.isNotEmpty()) { "Choose a capture with a real evolvable party." }
                onNodeWithTag("pinball-evolution-${collection.evolutions.first().partySlot}").assertExists()
            } else {
                onNodeWithTag("pinball-pool-2").assertIsDisplayed()
            }
        }
    }
}
