package io.github.munchi369.penautilitytoolnext.savedata

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types

internal fun <T> Connection.rows(
    sql: String,
    vararg parameters: Int,
    read: ResultSet.() -> T,
): List<T> =
    prepareStatement(sql).use { statement ->
        parameters.forEachIndexed { index, value -> statement.setInt(index + 1, value) }
        statement.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.read()) } }
    }

internal fun <T> Connection.firstRowOrNull(
    sql: String,
    vararg parameters: Int,
    read: ResultSet.() -> T,
): T? =
    prepareStatement(sql).use { statement ->
        statement.maxRows = 1
        parameters.forEachIndexed { index, value -> statement.setInt(index + 1, value) }
        statement.executeQuery().use { rows -> if (rows.next()) rows.read() else null }
    }

internal fun ResultSet.requiredInt(column: String): Int {
    val value = getInt(column)
    check(!wasNull()) { "必須値がありません: $column" }
    return value
}

internal fun ResultSet.nullableInt(column: String): Int? {
    val value = getInt(column)
    return if (wasNull()) null else value
}

/** Integer columns of the current row by column label; SQL `NULL` stays `null`. */
internal fun ResultSet.integers(): Map<String, Int?> =
    buildMap {
        val metadata = metaData
        for (index in 1..metadata.columnCount) {
            if (metadata.getColumnType(index) in integerTypes) {
                val value = getInt(index)
                put(metadata.getColumnLabel(index), if (wasNull()) null else value)
            }
        }
    }

/** Non-integer columns of the current row by column label, read as strings. */
internal fun ResultSet.strings(): Map<String, String?> =
    buildMap {
        val metadata = metaData
        for (index in 1..metadata.columnCount) {
            if (metadata.getColumnType(index) !in integerTypes) put(metadata.getColumnLabel(index), getString(index))
        }
    }

private val integerTypes = setOf(Types.INTEGER, Types.SMALLINT)
