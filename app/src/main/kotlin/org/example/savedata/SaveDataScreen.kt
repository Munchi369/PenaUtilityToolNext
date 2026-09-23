package org.example.savedata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.APP_VERSION
import org.example.ui.TechnicalDetails
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser

private val checkedAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun SaveDataScreen(controller: SaveDataController) {
    val state = controller.state
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 28.dp)
                .fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "セーブデータ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "penanto3のセーブデータを選択し、利用できる状態か確認します。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SaveDataStatusCard(
            state = state,
            onChoose = {
                chooseSaveDataDirectory(state.target?.path)?.let(controller::choose)
            },
            onRetry = controller::retry,
            onReturnToAutomatic = controller::returnToAutomaticSelection,
            onResetSettings = controller::resetSettings,
        )

        Text(
            text = "バージョン $APP_VERSION",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaveDataStatusCard(
    state: SaveDataViewState,
    onChoose: () -> Unit,
    onRetry: () -> Unit,
    onReturnToAutomatic: () -> Unit,
    onResetSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.titleLarge,
                    color = statusColor(state.phase),
                    fontWeight = FontWeight.Medium,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusField(label = "パス", value = state.displayPath ?: "未選択")
                StatusField(label = "選択元", value = state.origin?.displayName ?: "なし")
                state.checkedAt?.let {
                    StatusField(label = "最終確認", value = checkedAtFormatter.format(it))
                }
            }

            if (state.selectionLocked) {
                Text(
                    text = "起動設定で接続先が固定されています。変更するには起動指定を外して再起動してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    enabled = state.canChoose,
                    onClick = onChoose,
                ) {
                    Text("セーブデータを選択")
                }

                if (state.target != null) {
                    OutlinedButton(
                        enabled = state.canRetry,
                        onClick = onRetry,
                    ) {
                        Text("再確認")
                    }
                }
            }

            if (state.canReturnToAutomatic || state.hasSavedSelection) {
                TextButton(
                    enabled = state.canReturnToAutomatic,
                    onClick = onReturnToAutomatic,
                ) {
                    Text("自動選択に戻す")
                }
            }

            if (state.canResetSettings || state.phase == SaveDataPhase.SETTINGS_ERROR) {
                Button(
                    enabled = state.canResetSettings,
                    onClick = onResetSettings,
                ) {
                    Text("設定を初期化")
                }
            }

            state.technicalDetails?.let {
                HorizontalDivider()
                TechnicalDetails(it)
            }
        }
    }
}

@Composable
private fun StatusField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun statusColor(phase: SaveDataPhase): Color =
    when (phase) {
        SaveDataPhase.AVAILABLE -> MaterialTheme.colorScheme.primary
        SaveDataPhase.NOT_SAVE_DATABASE,
        SaveDataPhase.IN_USE,
        SaveDataPhase.UNAVAILABLE,
        SaveDataPhase.SETTINGS_ERROR,
        SaveDataPhase.SETTINGS_SAVE_ERROR,
        -> MaterialTheme.colorScheme.error

        else -> MaterialTheme.colorScheme.onSurface
    }

private fun chooseSaveDataDirectory(currentPath: Path?): Path? {
    val initialDirectory =
        currentPath?.let { path ->
            when {
                Files.isDirectory(path) -> path
                Files.isDirectory(path.parent) -> path.parent
                else -> null
            }
        }
    val chooser =
        JFileChooser(initialDirectory?.toFile()).apply {
            dialogTitle = "セーブデータを選択"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath()
    } else {
        null
    }
}
