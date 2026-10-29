package io.github.munchi369.penautilitytoolnext.player

import io.github.munchi369.penautilitytoolnext.game.PlayerPosition
import io.github.munchi369.penautilitytoolnext.game.PlayerRegistration
import io.github.munchi369.penautilitytoolnext.game.PlayerSquad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class PlayerRosterSettingsStoreTest {
    @Test
    fun uniformNumberIsAddedToOldSettingsButItsNewHiddenPositionPersists(
        @TempDir root: Path,
    ) {
        val base = PlayerRosterOptions.forTeam(6)
        val snapshot = PlayerRosterSettings.from(root.resolve("save"), base)
        val old =
            snapshot.copy(
                displays =
                    snapshot.displays.mapValues { (_, value) ->
                        value.copy(
                            visibleColumns = value.visibleColumns?.minus("NUMBER"),
                            knownColumns = value.knownColumns?.minus("NUMBER"),
                            columnOrder = listOf("AGE", "ID", "SOUGOU"),
                        )
                    },
            )
        val restored = old.restore(base)
        assertTrue("NUMBER" in restored.current.visibleColumns)
        assertEquals(listOf("AGE", "NUMBER", "ID", "SOUGOU"), restored.orderedColumns().take(4).map { it.key })
        val store = JsonPlayerRosterSettingsStore(root.resolve("player-roster.json"))
        val modified = restored.moveColumn("NUMBER", "SOUGOU").hideColumn("NUMBER")
        store.save(PlayerRosterSettings.from(root.resolve("save"), modified))
        val reloaded = (store.load() as PlayerRosterSettingsLoadResult.Loaded).settings.restore(base)
        assertFalse("NUMBER" in reloaded.current.visibleColumns)
        assertEquals(modified.current.columnOrder, reloaded.current.columnOrder)
        for (display in RosterDisplay.entries) {
            assertEquals(
                "NUMBER",
                base
                    .selectDisplay(display)
                    .orderedColumns()
                    .first()
                    .key,
            )
        }
    }

    @Test
    fun missingFileUsesDefaults(
        @TempDir root: Path,
    ) {
        val result = JsonPlayerRosterSettingsStore(root.resolve("config/player-roster.json")).load()

        assertEquals(PlayerRosterSettingsLoadResult.Missing, result)
    }

    @Test
    fun settingsRoundTripRestoresPersistentOptionsOnly(
        @TempDir root: Path,
    ) {
        val path = root.resolve("save").toAbsolutePath()
        val settingsFile = root.resolve("config/player-roster.json")
        val store = JsonPlayerRosterSettingsStore(settingsFile)
        val options =
            PlayerRosterOptions
                .forTeam(6)
                .copy(
                    teamScope = RosterTeamScope.Major,
                    display = RosterDisplay.PITCHING_STATS,
                    search = "検索中",
                    squad = PlayerSquad.SECOND,
                    registration = PlayerRegistration.DEVELOPMENT,
                    positionFilter = RosterPositionFilter.MainPosition(PlayerPosition.CATCHER),
                    automaticDisplaySwitch = true,
                    statsSquad = 2,
                    grouping = RosterGrouping.POSITION,
                    expandedSquads = emptySet(),
                    expandedPositions = setOf(PlayerPosition.PITCHER, PlayerPosition.CATCHER),
                    displays =
                        PlayerRosterOptions
                            .forTeam(6)
                            .displays
                            .mapValues { (display, value) ->
                                if (display == RosterDisplay.BASIC) {
                                    value.copy(
                                        sortKey = "AGE",
                                        descending = false,
                                        visibleColumns = setOf("ID", "AGE"),
                                        columnOrder = listOf("AGE", "ID", "SOUGOU"),
                                        columnWidths = mapOf("AGE" to 102.5f, "NAME" to 240f),
                                    )
                                } else {
                                    value
                                }
                            },
                )

        store.save(PlayerRosterSettings.from(path, options))
        val loaded = store.load() as PlayerRosterSettingsLoadResult.Loaded
        val restored = loaded.settings.restore(PlayerRosterOptions.forTeam(3))

        assertEquals(path.normalize(), loaded.settings.savePath)
        assertEquals(RosterTeamScope.Major, restored.teamScope)
        assertEquals(RosterDisplay.BASIC, restored.display)
        assertEquals("", restored.search)
        assertEquals(PlayerSquad.SECOND, restored.squad)
        assertEquals(PlayerRegistration.DEVELOPMENT, restored.registration)
        assertEquals(RosterPositionFilter.MainPosition(PlayerPosition.CATCHER), restored.positionFilter)
        assertTrue(restored.automaticDisplaySwitch)
        assertEquals(2, restored.statsSquad)
        assertEquals(RosterGrouping.POSITION, restored.grouping)
        assertEquals(emptySet<PlayerSquad>(), restored.expandedSquads)
        assertEquals(setOf(PlayerPosition.PITCHER, PlayerPosition.CATCHER), restored.expandedPositions)
        assertEquals("AGE", restored.displays.getValue(RosterDisplay.BASIC).sortKey)
        assertFalse(restored.displays.getValue(RosterDisplay.BASIC).descending)
        assertEquals(setOf("ID", "AGE"), restored.displays.getValue(RosterDisplay.BASIC).visibleColumns)
        assertEquals(listOf("AGE", "ID", "SOUGOU"), restored.current.columnOrder)
        assertEquals(mapOf("AGE" to 102.5f, "NAME" to 240f), restored.current.columnWidths)
        val json = Files.readString(settingsFile, StandardCharsets.UTF_8)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"filters\": {"))
        assertTrue(json.contains("\"teamScope\": \"MAJOR\""))
        assertTrue(json.contains("\"visibleColumns\": ["))
    }

    @Test
    fun invalidIndividualValuesFallBackWithoutDiscardingValidValues(
        @TempDir root: Path,
    ) {
        val settingsFile = root.resolve("config/player-roster.json")
        val path = root.resolve("save").toAbsolutePath().normalize()
        Files.createDirectories(settingsFile.parent)
        Files.writeString(
            settingsFile,
            """
            {
              "schemaVersion": 1,
              "savePath": "${path.toString().replace("\\", "\\\\")}",
              "filters": {
                "teamScope": "6",
                "squad": "THIRD",
                "registration": "DEVELOPMENT"
              },
              "automaticDisplay": "maybe",
              "statsSquad": 9,
              "grouping": "UNKNOWN",
              "displays": {
                "BASIC": {
                  "sortKey": "REMOVED",
                  "descending": false,
                  "visibleColumns": ["ID", "REMOVED"],
                  "knownColumns": ["ID", "REMOVED"],
                  "columnWidths": {"ID": 120.5, "NAME": 220, "AGE": -1, "TEAM": "bad", "REMOVED": 100, "SOUGOU": 99999}
                }
              }
            }
            """.trimIndent(),
        )

        val loaded = JsonPlayerRosterSettingsStore(settingsFile).load() as PlayerRosterSettingsLoadResult.Loaded
        val restored = loaded.settings.restore(PlayerRosterOptions.forTeam(3))
        val basic = restored.displays.getValue(RosterDisplay.BASIC)

        assertEquals(RosterTeamScope.Team(3), restored.teamScope)
        assertEquals(null, restored.squad)
        assertEquals(PlayerRegistration.DEVELOPMENT, restored.registration)
        assertFalse(restored.automaticDisplaySwitch)
        assertEquals(1, restored.statsSquad)
        assertEquals(RosterGrouping.SQUAD, restored.grouping)
        assertEquals("SOUGOU", basic.sortKey)
        assertFalse(basic.descending)
        assertTrue("ID" in basic.visibleColumns)
        assertTrue("SOUGOU" in basic.visibleColumns)
        assertTrue(RosterColumns.basic.filterNot { it.standard }.none { it.key in basic.visibleColumns })
        assertFalse("REMOVED" in basic.visibleColumns)
        assertEquals(mapOf("ID" to 120.5f, "NAME" to 220f), basic.columnWidths)
    }

    @Test
    fun unsupportedSchemaDoesNotAffectOtherApplicationSettings(
        @TempDir root: Path,
    ) {
        val settingsFile = root.resolve("config/player-roster.json")
        Files.createDirectories(settingsFile.parent)
        Files.writeString(settingsFile, """{"schemaVersion":99,"savePath":"C:\\\\save"}""")

        assertTrue(JsonPlayerRosterSettingsStore(settingsFile).load() is PlayerRosterSettingsLoadResult.Invalid)
    }

    @Test
    fun legacyPropertiesAreMigratedToJson(
        @TempDir root: Path,
    ) {
        val jsonFile = root.resolve("config/player-roster.json")
        val legacyFile = root.resolve("config/player-roster.properties")
        val savePath = root.resolve("save").toAbsolutePath().normalize()
        Files.createDirectories(legacyFile.parent)
        Files.writeString(
            legacyFile,
            """
            schemaVersion=1
            savePath=${savePath.toString().replace("\\", "\\\\")}
            filter.squad=SECOND
            grouping=POSITION
            display.BASIC.sortKey=AGE
            display.BASIC.descending=false
            display.BASIC.visibleColumns=ID,AGE
            display.BASIC.knownColumns=ID,AGE
            """.trimIndent(),
        )

        val result = JsonPlayerRosterSettingsStore(jsonFile, legacyFile).load() as PlayerRosterSettingsLoadResult.Loaded

        assertEquals(savePath, result.settings.savePath)
        assertEquals(PlayerSquad.SECOND, result.settings.squad)
        assertEquals(
            "AGE",
            result.settings.displays
                .getValue(RosterDisplay.BASIC)
                .sortKey,
        )
        assertTrue(Files.exists(jsonFile))
        assertFalse(Files.exists(legacyFile))
    }

    @Test
    fun starterRoleFilterRoundTrips(
        @TempDir root: Path,
    ) {
        val store = JsonPlayerRosterSettingsStore(root.resolve("config/player-roster.json"))
        val path = root.resolve("save").toAbsolutePath()
        store.save(
            PlayerRosterSettings.from(
                path,
                PlayerRosterOptions.forTeam(6).copy(positionFilter = RosterPositionFilter.Starters),
            ),
        )
        val loaded = store.load() as PlayerRosterSettingsLoadResult.Loaded
        assertEquals(
            RosterPositionFilter.Starters,
            loaded.settings.restore(PlayerRosterOptions.forTeam(6)).positionFilter,
        )
    }

    @Test
    fun clearDeletesRosterJsonAndLegacyPropertiesOnly(
        @TempDir root: Path,
    ) {
        val rosterFile = root.resolve("config/player-roster.json")
        val legacyFile = root.resolve("config/player-roster.properties")
        val appFile = root.resolve("config/settings.properties")
        Files.createDirectories(rosterFile.parent)
        Files.writeString(rosterFile, "roster")
        Files.writeString(legacyFile, "legacy roster")
        Files.writeString(appFile, "app")

        JsonPlayerRosterSettingsStore(rosterFile, legacyFile).clear()

        assertFalse(Files.exists(rosterFile))
        assertFalse(Files.exists(legacyFile))
        assertTrue(Files.exists(appFile))
    }
}
