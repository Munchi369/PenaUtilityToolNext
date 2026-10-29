package io.github.munchi369.penautilitytoolnext.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.munchi369.penautilitytoolnext.rosterColorScheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ua.wwind.table.Table
import ua.wwind.table.config.TableSettings
import ua.wwind.table.state.TableState
import ua.wwind.table.state.rememberTableState
import ua.wwind.table.tableColumns
import javax.swing.SwingUtilities

@OptIn(ExperimentalComposeUiApi::class)
class WhiteWindTableCompatibilityTest {
    @Test
    fun rendersWithApplicationThemeAndHandlesHeaderInput() {
        SwingUtilities.invokeAndWait {
            lateinit var state: TableState<String>
            val scene =
                ImageComposeScene(640, 480) {
                    MaterialTheme(colorScheme = rosterColorScheme) {
                        val columns =
                            remember {
                                tableColumns<String, String, Unit> {
                                    column("name", valueOf = { it }) {
                                        header("名前")
                                        cell { name, _ -> Text(name) }
                                        sortable()
                                    }
                                }
                            }
                        state =
                            rememberTableState(
                                columns = persistentListOf("name"),
                                settings = TableSettings(pinnedColumnsCount = 1),
                            )
                        Table(
                            itemsCount = 1,
                            itemAt = { "互換性確認" },
                            state = state,
                            columns = columns,
                        )
                    }
                }
            try {
                repeat(3) { scene.render().close() }
                val nodes = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }

                fun SemanticsNode.hasText(value: String): Boolean =
                    config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true

                assertTrue(nodes.any { it.hasText("互換性確認") })
                val header = nodes.first { it.hasText("名前") }
                scene.sendPointerEvent(PointerEventType.Press, header.boundsInWindow.center)
                scene.sendPointerEvent(PointerEventType.Release, header.boundsInWindow.center)
                repeat(3) { scene.render().close() }
                assertEquals("name", state.sort?.column)
            } finally {
                scene.close()
            }
        }
    }

    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
}
