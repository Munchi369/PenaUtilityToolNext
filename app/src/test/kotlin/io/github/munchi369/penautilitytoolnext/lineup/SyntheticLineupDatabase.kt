package io.github.munchi369.penautilitytoolnext.lineup

import io.github.munchi369.penautilitytoolnext.game.PlayerNumberCode
import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import io.github.munchi369.penautilitytoolnext.game.PlayerStatusCode
import io.github.munchi369.penautilitytoolnext.savedata.CopiedDerbyTestDatabase
import java.sql.Connection

/**
 * The game tables the lineup editor reads and writes, reduced to the columns it uses.
 * Types follow the Reference DB; [SyntheticLineupDatabaseTest] compares them whenever it is available.
 */
internal object SyntheticLineupDatabase {
    const val TEAM = 1
    const val OTHER_TEAM_PLAYER = 9001
    const val DAY = 100
    const val SEASON = 5

    private val kiyoColumns =
        (1..9).map { "SENPATU$it" } + (1..9).map { "NAKATUGI$it" } + (2..9).map { "POJI$it" } + (1..9).map { "DAJUN$it" } +
            (1..13).map { "HIKAE$it" } + (2..10).map { "POJIDH$it" } + (1..9).map { "DAJUNDH$it" } + (1..12).map { "HIKAEDH$it" }

    private val playerColumns =
        listOf("SENSYU_NUM", "NAME", "POJI") + FieldSlot.defense.map { requireNotNull(it.aptitudeColumn) } +
            listOf("NENREI", "NENME", "KOUDA", "POWER", "SENPATUTEKISEI", "NAKATUGITEKISEI", "ITINIGUN", "KEGA", "TOROKU", "SOUGOU") +
            listOf("GENINFLG", "SHOZOKU")

    val tables: Map<String, List<String>> =
        mapOf(
            "SAVE" to listOf("MYTEAM INTEGER", "SYSNENME INTEGER", "SYSDAY INTEGER"),
            "SENSYU_DATA" to
                listOf("SENSYU_ID INTEGER NOT NULL PRIMARY KEY") +
                playerColumns.map { if (it == "NAME") "NAME VARCHAR(10)" else "$it INTEGER" },
            "KIYO" to kiyoColumns.map { "$it INTEGER" },
        ) +
            DerbyLineupLoader.seasonTables.associateWith {
                listOf("NUM INTEGER NOT NULL", "NENME INTEGER NOT NULL", "ITINIGUN INTEGER NOT NULL", "SEBANGOU INTEGER")
            }

    /** [fullTeam] plus a second-team fielder, one player of another team, a history lineup row and last season's records. */
    val players: List<LineupPlayer> = fullTeam(listOf(fielder(201, PlayerPosition.CATCHER, squad = PlayerSquad.SECOND)))

    fun create(): CopiedDerbyTestDatabase =
        CopiedDerbyTestDatabase.createSynthetic { connection ->
            tables.forEach { (table, columns) -> connection.execute("CREATE TABLE $table (${columns.joinToString()})") }
            connection.execute("INSERT INTO SAVE VALUES ($TEAM, $SEASON, $DAY)")
            connection.execute("INSERT INTO SAVE VALUES (-11, 255, 0)")
            players.forEach { connection.insertPlayer(it, TEAM) }
            connection.insertPlayer(fielder(OTHER_TEAM_PLAYER, PlayerPosition.CATCHER).copy(uniformNumber = 1), team = 2)
            val starters = snapshotOf(players).initial
            connection.insertKiyo(regularKiyo(starters))
            connection.insertKiyo(regularKiyo(starters) + ("HIKAEDH12" to 3))
            (players.map { it.id } + OTHER_TEAM_PLAYER).forEach { id ->
                val table = if (id < 100) "TOUSYU_SEISEKI" else "YASYU_SEISEKI"
                val number = players.firstOrNull { it.id == id }?.uniformNumber ?: 1
                connection.execute("INSERT INTO $table VALUES ($id, 1, 1, $number)")
                connection.execute("INSERT INTO $table VALUES ($id, 2, 1, $number)")
            }
        }

    private fun regularKiyo(draft: LineupDraft): Map<String, Int> =
        kiyoColumns.associateWith { 0 } +
            draft.normal.mapIndexed { index, entry -> "DAJUN${index + 1}" to (entry.playerId ?: 0) } +
            draft.dh.mapIndexed { index, entry -> "DAJUNDH${index + 1}" to (entry.playerId ?: 0) } +
            FieldSlot.defense.mapIndexed { index, slot -> "POJI${index + 2}" to draft.normal.single { it.slot == slot }.playerId!! } +
            (FieldSlot.defense + FieldSlot.DH).mapIndexed { index, slot ->
                "POJIDH${index + 2}" to
                    draft.dh.single { it.slot == slot }.playerId!!
            } +
            draft.starters.mapIndexed { index, id -> "SENPATU${index + 1}" to (id ?: 0) } +
            draft.relievers.mapIndexed { index, id -> "NAKATUGI${index + 1}" to (id ?: 0) } +
            ("HIKAEDH12" to 0)

    private fun Connection.insertKiyo(values: Map<String, Int>) {
        execute("INSERT INTO KIYO (${values.keys.joinToString()}) VALUES (${values.values.joinToString()})")
    }

    private fun Connection.insertPlayer(
        player: LineupPlayer,
        team: Int,
    ) {
        val values =
            mapOf(
                "SENSYU_ID" to player.id,
                "SENSYU_NUM" to PlayerNumberCode.encode(team, player.initialRegistration, player.uniformNumber),
                "POJI" to player.position.databaseValue,
                "NENREI" to player.age,
                "NENME" to 2,
                "KOUDA" to player.batting,
                "POWER" to player.power,
                "SENPATUTEKISEI" to player.starterAptitude,
                "NAKATUGITEKISEI" to player.relieverAptitude,
                "ITINIGUN" to player.initialSquad.databaseValue,
                "KEGA" to 0,
                "TOROKU" to player.restDays,
                "SOUGOU" to player.overall,
                "GENINFLG" to PlayerStatusCode.ACTIVE,
                "SHOZOKU" to if (player.foreign) 0 else 1,
            ) + FieldSlot.defense.associate { requireNotNull(it.aptitudeColumn) to (player.initialAptitudes[it] ?: 0) }
        prepareStatement(
            "INSERT INTO SENSYU_DATA (NAME, ${values.keys.joinToString()}) VALUES (?, ${values.values.joinToString()})",
        ).use { statement ->
            statement.setString(1, player.name)
            statement.executeUpdate()
        }
    }

    private fun Connection.execute(sql: String) {
        createStatement().use { it.executeUpdate(sql) }
    }
}
