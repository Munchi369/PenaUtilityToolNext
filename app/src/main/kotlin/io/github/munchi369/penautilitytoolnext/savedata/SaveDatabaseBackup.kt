package io.github.munchi369.penautilitytoolnext.savedata

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID

fun interface SaveDatabaseBackup {
    @Throws(IOException::class)
    fun backup(database: Path)
}

class OptionalSaveDatabaseBackup(
    private val settingsStore: SaveDatabaseSettingsStore,
    private val delegate: SaveDatabaseBackup,
) : SaveDatabaseBackup {
    override fun backup(database: Path) {
        when (val result = settingsStore.load()) {
            is SaveDatabaseSettingsLoadResult.Invalid -> throw IOException(result.technicalDetails)
            is SaveDatabaseSettingsLoadResult.Loaded -> if (result.settings.backupEnabled) delegate.backup(database)
        }
    }
}

/** Keeps only the latest copy; the previous copy is removed only after the new copy is complete. */
class LatestSaveDatabaseBackup internal constructor(
    private val backupDirectory: Path,
    private val moveDirectory: (Path, Path) -> Unit,
) : SaveDatabaseBackup {
    constructor(backupDirectory: Path) : this(backupDirectory, { source, target -> Files.move(source, target) })

    override fun backup(database: Path) {
        val source = database.toAbsolutePath().normalize()
        val target = backupDirectory.toAbsolutePath().normalize()
        val parent = requireNotNull(target.parent)
        Files.createDirectories(parent)
        val incoming = parent.resolve("${target.fileName}.incoming-${UUID.randomUUID()}")
        val outgoing = parent.resolve("${target.fileName}.outgoing-${UUID.randomUUID()}")
        try {
            copyDirectory(source, incoming)
            if (Files.exists(target)) moveDirectory(target, outgoing)
            moveDirectory(incoming, target)
        } catch (error: Exception) {
            var failure = error
            if (Files.exists(outgoing) && !Files.exists(target)) {
                runCatching { moveDirectory(outgoing, target) }.exceptionOrNull()?.let { restoreError ->
                    failure =
                        IOException("バックアップを置き換えられません。前回のバックアップは $outgoing に保持しています", error).apply {
                            addSuppressed(restoreError)
                        }
                }
            }
            runCatching { deleteDirectory(incoming) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
        deleteDirectory(outgoing)
    }

    private fun copyDirectory(
        source: Path,
        target: Path,
    ) {
        Files.walk(source).use { paths ->
            paths.forEach { sourcePath ->
                val relative = source.relativize(sourcePath)
                if (relative.toString() == LOCK_FILE) return@forEach
                val targetPath = target.resolve(relative.toString())
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
    }

    private fun deleteDirectory(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private companion object {
        const val LOCK_FILE = "db.lck"
    }
}
