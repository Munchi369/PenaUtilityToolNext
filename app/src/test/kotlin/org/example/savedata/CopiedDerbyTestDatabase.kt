package org.example.savedata

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID

internal class CopiedDerbyTestDatabase private constructor(
    val path: Path,
    private val generatedRoot: Path,
    private val repositoryRoot: Path,
) : AutoCloseable {
    override fun close() {
        val allowedRoot = repositoryRoot.resolve("build/test-db").toAbsolutePath().normalize()
        val normalizedPath = generatedRoot.toAbsolutePath().normalize()
        check(normalizedPath.startsWith(allowedRoot)) { "Refusing to delete outside $allowedRoot: $normalizedPath" }
        if (!Files.exists(normalizedPath)) return

        Files.walk(normalizedPath).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    companion object {
        fun copyFixture(source: Path): CopiedDerbyTestDatabase {
            val repositoryRoot = findRepositoryRoot()
            val fixturesRoot = repositoryRoot.resolve("test-data/fixtures").toAbsolutePath().normalize()
            check(source.toAbsolutePath().normalize().startsWith(fixturesRoot))
            val generatedRoot = repositoryRoot.resolve("build/test-db/${UUID.randomUUID()}")
            val copiedDatabase = generatedRoot.resolve("penanto3")
            copyDirectory(source, copiedDatabase)
            return CopiedDerbyTestDatabase(copiedDatabase, generatedRoot, repositoryRoot)
        }

        fun copyPrimaryReference(): CopiedDerbyTestDatabase {
            val repositoryRoot = findRepositoryRoot()
            val referenceDatabase = repositoryRoot.resolve("test-data/reference/year-05/penanto3-year05-end")
            assumeTrue(
                Files.isRegularFile(referenceDatabase.resolve("service.properties")),
                "Reference DB is not available: $referenceDatabase",
            )
            val generatedRoot = repositoryRoot.resolve("build/test-db/${UUID.randomUUID()}")
            val copiedDatabase = generatedRoot.resolve("penanto3")
            copyDirectory(referenceDatabase, copiedDatabase)
            return CopiedDerbyTestDatabase(copiedDatabase, generatedRoot, repositoryRoot)
        }

        private fun findRepositoryRoot(): Path {
            val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
            return generateSequence(workingDirectory) { it.parent }
                .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
        }

        private fun copyDirectory(
            source: Path,
            target: Path,
        ) {
            Files.walk(source).use { paths ->
                paths.forEach { sourcePath ->
                    val targetPath = target.resolve(source.relativize(sourcePath).toString())
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath)
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES)
                    }
                }
            }
        }
    }
}
