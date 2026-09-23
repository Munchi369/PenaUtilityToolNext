package org.example.savedata

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class SaveDatabasePathResolver(
    private val applicationDirectory: Path,
    private val developmentMode: Boolean,
    private val systemProperty: () -> String? = { System.getProperty(DATABASE_PATH_PROPERTY) },
    private val environmentVariable: () -> String? = { System.getenv(DATABASE_PATH_ENVIRONMENT_VARIABLE) },
    private val markerExists: (Path) -> Boolean = ::hasDerbyMarker,
) {
    fun resolve(savedPath: Path?): SaveDatabasePathResolution {
        explicitPath(systemProperty(), SaveDatabaseOrigin.JVM_PROPERTY)?.let { return it }
        explicitPath(environmentVariable(), SaveDatabaseOrigin.ENVIRONMENT_VARIABLE)?.let { return it }

        if (savedPath != null) {
            return SaveDatabasePathResolution.Found(
                SaveDatabaseTarget(
                    path = savedPath.toAbsolutePath().normalize(),
                    origin = SaveDatabaseOrigin.SAVED_SETTING,
                ),
            )
        }

        val automaticTarget =
            if (developmentMode) {
                SaveDatabaseTarget(
                    path = applicationDirectory.resolve("test-data/dev/penanto3").toAbsolutePath().normalize(),
                    origin = SaveDatabaseOrigin.DEVELOPMENT,
                )
            } else {
                val parent = applicationDirectory.parent ?: return SaveDatabasePathResolution.NotFound
                SaveDatabaseTarget(
                    path = parent.resolve("penanto3").toAbsolutePath().normalize(),
                    origin = SaveDatabaseOrigin.STANDARD_LAYOUT,
                )
            }

        return if (markerExists(automaticTarget.path)) {
            SaveDatabasePathResolution.Found(automaticTarget)
        } else {
            SaveDatabasePathResolution.NotFound
        }
    }

    private fun explicitPath(
        value: String?,
        origin: SaveDatabaseOrigin,
    ): SaveDatabasePathResolution? {
        val rawPath = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return try {
            SaveDatabasePathResolution.Found(
                SaveDatabaseTarget(
                    path = Path.of(rawPath).toAbsolutePath().normalize(),
                    origin = origin,
                ),
            )
        } catch (error: InvalidPathException) {
            SaveDatabasePathResolution.InvalidOverride(
                origin = origin,
                rawPath = rawPath,
                technicalDetails = error.toTechnicalDetails("起動指定: $rawPath"),
            )
        }
    }

    companion object {
        const val DATABASE_PATH_PROPERTY = "pena.db.path"
        const val DATABASE_PATH_ENVIRONMENT_VARIABLE = "PENA_DB_PATH"

        fun hasDerbyMarker(path: Path): Boolean = Files.isRegularFile(path.resolve("service.properties"))
    }
}
