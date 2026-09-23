package org.example.team

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.example.savedata.DerbySaveDatabaseAccess
import org.example.savedata.SaveDatabaseReadResult
import java.nio.file.Path
import java.util.concurrent.Executor

data class TeamIdentity(
    val name: String,
    val rgb: Int,
)

fun interface TeamIdentityLoader {
    fun load(path: Path): TeamIdentity?
}

class DerbyTeamIdentityLoader(
    private val databaseAccess: DerbySaveDatabaseAccess,
) : TeamIdentityLoader {
    override fun load(path: Path): TeamIdentity? {
        val result =
            databaseAccess.read(path, "自チーム情報の読み込み") { connection ->
                val teamNumber =
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT MYTEAM FROM SAVE WHERE MYTEAM >= 0 OR MYTEAM IS NULL").use { rows ->
                            if (!rows.next()) return@read null
                            val number = rows.getInt(1)
                            if (number !in 1..12 || rows.next()) return@read null
                            number
                        }
                    }
                val name =
                    connection.prepareStatement("SELECT TEAMNAME FROM TEAM_INFO WHERE TEAMNO = ?").use { statement ->
                        statement.setInt(1, teamNumber)
                        statement.executeQuery().use { rows ->
                            if (!rows.next()) return@read null
                            val value = rows.getString(1)
                            if (value.isNullOrBlank() || rows.next()) return@read null
                            value
                        }
                    }
                connection.prepareStatement("SELECT SYSNENME, SYSDAY, MONEY FROM SAVE WHERE MYTEAM = ?").use { statement ->
                    statement.setInt(1, -(teamNumber + 10))
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) return@read null
                        val components =
                            (1..3).map { column ->
                                val value = rows.getInt(column)
                                if (rows.wasNull() || value !in 0..255) return@read null
                                value
                            }
                        if (rows.next()) return@read null
                        TeamIdentity(name, (components[0] shl 16) or (components[1] shl 8) or components[2])
                    }
                }
            }
        return (result as? SaveDatabaseReadResult.Success)?.value
    }
}

class TeamIdentityController(
    private val loader: TeamIdentityLoader,
    private val backgroundExecutor: Executor,
    private val uiExecutor: Executor,
) {
    var identity by mutableStateOf<TeamIdentity?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var targetPath by mutableStateOf<Path?>(null)
        private set
    private var requestVersion = 0L

    fun clear() {
        requestVersion++
        identity = null
        isLoading = false
        targetPath = null
    }

    fun load(path: Path) {
        val version = ++requestVersion
        targetPath = path
        identity = null
        isLoading = true
        backgroundExecutor.execute {
            val loaded = loader.load(path)
            uiExecutor.execute {
                if (version == requestVersion) {
                    identity = loaded
                    isLoading = false
                }
            }
        }
    }
}
