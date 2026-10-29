package io.github.munchi369.penautilitytoolnext.team

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.munchi369.penautilitytoolnext.game.GameTeams
import io.github.munchi369.penautilitytoolnext.game.SaveRows
import io.github.munchi369.penautilitytoolnext.savedata.DerbySaveDatabaseAccess
import io.github.munchi369.penautilitytoolnext.savedata.LatestRequest
import io.github.munchi369.penautilitytoolnext.savedata.SaveDatabaseResult
import io.github.munchi369.penautilitytoolnext.savedata.nullableInt
import io.github.munchi369.penautilitytoolnext.savedata.rows
import java.nio.file.Path
import java.sql.Connection
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
                    connection
                        .rows("SELECT MYTEAM FROM SAVE WHERE ${SaveRows.CURRENT_CONDITION}") { nullableInt("MYTEAM") }
                        .singleOrNull()
                        ?.takeIf { it in GameTeams.domestic } ?: return@read null
                val name =
                    connection
                        .rows("SELECT TEAMNAME FROM TEAM_INFO WHERE TEAMNO = ?", teamNumber) { getString("TEAMNAME") }
                        .singleOrNull()
                        ?.takeIf { it.isNotBlank() } ?: return@read null
                TeamIdentity(name, connection.teamColor(teamNumber) ?: return@read null)
            }
        return (result as? SaveDatabaseResult.Success)?.value
    }
}

/** The saved RGB of [team], or null when its color row is missing, duplicated or out of range. */
internal fun Connection.teamColor(team: Int): Int? {
    val components =
        rows("SELECT ${SaveRows.teamColorColumns.joinToString()} FROM SAVE WHERE MYTEAM = ?", SaveRows.teamColorMyTeam(team)) {
            SaveRows.teamColorColumns.map { nullableInt(it) }
        }.singleOrNull() ?: return null
    val (red, green, blue) = components.map { it?.takeIf { value -> value in 0..255 } ?: return null }
    return (red shl 16) or (green shl 8) or blue
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
    private val request = LatestRequest(backgroundExecutor, uiExecutor)

    fun clear() {
        request.cancel()
        identity = null
        isLoading = false
        targetPath = null
    }

    fun load(path: Path) {
        targetPath = path
        identity = null
        isLoading = true
        request.run(work = { loader.load(path) }, onFailure = { null }) { loaded ->
            identity = loaded
            isLoading = false
        }
    }
}
