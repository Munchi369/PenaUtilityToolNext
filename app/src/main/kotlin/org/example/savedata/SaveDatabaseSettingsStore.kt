package org.example.savedata

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties

interface SaveDatabaseSettingsStore {
    val settingsFile: Path

    fun load(): SaveDatabaseSettingsLoadResult

    @Throws(SaveDatabaseSettingsWriteException::class)
    fun save(settings: SaveDatabaseSettings)
}

class SaveDatabaseSettingsWriteException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)

class PropertiesSaveDatabaseSettingsStore(
    override val settingsFile: Path,
) : SaveDatabaseSettingsStore {
    override fun load(): SaveDatabaseSettingsLoadResult {
        if (!Files.exists(settingsFile)) {
            return SaveDatabaseSettingsLoadResult.Loaded(SaveDatabaseSettings(selectedPath = null))
        }

        return try {
            val properties = Properties()
            Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8).use(properties::load)

            val schemaVersion = properties.getProperty(SCHEMA_VERSION_KEY)
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                SaveDatabaseSettingsLoadResult.Invalid(
                    technicalDetails =
                        "設定ファイル: $settingsFile\n" +
                            "schemaVersion: ${schemaVersion ?: "未指定"}\n" +
                            "対応schemaVersion: $CURRENT_SCHEMA_VERSION",
                )
            } else {
                loadSettings(properties)
            }
        } catch (error: Exception) {
            SaveDatabaseSettingsLoadResult.Invalid(
                technicalDetails = error.toTechnicalDetails("設定ファイル: $settingsFile"),
            )
        }
    }

    override fun save(settings: SaveDatabaseSettings) {
        val directory = settingsFile.parent
        var temporaryFile: Path? = null

        try {
            Files.createDirectories(directory)
            temporaryFile = Files.createTempFile(directory, "settings-", ".tmp")

            val properties =
                Properties().apply {
                    setProperty(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION)
                    settings.selectedPath?.let {
                        setProperty(SELECTED_PATH_KEY, it.toAbsolutePath().normalize().toString())
                    }
                }
            Files
                .newBufferedWriter(
                    temporaryFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ).use { writer ->
                    properties.store(writer, null)
                }

            moveIntoPlace(temporaryFile)
            temporaryFile = null
        } catch (error: Exception) {
            throw SaveDatabaseSettingsWriteException(
                message = "設定ファイルを保存できません: $settingsFile",
                cause = error,
            )
        } finally {
            temporaryFile?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun loadSettings(properties: Properties): SaveDatabaseSettingsLoadResult {
        val selectedPath = properties.getProperty(SELECTED_PATH_KEY)?.trim()?.takeIf(String::isNotEmpty)
        return try {
            val parsedPath = selectedPath?.let(Path::of)
            if (parsedPath != null && !parsedPath.isAbsolute) {
                return SaveDatabaseSettingsLoadResult.Invalid(
                    technicalDetails =
                        "設定ファイル: $settingsFile\n" +
                            "selectedPathは絶対パスである必要があります: $parsedPath",
                )
            }
            SaveDatabaseSettingsLoadResult.Loaded(
                SaveDatabaseSettings(selectedPath = parsedPath?.normalize()),
            )
        } catch (error: RuntimeException) {
            SaveDatabaseSettingsLoadResult.Invalid(
                technicalDetails = error.toTechnicalDetails("設定ファイル: $settingsFile"),
            )
        }
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
            Files.move(
                temporaryFile,
                settingsFile,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = "1"
        const val SCHEMA_VERSION_KEY = "schemaVersion"
        const val SELECTED_PATH_KEY = "selectedPath"
    }
}

internal fun Throwable.toTechnicalDetails(context: String): String =
    buildString {
        appendLine(context)
        appendLine("例外: ${this@toTechnicalDetails::class.qualifiedName}")
        message?.let { append("メッセージ: $it") }
    }.trimEnd()
