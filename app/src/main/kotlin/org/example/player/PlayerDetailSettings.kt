package org.example.player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class PlayerDetailSettings(
    val battingColumns: Set<String> =
        DetailColumns.batting
            .filter { it.standard }
            .map { it.key }
            .toSet(),
    val pitchingColumns: Set<String> =
        DetailColumns.pitching
            .filter { it.standard }
            .map { it.key }
            .toSet(),
    val graphColumns: Set<String> = setOf("SOUGOU"),
) {
    fun columns(pitching: Boolean): Set<String> = if (pitching) pitchingColumns else battingColumns
}

class PlayerDetailSettingsStore(
    private val path: Path,
) {
    fun load(): PlayerDetailSettings {
        if (!Files.exists(path)) return PlayerDetailSettings()
        val root = Json.parseToJsonElement(Files.readString(path)) as? JsonObject ?: error("詳細設定の形式が不正です")
        val defaults = PlayerDetailSettings()

        fun keys(
            key: String,
            allowed: Set<String>,
            fallback: Set<String>,
        ): Set<String> {
            val array = root[key] as? JsonArray ?: return fallback
            return array.mapNotNull { (it as? JsonPrimitive)?.content }.toSet().intersect(allowed)
        }
        return PlayerDetailSettings(
            keys("batting", DetailColumns.batting.map { it.key }.toSet(), defaults.battingColumns),
            keys("pitching", DetailColumns.pitching.map { it.key }.toSet(), defaults.pitchingColumns),
            keys("graphs", DetailColumns.graphAbilities.map { it.key }.toSet(), defaults.graphColumns),
        )
    }

    fun save(settings: PlayerDetailSettings) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val json =
            JsonObject(
                mapOf(
                    "batting" to JsonArray(settings.battingColumns.sorted().map(::JsonPrimitive)),
                    "pitching" to JsonArray(settings.pitchingColumns.sorted().map(::JsonPrimitive)),
                    "graphs" to JsonArray(settings.graphColumns.sorted().map(::JsonPrimitive)),
                ),
            )
        val temporary = Files.createTempFile(path.toAbsolutePath().parent, "player-detail-", ".tmp")
        try {
            Files.writeString(temporary, Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json))
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun clear() {
        Files.deleteIfExists(path)
    }
}
