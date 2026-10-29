package io.github.munchi369.penautilitytoolnext.player

import io.github.munchi369.penautilitytoolnext.savedata.writeSettingsFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

data class PlayerDetailSettings(
    val graphColumns: Set<String> = setOf("SOUGOU"),
)

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
            keys("graphs", DetailColumns.graphAbilities.map { it.key }.toSet(), defaults.graphColumns),
        )
    }

    fun save(settings: PlayerDetailSettings) {
        val json =
            JsonObject(
                mapOf(
                    "graphs" to JsonArray(settings.graphColumns.sorted().map(::JsonPrimitive)),
                ),
            )
        writeSettingsFile(path, Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json))
    }

    fun clear() {
        Files.deleteIfExists(path)
    }
}
