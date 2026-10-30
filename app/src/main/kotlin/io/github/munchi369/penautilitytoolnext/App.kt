package io.github.munchi369.penautilitytoolnext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import io.github.munchi369.penautilitytoolnext.home.HomeScreen
import io.github.munchi369.penautilitytoolnext.lineup.LineupScreen
import io.github.munchi369.penautilitytoolnext.player.PlayerDetailScreen
import io.github.munchi369.penautilitytoolnext.player.PlayerRosterScreen
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataPhase
import io.github.munchi369.penautilitytoolnext.savedata.SaveDataScreen
import io.github.munchi369.penautilitytoolnext.ui.appTypography
import java.nio.file.Path

const val APPLICATION_TITLE = "Pena Utility Tool Next"

internal val appShellBackground = Color(0xFFF0F0F2)

internal enum class AppDestination {
    HOME,
    PLAYERS,
    LINEUP,
    SETTINGS,
}

@Composable
fun App(controllers: ApplicationControllers) {
    val saveDataState = controllers.saveData.state
    val playerRosterState = controllers.playerRoster.state
    var destination by remember { mutableStateOf(AppDestination.SETTINGS) }
    var startupResolved by remember { mutableStateOf(false) }
    var loadedPath by remember { mutableStateOf<Path?>(null) }
    val rosterScreenState = rememberSaveableStateHolder()
    val homeScreenState = rememberSaveableStateHolder()
    var detailReturnDestination by remember { mutableStateOf(AppDestination.PLAYERS) }
    var rosterPinned by remember { mutableStateOf(false) }
    val detailState = controllers.playerDetail.state

    val saveDataAvailable = saveDataState.phase == SaveDataPhase.AVAILABLE && saveDataState.target != null

    LaunchedEffect(saveDataState.phase, saveDataState.target?.path) {
        val targetPath = saveDataState.target?.path
        if (!startupResolved && !saveDataState.isBusy) {
            destination = if (saveDataAvailable) AppDestination.HOME else AppDestination.SETTINGS
            startupResolved = true
        }

        if (!saveDataAvailable) {
            controllers.playerDetail.close()
            destination = AppDestination.SETTINGS
            rosterPinned = false
            loadedPath = null
            controllers.teamIdentity.clear()
            controllers.lineup.clear()
            controllers.home.clear()
        } else if (targetPath != null && targetPath != loadedPath) {
            controllers.playerDetail.close()
            if (destination != AppDestination.PLAYERS) rosterPinned = false
            rosterScreenState.removeState("roster")
            homeScreenState.removeState("home")
            loadedPath = targetPath
            controllers.lineup.clear()
            controllers.teamIdentity.load(targetPath)
            controllers.playerRoster.load(targetPath)
            controllers.home.load(targetPath)
        }
    }

    LaunchedEffect(destination, loadedPath) {
        val path = loadedPath
        if (destination == AppDestination.LINEUP && path != null && controllers.lineup.state.targetPath == null) {
            controllers.lineup.load(path)
        }
    }

    MaterialTheme(colorScheme = rosterColorScheme, typography = appTypography) {
        val teamIdentity =
            controllers.teamIdentity.identity.takeIf {
                saveDataAvailable && controllers.teamIdentity.targetPath == saveDataState.target.path
            }
        Surface(modifier = Modifier.fillMaxSize(), color = appShellBackground) {
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigation(
                    destination = destination,
                    playersEnabled = saveDataAvailable,
                    team = teamIdentity,
                    teamStatus =
                        when {
                            saveDataState.target == null -> "セーブデータ未選択"
                            saveDataState.isBusy || controllers.teamIdentity.isLoading -> "読み込み中"
                            !saveDataAvailable -> "セーブデータを利用できません"
                            else -> "チーム情報を取得できません"
                        },
                    onPlayers = {
                        controllers.playerDetail.close()
                        rosterPinned = true
                        destination = AppDestination.PLAYERS
                    },
                    onSettings = { destination = AppDestination.SETTINGS },
                    onLineup = {
                        controllers.playerDetail.close()
                        destination = AppDestination.LINEUP
                    },
                    onHome = {
                        controllers.playerDetail.close()
                        destination = AppDestination.HOME
                    },
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (destination == AppDestination.HOME || destination == AppDestination.LINEUP) {
                                    Color(0xFFF8F8FA)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                ) {
                    PageHeader(controllers.pageHeader(destination))
                    Box(modifier = Modifier.fillMaxSize()) {
                        val rosterVisible =
                            destination == AppDestination.PLAYERS &&
                                detailState.player == null &&
                                detailState.requestedPlayerId == null
                        // Keep the table composed after the first visit. Later visits only show it again.
                        if (rosterPinned && loadedPath != null) {
                            key(loadedPath) {
                                Box(Modifier.keptBeneath(rosterVisible).fillMaxSize()) {
                                    rosterScreenState.SaveableStateProvider("roster") {
                                        PlayerRosterScreen(
                                            state = playerRosterState,
                                            options = controllers.playerRoster.options,
                                            onOptionsChange = controllers.playerRoster::updateOptions,
                                            onOpenSettings = { destination = AppDestination.SETTINGS },
                                            settingsNotice = controllers.playerRoster.settingsNotice,
                                            onDismissSettingsNotice = controllers.playerRoster::dismissSettingsNotice,
                                            onOpenPlayer = { player ->
                                                val path = playerRosterState.targetPath
                                                val roster = playerRosterState.roster
                                                if (path != null && roster != null) {
                                                    detailReturnDestination = AppDestination.PLAYERS
                                                    controllers.playerDetail.open(
                                                        path,
                                                        player,
                                                        roster,
                                                        controllers.playerRoster.options,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (!rosterVisible) {
                            when (destination) {
                                AppDestination.HOME ->
                                    homeScreenState.SaveableStateProvider("home") {
                                        HomeScreen(
                                            controller = controllers.home,
                                            rosterState = playerRosterState,
                                            onOpenCalendarPlayer = { playerId ->
                                                val path = controllers.home.path
                                                val data = controllers.home.data
                                                val name = data?.players?.get(playerId)
                                                if (path != null && name != null) {
                                                    controllers.playerDetail.openById(path, playerId, name, data.teamColors)
                                                    detailReturnDestination = AppDestination.HOME
                                                    destination = AppDestination.PLAYERS
                                                }
                                            },
                                            onOpenPlayer = { player ->
                                                val path = playerRosterState.targetPath
                                                val roster = playerRosterState.roster
                                                if (path != null && roster != null) {
                                                    controllers.playerDetail.open(
                                                        path,
                                                        player,
                                                        roster,
                                                        controllers.playerRoster.options,
                                                        selectPitchingByPosition = true,
                                                    )
                                                    detailReturnDestination = AppDestination.HOME
                                                    destination = AppDestination.PLAYERS
                                                }
                                            },
                                        )
                                    }

                                AppDestination.PLAYERS ->
                                    PlayerDetailScreen(
                                        controllers.playerDetail,
                                        onBack = {
                                            controllers.playerDetail.close()
                                            destination = detailReturnDestination
                                        },
                                        backLabel =
                                            if (detailReturnDestination == AppDestination.HOME) "← ホームへ戻る" else "← 一覧へ戻る",
                                    )

                                AppDestination.LINEUP -> LineupScreen(controllers.lineup, teamIdentity)
                                AppDestination.SETTINGS -> SaveDataScreen(controllers.saveData)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.keptBeneath(visible: Boolean): Modifier =
    if (visible) {
        this
    } else {
        this
            .clearAndSetSemantics {}
            .focusProperties { onEnter = { cancelFocusChange() } }
            .layout { _, _ -> layout(0, 0) {} }
    }

private class PageHeaderState(
    val title: String,
    val canReload: Boolean = false,
    val isReloading: Boolean = false,
    val onReload: () -> Unit = {},
)

private fun ApplicationControllers.pageHeader(destination: AppDestination): PageHeaderState {
    val detail = playerDetail.state
    if ((detail.player != null || detail.requestedPlayerId != null) && destination == AppDestination.PLAYERS) {
        return PageHeaderState("選手詳細", !detail.loading, detail.loading, playerDetail::reload)
    }
    return when (destination) {
        AppDestination.HOME ->
            PageHeaderState("ホーム", !home.loading && home.path != null, home.loading) {
                home.reload()
                playerRoster.reload()
            }
        AppDestination.PLAYERS ->
            PageHeaderState(
                "現役選手一覧",
                playerRoster.state.canReload,
                playerRoster.state.isLoading,
                playerRoster::reload,
            )
        AppDestination.LINEUP -> PageHeaderState("起用", lineup.state.canReload, lineup.state.isLoading, lineup::reload)
        AppDestination.SETTINGS -> PageHeaderState("設定")
    }
}

@Composable
private fun PageHeader(header: PageHeaderState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp),
    ) {
        Text(
            text = header.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (header.canReload || header.isReloading) {
            ReloadButton(header.isReloading, header.canReload, header.onReload)
        }
    }
}

@Composable
private fun ReloadButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = if (loading) "読み込み中" else "再読み込み",
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

fun main() =
    application {
        val controllers = remember { ApplicationControllers.createDefault() }
        DisposableEffect(controllers) {
            controllers.start()
            onDispose(controllers::close)
        }

        ApplicationWindow(controllers.windowPreferences, onClose = ::exitApplication) {
            App(controllers)
        }
    }

internal val rosterColorScheme =
    lightColorScheme(
        primary = Color(0xFF5E6AD2),
        onPrimary = Color.White,
        secondary = Color(0xFF5E6AD2),
        tertiary = Color(0xFF5E6AD2),
        background = Color(0xFFF7F7F8),
        surface = Color.White,
        surfaceContainer = Color(0xFFF3F3F4),
        surfaceContainerLow = Color(0xFFF7F7F8),
        primaryContainer = Color(0xFFEEF0FC),
        onPrimaryContainer = Color(0xFF404BA8),
        secondaryContainer = Color(0xFFEEF0FC),
        onSecondaryContainer = Color(0xFF1F2328),
        onSurface = Color(0xFF1A1C1F),
        onSurfaceVariant = Color(0xFF6B6E76),
        outline = Color(0xFFD4D4D8),
        outlineVariant = Color(0xFFE7E7EA),
    )
