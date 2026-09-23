package org.example.savedata

import java.nio.file.Files
import java.nio.file.Path

data class ApplicationEnvironment(
    val applicationDirectory: Path,
    val developmentMode: Boolean,
) {
    val settingsFile: Path
        get() = applicationDirectory.resolve("config/settings.properties")

    val playerRosterSettingsFile: Path
        get() = applicationDirectory.resolve("config/player-roster.json")

    val legacyPlayerRosterSettingsFile: Path
        get() = applicationDirectory.resolve("config/player-roster.properties")
}

object ApplicationEnvironmentLocator {
    fun locate(): ApplicationEnvironment {
        System
            .getProperty(APPLICATION_DIRECTORY_PROPERTY)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let {
                return ApplicationEnvironment(
                    applicationDirectory = Path.of(it).toAbsolutePath().normalize(),
                    developmentMode = System.getProperty(DEVELOPMENT_MODE_PROPERTY).toBoolean(),
                )
            }

        System
            .getProperty(JPACKAGE_APPLICATION_PATH_PROPERTY)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { applicationPath ->
                Path.of(applicationPath).toAbsolutePath().normalize().parent?.let { directory ->
                    return ApplicationEnvironment(directory, developmentMode = false)
                }
            }

        System
            .getProperty(COMPOSE_RESOURCES_DIRECTORY_PROPERTY)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { resourcesPath ->
                val resourcesDirectory = Path.of(resourcesPath).toAbsolutePath().normalize()
                if (resourcesDirectory.fileName?.toString() == "resources") {
                    resourcesDirectory.parent?.parent?.let { directory ->
                        return ApplicationEnvironment(directory, developmentMode = false)
                    }
                }
            }

        findRepositoryRoot()?.let { repositoryRoot ->
            return ApplicationEnvironment(repositoryRoot, developmentMode = true)
        }

        error("PenaUtilityToolNextの配置ディレクトリを特定できません")
    }

    private fun findRepositoryRoot(): Path? {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
    }

    private const val APPLICATION_DIRECTORY_PROPERTY = "pena.app.dir"
    private const val DEVELOPMENT_MODE_PROPERTY = "pena.development"
    private const val JPACKAGE_APPLICATION_PATH_PROPERTY = "jpackage.app-path"
    private const val COMPOSE_RESOURCES_DIRECTORY_PROPERTY = "compose.application.resources.dir"
}
