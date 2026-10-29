package io.github.munchi369.penautilitytoolnext.savedata

import java.io.IOException
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Writes through a temporary file in the same directory so a crash never leaves a partially written settings file. */
@Throws(IOException::class)
internal fun writeSettingsFile(
    file: Path,
    content: String,
) {
    val directory = requireNotNull(file.toAbsolutePath().parent)
    Files.createDirectories(directory)
    val temporary = Files.createTempFile(directory, "${file.fileName}-", ".tmp")
    try {
        Files.writeString(temporary, content, StandardCharsets.UTF_8)
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

internal fun Properties.toSettingsText(comment: String? = null): String = StringWriter().also { store(it, comment) }.toString()

/**
 * Identity of a save database in tool settings. The tool runs on Windows, whose paths are case-insensitive,
 * so the absolute normalized path is case-folded.
 */
fun saveDatabaseKey(path: Path): String =
    path
        .toAbsolutePath()
        .normalize()
        .toString()
        .lowercase()

fun sameSaveDatabase(
    first: Path,
    second: Path,
): Boolean = saveDatabaseKey(first) == saveDatabaseKey(second)
