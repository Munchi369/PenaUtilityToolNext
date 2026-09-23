package org.example.savedata

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

sealed interface SaveDatabaseReadResult<out T> {
    data class Success<T>(
        val value: T,
    ) : SaveDatabaseReadResult<T>

    data class Failed(
        val kind: SaveDatabaseFailureKind,
        val technicalDetails: String,
    ) : SaveDatabaseReadResult<Nothing>
}

class DerbySaveDatabaseAccess {
    fun <T> read(
        path: Path,
        operation: String,
        query: (Connection) -> T,
    ): SaveDatabaseReadResult<T> {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (!SaveDatabasePathResolver.hasDerbyMarker(normalizedPath)) {
            return SaveDatabaseReadResult.Failed(
                kind = SaveDatabaseFailureKind.NOT_SAVE_DATABASE,
                technicalDetails = "service.propertiesがありません。\n対象パス: $normalizedPath",
            )
        }

        val databaseUrl = "jdbc:derby:$normalizedPath"
        var databaseBooted = false
        var operationValue: T? = null
        var operationCompleted = false
        var operationFailure: Throwable? = null

        try {
            Class.forName(DERBY_DRIVER_CLASS)
            DriverManager.getConnection("$databaseUrl;create=false").use { connection ->
                databaseBooted = true
                connection.isReadOnly = true
                operationValue = query(connection)
                operationCompleted = true
            }
        } catch (error: Exception) {
            operationFailure = error
        }

        if (!databaseBooted) {
            return failureFrom(
                error = operationFailure ?: IllegalStateException("DB接続に失敗しました"),
                path = normalizedPath,
                operation = operation,
            )
        }

        val shutdownFailure = shutdown(databaseUrl)
        if (shutdownFailure != null) {
            return SaveDatabaseReadResult.Failed(
                kind = SaveDatabaseFailureKind.UNAVAILABLE,
                technicalDetails =
                    buildString {
                        operationFailure?.let {
                            appendLine(it.toSqlTechnicalDetails(normalizedPath, operation))
                            appendLine()
                        }
                        append(shutdownFailure.toSqlTechnicalDetails(normalizedPath, "DBの解放"))
                    },
            )
        }

        operationFailure?.let {
            return failureFrom(
                error = it,
                path = normalizedPath,
                operation = operation,
            )
        }

        check(operationCompleted) { "DB読み取り結果がありません" }
        @Suppress("UNCHECKED_CAST")
        return SaveDatabaseReadResult.Success(operationValue as T)
    }

    private fun shutdown(databaseUrl: String): Throwable? =
        try {
            DriverManager.getConnection("$databaseUrl;shutdown=true").use(Connection::close)
            IllegalStateException("Derby DBのshutdownが例外を返さずに終了しました")
        } catch (error: SQLException) {
            if (error.sqlState == DATABASE_SHUTDOWN_SQL_STATE) null else error
        } catch (error: Exception) {
            error
        }

    private fun failureFrom(
        error: Throwable,
        path: Path,
        operation: String,
    ): SaveDatabaseReadResult.Failed =
        SaveDatabaseReadResult.Failed(
            kind = DerbyFailureClassifier.classify(error),
            technicalDetails = error.toSqlTechnicalDetails(path, operation),
        )

    private companion object {
        const val DERBY_DRIVER_CLASS = "org.apache.derby.jdbc.EmbeddedDriver"
        const val DATABASE_SHUTDOWN_SQL_STATE = "08006"
    }
}

internal fun Throwable.toSqlTechnicalDetails(
    path: Path,
    operation: String,
): String =
    buildString {
        appendLine("処理: $operation")
        appendLine("対象パス: $path")
        throwableSequence().forEachIndexed { index, error ->
            if (index > 0) appendLine()
            appendLine("例外: ${error::class.qualifiedName}")
            if (error is SQLException) {
                appendLine("SQLState: ${error.sqlState ?: "未指定"}")
                appendLine("ベンダーコード: ${error.errorCode}")
            }
            error.message?.let { appendLine("メッセージ: $it") }
        }
    }.trimEnd()

internal fun Throwable.throwableSequence(): Sequence<Throwable> =
    sequence {
        val visited = mutableSetOf<Throwable>()
        var current: Throwable? = this@throwableSequence
        while (current != null && visited.add(current)) {
            yield(current)
            current =
                if (current is SQLException && current.nextException != null) {
                    current.nextException
                } else {
                    current.cause
                }
        }
    }
