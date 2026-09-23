package org.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.example.player.PlayerDetailScreen
import org.example.player.PlayerRosterScreen
import org.example.savedata.SaveDataPhase
import org.example.savedata.SaveDataScreen
import java.nio.file.Path

const val APPLICATION_TITLE = "Pena Utility Tool Next"

internal enum class AppDestination {
    PLAYERS,
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
    val detailState = controllers.playerDetail.state

    val saveDataAvailable = saveDataState.phase == SaveDataPhase.AVAILABLE && saveDataState.target != null

    LaunchedEffect(saveDataState.phase, saveDataState.target?.path) {
        val targetPath = saveDataState.target?.path
        if (!startupResolved && !saveDataState.isBusy) {
            destination = if (saveDataAvailable) AppDestination.PLAYERS else AppDestination.SETTINGS
            startupResolved = true
        }

        if (!saveDataAvailable) {
            controllers.playerDetail.close()
            destination = AppDestination.SETTINGS
            loadedPath = null
            controllers.teamIdentity.clear()
        } else if (targetPath != null && targetPath != loadedPath) {
            controllers.playerDetail.close()
            rosterScreenState.removeState("roster")
            loadedPath = targetPath
            controllers.teamIdentity.load(targetPath)
            controllers.playerRoster.load(targetPath)
        }
    }

    MaterialTheme(colorScheme = rosterColorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigation(
                    destination = destination,
                    playersEnabled = saveDataAvailable,
                    team =
                        controllers.teamIdentity.identity.takeIf {
                            saveDataAvailable && controllers.teamIdentity.targetPath == saveDataState.target.path
                        },
                    teamStatus =
                        when {
                            saveDataState.target == null -> "セーブデータ未選択"
                            saveDataState.isBusy || controllers.teamIdentity.isLoading -> "読み込み中"
                            !saveDataAvailable -> "セーブデータを利用できません"
                            else -> "チーム情報を取得できません"
                        },
                    onPlayers = {
                        controllers.playerDetail.close()
                        destination = AppDestination.PLAYERS
                    },
                    onSettings = { destination = AppDestination.SETTINGS },
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface),
                ) {
                    PageHeader(
                        title =
                            when (destination) {
                                AppDestination.PLAYERS -> if (detailState.player == null) "現役選手一覧" else "選手詳細"
                                AppDestination.SETTINGS -> "設定"
                            },
                        canReload =
                            destination == AppDestination.PLAYERS &&
                                if (detailState.player == null) playerRosterState.canReload else !detailState.loading,
                        isReloading =
                            destination == AppDestination.PLAYERS &&
                                if (detailState.player == null) playerRosterState.isLoading else detailState.loading,
                        onReload = {
                            if (detailState.player == null) controllers.playerRoster.reload() else controllers.playerDetail.reload()
                        },
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (destination) {
                            AppDestination.PLAYERS ->
                                if (detailState.player != null) {
                                    PlayerDetailScreen(controllers.playerDetail, controllers.playerDetail::close)
                                } else {
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
                                                    controllers.playerDetail.open(path, player, roster, controllers.playerRoster.options)
                                                }
                                            },
                                        )
                                    }
                                }

                            AppDestination.SETTINGS -> SaveDataScreen(controllers.saveData)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    canReload: Boolean,
    isReloading: Boolean,
    onReload: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (canReload || isReloading) {
            OutlinedButton(
                enabled = canReload,
                onClick = onReload,
            ) {
                Text(if (isReloading) "読み込み中" else "再読み込み")
            }
        }
    }
}

fun main() =
    application {
        val controllers = remember { ApplicationControllers.createDefault() }
        DisposableEffect(controllers) {
            controllers.start()
            onDispose(controllers::close)
        }

        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1440.dp, height = 900.dp),
            title = APPLICATION_TITLE,
        ) {
            App(controllers)
        }
    }

internal val rosterColorScheme =
    lightColorScheme(
        primary = Color(0xFF23344D),
        onPrimary = Color.White,
        background = Color(0xFFF0F3F6),
        surface = Color.White,
        surfaceContainer = Color(0xFFF0F3F6),
        surfaceContainerLow = Color(0xFFF7F9FA),
        secondaryContainer = Color(0xFFE6EBF0),
        onSecondaryContainer = Color(0xFF18263B),
        onSurface = Color(0xFF172338),
        onSurfaceVariant = Color(0xFF5B687D),
        outline = Color(0xFFB9C3CF),
        outlineVariant = Color(0xFFE4E9EE),
    )
