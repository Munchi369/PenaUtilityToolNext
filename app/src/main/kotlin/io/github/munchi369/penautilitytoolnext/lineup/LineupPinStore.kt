package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.savedata.saveDatabaseKey
import io.github.munchi369.penautilitytoolnext.savedata.writeSettingsFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

interface LineupPinStore {
    @Throws(IOException::class)
    fun load(savePath: Path): Map<PinGroup, Set<Int>>

    @Throws(IOException::class)
    fun save(
        savePath: Path,
        pins: Map<PinGroup, Set<Int>>,
    )
}

object NoOpLineupPinStore : LineupPinStore {
    override fun load(savePath: Path): Map<PinGroup, Set<Int>> = emptyMap()

    override fun save(
        savePath: Path,
        pins: Map<PinGroup, Set<Int>>,
    ) = Unit
}

/** Pins are tool-only state, stored per save database path; they are never written to the game DB. */
class JsonLineupPinStore(
    private val file: Path,
) : LineupPinStore {
    override fun load(savePath: Path): Map<PinGroup, Set<Int>> {
        val saves = readSaves()
        val stored = saves[saveDatabaseKey(savePath)] as? JsonObject ?: return emptyMap()
        return PinGroup.entries.associateWith { group ->
            (stored[group.name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }?.toSet().orEmpty()
        }
    }

    override fun save(
        savePath: Path,
        pins: Map<PinGroup, Set<Int>>,
    ) {
        val saves = readSaves().toMutableMap()
        saves[saveDatabaseKey(savePath)] =
            JsonObject(PinGroup.entries.associate { group -> group.name to JsonArray(pins[group].orEmpty().sorted().map(::JsonPrimitive)) })
        val root =
            buildJsonObject {
                put(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION)
                put(SAVES_KEY, JsonObject(saves.toSortedMap()))
            }
        writeSettingsFile(file, JSON.encodeToString(JsonElement.serializer(), root) + System.lineSeparator())
    }

    private fun readSaves(): Map<String, JsonElement> {
        if (!Files.exists(file)) return emptyMap()
        val root =
            runCatching { JSON.parseToJsonElement(Files.readString(file, StandardCharsets.UTF_8)) as? JsonObject }
                .getOrElse { throw IOException("ピン設定を読み込めません: $file", it) }
                ?: throw IOException("ピン設定のルートがJSONオブジェクトではありません: $file")
        val version = (root[SCHEMA_VERSION_KEY] as? JsonPrimitive)?.intOrNull
        if (version != CURRENT_SCHEMA_VERSION) throw IOException("ピン設定のschemaVersionに対応していません: ${version ?: "未指定"}")
        return (root[SAVES_KEY] as? JsonObject).orEmpty()
    }

    private companion object {
        val JSON = Json { prettyPrint = true }
        const val CURRENT_SCHEMA_VERSION = 1
        const val SCHEMA_VERSION_KEY = "schemaVersion"
        const val SAVES_KEY = "saves"
    }
}
