package io.github.munchi369.penautilitytoolnext.savedata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DerbySaveDatabaseAccessTest {
    @Test
    fun committedWriteRemainsSuccessfulWhenDatabaseShutdownFails() {
        database().use { database ->
            val access =
                DerbySaveDatabaseAccess { url ->
                    if (url.endsWith(";shutdown=true")) throw SQLException("shutdown failed", "XTEST")
                    DriverManager.getConnection(url)
                }

            val result = access.write(database.path, "保存") { update(it) }
            val persisted = amount(database.path)

            assertTrue(result is SaveDatabaseResult.Success)
            val saved = result as SaveDatabaseResult.Success
            assertEquals(1, saved.value)
            assertTrue(requireNotNull(saved.releaseWarning).contains("shutdown failed"))
            assertEquals(1, persisted)
        }
    }

    @Test
    fun committedWriteRemainsSuccessfulWhenClosingTheConnectionFails() {
        database().use { database ->
            val access =
                DerbySaveDatabaseAccess { url ->
                    val connection = DriverManager.getConnection(url)
                    wrap(connection) { method ->
                        if (method == "close") {
                            connection.close()
                            throw SQLException("close failed", "XTEST")
                        }
                    }
                }

            val result = access.write(database.path, "保存") { update(it) } as SaveDatabaseResult.Success

            assertNotNull(result.releaseWarning)
            assertTrue(requireNotNull(result.releaseWarning).contains("close failed"))
            assertEquals(1, amount(database.path))
        }
    }

    @Test
    fun failedCommitRollsBackTheChanges() {
        database().use { database ->
            val access =
                DerbySaveDatabaseAccess { url ->
                    wrap(DriverManager.getConnection(url)) { method ->
                        if (method == "commit") throw SQLException("commit failed", "XTEST")
                    }
                }

            val result = access.write(database.path, "保存") { update(it) }

            assertTrue(result is SaveDatabaseResult.Failed)
            assertEquals(0, amount(database.path))
        }
    }

    @Test
    fun readStillFailsWhenTheDatabaseCannotBeReleased() {
        database().use { database ->
            val access =
                DerbySaveDatabaseAccess { url ->
                    if (url.endsWith(";shutdown=true")) throw SQLException("shutdown failed", "XTEST")
                    DriverManager.getConnection(url)
                }

            val result = access.read(database.path, "読み込み") { }
            val persisted = amount(database.path)

            assertTrue(result is SaveDatabaseResult.Failed)
            assertEquals(0, persisted)
        }
    }

    private fun database(): CopiedDerbyTestDatabase =
        CopiedDerbyTestDatabase.createSynthetic { connection ->
            connection.createStatement().use {
                it.executeUpdate("CREATE TABLE TEST_VALUES (AMOUNT INT NOT NULL)")
                it.executeUpdate("INSERT INTO TEST_VALUES VALUES 0")
            }
        }

    private fun update(connection: Connection): Int =
        connection.createStatement().use { statement ->
            statement.executeUpdate("UPDATE TEST_VALUES SET AMOUNT = 1")
        }

    private fun amount(path: Path): Int {
        val result =
            DerbySaveDatabaseAccess().read(path, "保存結果の確認") { connection ->
                connection.rows("SELECT AMOUNT FROM TEST_VALUES") { getInt("AMOUNT") }.single()
            }
        return (result as SaveDatabaseResult.Success).value
    }

    private fun wrap(
        connection: Connection,
        before: (String) -> Unit,
    ): Connection =
        Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, arguments ->
            before(method.name)
            try {
                method.invoke(connection, *arguments.orEmpty())
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        } as Connection
}
