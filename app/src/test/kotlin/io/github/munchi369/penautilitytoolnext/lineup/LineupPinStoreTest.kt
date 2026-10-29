package io.github.munchi369.penautilitytoolnext.lineup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class LineupPinStoreTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun pinsRoundTripPerSaveDatabase() {
        val store = JsonLineupPinStore(root.resolve("config/lineup-pins.json"))
        val first = root.resolve("save-a/penanto3")
        val second = root.resolve("save-b/penanto3")

        store.save(first, mapOf(PinGroup.NORMAL to setOf(3, 1), PinGroup.PITCHING to setOf(9)))
        store.save(second, mapOf(PinGroup.DH to setOf(5)))

        assertEquals(mapOf(PinGroup.NORMAL to setOf(1, 3), PinGroup.DH to emptySet(), PinGroup.PITCHING to setOf(9)), store.load(first))
        assertEquals(setOf(5), store.load(second)[PinGroup.DH])
        assertEquals(emptyMap<PinGroup, Set<Int>>(), store.load(root.resolve("unknown")))
    }

    @Test
    fun unsupportedSchemaIsReportedInsteadOfOverwritten() {
        val file = root.resolve("lineup-pins.json")
        Files.writeString(file, """{"schemaVersion": 99, "saves": {}}""")

        assertThrows(IOException::class.java) { JsonLineupPinStore(file).load(root) }
    }
}
