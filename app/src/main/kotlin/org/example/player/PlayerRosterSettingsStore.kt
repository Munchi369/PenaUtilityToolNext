package org.example.player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.example.savedata.toTechnicalDetails
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties

interface PlayerRosterSettingsStore {
    fun load(): PlayerRosterSettingsLoadResult

    @Throws(PlayerRosterSettingsWriteException::class)
    fun save(settings: PlayerRosterSettings)

    @Throws(PlayerRosterSettingsWriteException::class)
    fun clear()
}

sealed interface PlayerRosterSettingsLoadResult {
    data object Missing : PlayerRosterSettingsLoadResult

    data class Loaded(
        val settings: PlayerRosterSettings,
    ) : PlayerRosterSettingsLoadResult

    data class Invalid(
        val technicalDetails: String,
    ) : PlayerRosterSettingsLoadResult
}

class PlayerRosterSettingsWriteException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)

data class PlayerRosterSettings(
    val savePath: Path,
    val teamScope: RosterTeamScope?,
    val squad: PlayerSquad?,
    val registration: PlayerRegistration?,
    val positionFilter: RosterPositionFilter?,
    val automaticDisplaySwitch: Boolean?,
    val statsSquad: Int?,
    val grouping: RosterGrouping?,
    val expandedSquads: Set<PlayerSquad>?,
    val expandedPositions: Set<PlayerPosition>?,
    val displays: Map<RosterDisplay, StoredRosterDisplayOptions>,
) {
    fun restore(base: PlayerRosterOptions): PlayerRosterOptions {
        val restoredDisplays =
            RosterDisplay.entries.associateWith { display ->
                val defaults = base.displays.getValue(display)
                val stored = displays[display] ?: return@associateWith defaults
                val columns = RosterColumns.forDisplay(display)
                val availableKeys = columns.mapTo(mutableSetOf()) { it.key }
                val sortableKeys = availableKeys + setOf("TEAM", "NUMBER", "NAME")
                val knownKeys = stored.knownColumns ?: availableKeys
                val visibleKeys =
                    stored.visibleColumns
                        ?.intersect(availableKeys)
                        ?.plus(columns.filter { it.standard && it.key !in knownKeys }.map { it.key })
                        ?: defaults.visibleColumns
                defaults.copy(
                    sortKey = stored.sortKey?.takeIf { it in sortableKeys } ?: defaults.sortKey,
                    descending = stored.descending ?: defaults.descending,
                    visibleColumns = visibleKeys,
                )
            }
        return base
            .copy(
                teamScope = teamScope ?: base.teamScope,
                squad = squad,
                registration = registration,
                positionFilter = positionFilter,
                automaticDisplaySwitch = automaticDisplaySwitch ?: base.automaticDisplaySwitch,
                statsSquad = statsSquad?.takeIf { it == 1 || it == 2 } ?: base.statsSquad,
                grouping = grouping ?: base.grouping,
                expandedSquads = expandedSquads ?: base.expandedSquads,
                expandedPositions = expandedPositions ?: base.expandedPositions,
                displays = restoredDisplays,
            ).coercedGrouping()
    }

    companion object {
        fun from(
            savePath: Path,
            options: PlayerRosterOptions,
        ): PlayerRosterSettings =
            PlayerRosterSettings(
                savePath = savePath.toAbsolutePath().normalize(),
                teamScope = options.teamScope,
                squad = options.squad,
                registration = options.registration,
                positionFilter = options.positionFilter,
                automaticDisplaySwitch = options.automaticDisplaySwitch,
                statsSquad = options.statsSquad,
                grouping = options.grouping,
                expandedSquads = options.expandedSquads,
                expandedPositions = options.expandedPositions,
                displays =
                    options.displays.mapValues { (display, value) ->
                        StoredRosterDisplayOptions(
                            sortKey = value.sortKey,
                            descending = value.descending,
                            visibleColumns = value.visibleColumns,
                            knownColumns = RosterColumns.forDisplay(display).mapTo(mutableSetOf()) { it.key },
                        )
                    },
            )
    }
}

data class StoredRosterDisplayOptions(
    val sortKey: String?,
    val descending: Boolean?,
    val visibleColumns: Set<String>?,
    val knownColumns: Set<String>?,
)

object NoOpPlayerRosterSettingsStore : PlayerRosterSettingsStore {
    override fun load(): PlayerRosterSettingsLoadResult = PlayerRosterSettingsLoadResult.Missing

    override fun save(settings: PlayerRosterSettings) = Unit

    override fun clear() = Unit
}

class JsonPlayerRosterSettingsStore(
    private val settingsFile: Path,
    private val legacySettingsFile: Path? = null,
) : PlayerRosterSettingsStore {
    override fun load(): PlayerRosterSettingsLoadResult {
        if (Files.exists(settingsFile)) {
            return loadJson()
        }
        val legacyFile =
            legacySettingsFile?.takeIf(Files::exists)
                ?: return PlayerRosterSettingsLoadResult.Missing
        val loaded = loadLegacyProperties(legacyFile)
        if (loaded is PlayerRosterSettingsLoadResult.Loaded) {
            runCatching { save(loaded.settings) }
        }
        return loaded
    }

    override fun save(settings: PlayerRosterSettings) {
        val directory = requireNotNull(settingsFile.parent)
        var temporaryFile: Path? = null
        try {
            Files.createDirectories(directory)
            temporaryFile = Files.createTempFile(directory, "player-roster-", ".tmp")
            val content = JSON.encodeToString(JsonElement.serializer(), settings.toJson()) + System.lineSeparator()
            Files.writeString(
                temporaryFile,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            moveIntoPlace(temporaryFile)
            temporaryFile = null
            legacySettingsFile?.let { Files.deleteIfExists(it) }
        } catch (error: Exception) {
            throw PlayerRosterSettingsWriteException("一覧設定を保存できません: $settingsFile", error)
        } finally {
            temporaryFile?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    override fun clear() {
        val error =
            listOfNotNull(settingsFile, legacySettingsFile)
                .mapNotNull { file -> runCatching { Files.deleteIfExists(file) }.exceptionOrNull() }
                .firstOrNull()
        if (error != null) {
            throw PlayerRosterSettingsWriteException("一覧設定を初期化できません: $settingsFile", error)
        }
    }

    private fun loadJson(): PlayerRosterSettingsLoadResult =
        try {
            val root =
                JSON.parseToJsonElement(Files.readString(settingsFile, StandardCharsets.UTF_8)) as? JsonObject
                    ?: return PlayerRosterSettingsLoadResult.Invalid(
                        "一覧設定ファイルのルートがJSONオブジェクトではありません: $settingsFile",
                    )
            if (root.intValue(SCHEMA_VERSION_KEY) != CURRENT_SCHEMA_VERSION) {
                return PlayerRosterSettingsLoadResult.Invalid(
                    "一覧設定ファイル: $settingsFile\n" +
                        "schemaVersion: ${root.intValue(SCHEMA_VERSION_KEY) ?: "未指定"}\n" +
                        "対応schemaVersion: $CURRENT_SCHEMA_VERSION",
                )
            }
            loadJsonSettings(root)
        } catch (error: Exception) {
            PlayerRosterSettingsLoadResult.Invalid(error.toTechnicalDetails("一覧設定ファイル: $settingsFile"))
        }

    private fun loadJsonSettings(root: JsonObject): PlayerRosterSettingsLoadResult {
        val rawPath = root.stringValue(SAVE_PATH_KEY)?.trim().orEmpty()
        val savePath =
            rawPath.toAbsoluteNormalizedPathOrNull()
                ?: return PlayerRosterSettingsLoadResult.Invalid(
                    "一覧設定ファイル: $settingsFile\n${SAVE_PATH_KEY}は絶対パスである必要があります",
                )
        val filters = root.objectValue(FILTERS_KEY)
        val displayValues = root.objectValue(DISPLAYS_KEY)
        return PlayerRosterSettingsLoadResult.Loaded(
            PlayerRosterSettings(
                savePath = savePath,
                teamScope = filters?.stringValue(TEAM_SCOPE_KEY).toTeamScope(),
                squad = filters?.enumValue(SQUAD_KEY),
                registration = filters?.enumValue(REGISTRATION_KEY),
                positionFilter = filters?.stringValue(POSITION_FILTER_KEY).toPositionFilter(),
                automaticDisplaySwitch = root.booleanValue(AUTOMATIC_DISPLAY_KEY),
                statsSquad = root.intValue(STATS_SQUAD_KEY),
                grouping = root.enumValue(GROUPING_KEY),
                expandedSquads = root.enumSet(EXPANDED_SQUADS_KEY),
                expandedPositions = root.enumSet(EXPANDED_POSITIONS_KEY),
                displays =
                    RosterDisplay.entries
                        .mapNotNull { display ->
                            val stored = displayValues?.objectValue(display.name) ?: return@mapNotNull null
                            display to
                                StoredRosterDisplayOptions(
                                    sortKey = stored.stringValue(SORT_KEY),
                                    descending = stored.booleanValue(DESCENDING_KEY),
                                    visibleColumns = stored.stringSet(VISIBLE_COLUMNS_KEY),
                                    knownColumns = stored.stringSet(KNOWN_COLUMNS_KEY),
                                )
                        }.toMap(),
            ),
        )
    }

    private fun loadLegacyProperties(legacyFile: Path): PlayerRosterSettingsLoadResult =
        try {
            val properties = Properties()
            Files.newBufferedReader(legacyFile, StandardCharsets.UTF_8).use(properties::load)
            if (properties.getProperty(SCHEMA_VERSION_KEY) != CURRENT_SCHEMA_VERSION.toString()) {
                PlayerRosterSettingsLoadResult.Invalid(
                    "旧一覧設定ファイル: $legacyFile\n" +
                        "schemaVersion: ${properties.getProperty(SCHEMA_VERSION_KEY) ?: "未指定"}\n" +
                        "対応schemaVersion: $CURRENT_SCHEMA_VERSION",
                )
            } else {
                properties.toSettings(legacyFile)
            }
        } catch (error: Exception) {
            PlayerRosterSettingsLoadResult.Invalid(error.toTechnicalDetails("旧一覧設定ファイル: $legacyFile"))
        }

    @Throws(IOException::class)
    private fun moveIntoPlace(temporaryFile: Path) {
        try {
            Files.move(
                temporaryFile,
                settingsFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporaryFile, settingsFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun PlayerRosterSettings.toJson(): JsonObject =
        buildJsonObject {
            put(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION)
            put(SAVE_PATH_KEY, savePath.toAbsolutePath().normalize().toString())
            put(
                FILTERS_KEY,
                buildJsonObject {
                    teamScope?.let { put(TEAM_SCOPE_KEY, it.jsonValue()) }
                    squad?.let { put(SQUAD_KEY, it.name) }
                    registration?.let { put(REGISTRATION_KEY, it.name) }
                    positionFilter?.let { put(POSITION_FILTER_KEY, it.jsonValue()) }
                },
            )
            automaticDisplaySwitch?.let { put(AUTOMATIC_DISPLAY_KEY, it) }
            statsSquad?.let { put(STATS_SQUAD_KEY, it) }
            grouping?.let { put(GROUPING_KEY, it.name) }
            expandedSquads?.let { put(EXPANDED_SQUADS_KEY, it.toEnumJsonArray()) }
            expandedPositions?.let { put(EXPANDED_POSITIONS_KEY, it.toEnumJsonArray()) }
            put(
                DISPLAYS_KEY,
                buildJsonObject {
                    displays.toSortedMap().forEach { (display, value) ->
                        put(
                            display.name,
                            buildJsonObject {
                                value.sortKey?.let { put(SORT_KEY, it) }
                                value.descending?.let { put(DESCENDING_KEY, it) }
                                value.visibleColumns?.let { put(VISIBLE_COLUMNS_KEY, it.toStringJsonArray()) }
                                value.knownColumns?.let { put(KNOWN_COLUMNS_KEY, it.toStringJsonArray()) }
                            },
                        )
                    }
                },
            )
        }

    private fun Properties.toSettings(legacyFile: Path): PlayerRosterSettingsLoadResult {
        val rawPath = getProperty(SAVE_PATH_KEY)?.trim().orEmpty()
        val savePath =
            rawPath.toAbsoluteNormalizedPathOrNull()
                ?: return PlayerRosterSettingsLoadResult.Invalid(
                    "旧一覧設定ファイル: $legacyFile\n${SAVE_PATH_KEY}は絶対パスである必要があります",
                )
        return PlayerRosterSettingsLoadResult.Loaded(
            PlayerRosterSettings(
                savePath = savePath,
                teamScope = null,
                squad = enumValue("filter.squad"),
                registration = enumValue("filter.registration"),
                positionFilter = getProperty("filter.position").toPositionFilter(),
                automaticDisplaySwitch = booleanValue(AUTOMATIC_DISPLAY_KEY),
                statsSquad = getProperty(STATS_SQUAD_KEY)?.toIntOrNull(),
                grouping = enumValue(GROUPING_KEY),
                expandedSquads = enumSet(EXPANDED_SQUADS_KEY),
                expandedPositions = enumSet(EXPANDED_POSITIONS_KEY),
                displays =
                    RosterDisplay.entries
                        .mapNotNull { display ->
                            val prefix = "display.${display.name}."
                            val sortKey = getProperty(prefix + SORT_KEY)
                            val descending = booleanValue(prefix + DESCENDING_KEY)
                            val visibleColumns = stringSet(prefix + VISIBLE_COLUMNS_KEY)
                            val knownColumns = stringSet(prefix + KNOWN_COLUMNS_KEY)
                            if (sortKey == null && descending == null && visibleColumns == null && knownColumns == null) {
                                null
                            } else {
                                display to StoredRosterDisplayOptions(sortKey, descending, visibleColumns, knownColumns)
                            }
                        }.toMap(),
            ),
        )
    }

    private companion object {
        val JSON = Json { prettyPrint = true }
        const val CURRENT_SCHEMA_VERSION = 1
        const val SCHEMA_VERSION_KEY = "schemaVersion"
        const val SAVE_PATH_KEY = "savePath"
        const val FILTERS_KEY = "filters"
        const val TEAM_SCOPE_KEY = "teamScope"
        const val SQUAD_KEY = "squad"
        const val REGISTRATION_KEY = "registration"
        const val POSITION_FILTER_KEY = "position"
        const val AUTOMATIC_DISPLAY_KEY = "automaticDisplay"
        const val STATS_SQUAD_KEY = "statsSquad"
        const val GROUPING_KEY = "grouping"
        const val EXPANDED_SQUADS_KEY = "expandedSquads"
        const val EXPANDED_POSITIONS_KEY = "expandedPositions"
        const val DISPLAYS_KEY = "displays"
        const val SORT_KEY = "sortKey"
        const val DESCENDING_KEY = "descending"
        const val VISIBLE_COLUMNS_KEY = "visibleColumns"
        const val KNOWN_COLUMNS_KEY = "knownColumns"
    }
}

private fun String?.toAbsoluteNormalizedPathOrNull(): Path? =
    try {
        this
            ?.takeIf(String::isNotEmpty)
            ?.let(Path::of)
            ?.takeIf(Path::isAbsolute)
            ?.normalize()
    } catch (_: RuntimeException) {
        null
    }

private fun String?.toPositionFilter(): RosterPositionFilter? =
    when (this) {
        "FIELDERS" -> RosterPositionFilter.Fielders
        else ->
            this
                ?.removePrefix("POSITION:")
                ?.let { runCatching { PlayerPosition.valueOf(it) }.getOrNull() }
                ?.let(RosterPositionFilter::MainPosition)
    }

private fun String?.toTeamScope(): RosterTeamScope? =
    when (this) {
        "ALL" -> RosterTeamScope.All
        "ALL_DOMESTIC" -> RosterTeamScope.AllDomestic
        "FIRST_LEAGUE" -> RosterTeamScope.FirstLeague
        "SECOND_LEAGUE" -> RosterTeamScope.SecondLeague
        "MAJOR" -> RosterTeamScope.Major
        else ->
            this
                ?.takeIf { it.startsWith("TEAM:") }
                ?.removePrefix("TEAM:")
                ?.toIntOrNull()
                ?.takeIf { it in 1..12 }
                ?.let(RosterTeamScope::Team)
    }

private fun RosterTeamScope.jsonValue(): String =
    when (this) {
        RosterTeamScope.All -> "ALL"
        RosterTeamScope.AllDomestic -> "ALL_DOMESTIC"
        RosterTeamScope.FirstLeague -> "FIRST_LEAGUE"
        RosterTeamScope.SecondLeague -> "SECOND_LEAGUE"
        RosterTeamScope.Major -> "MAJOR"
        is RosterTeamScope.Team -> "TEAM:$number"
    }

private fun RosterPositionFilter.jsonValue(): String =
    when (this) {
        RosterPositionFilter.Fielders -> "FIELDERS"
        is RosterPositionFilter.MainPosition -> position.name
    }

private fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

private fun JsonObject.booleanValue(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.intValue(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.stringSet(key: String): Set<String>? =
    (this[key] as? JsonArray)
        ?.mapNotNull { element ->
            (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
        }?.toSet()

private inline fun <reified T : Enum<T>> JsonObject.enumValue(key: String): T? =
    stringValue(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

private inline fun <reified T : Enum<T>> JsonObject.enumSet(key: String): Set<T>? =
    stringSet(key)?.mapNotNull { runCatching { enumValueOf<T>(it) }.getOrNull() }?.toSet()

private inline fun <reified T : Enum<T>> Properties.enumValue(key: String): T? =
    getProperty(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

private inline fun <reified T : Enum<T>> Properties.enumSet(key: String): Set<T>? =
    stringSet(key)?.mapNotNull { runCatching { enumValueOf<T>(it) }.getOrNull() }?.toSet()

private fun Properties.booleanValue(key: String): Boolean? = getProperty(key)?.toBooleanStrictOrNull()

private fun Properties.stringSet(key: String): Set<String>? =
    getProperty(key)?.let { raw ->
        if (raw.isBlank()) {
            emptySet()
        } else {
            raw
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        }
    }

private fun <T : Enum<T>> Set<T>.toEnumJsonArray(): JsonArray = JsonArray(map { JsonPrimitive(it.name) }.sortedBy { it.content })

private fun Set<String>.toStringJsonArray(): JsonArray = JsonArray(sorted().map(::JsonPrimitive))
