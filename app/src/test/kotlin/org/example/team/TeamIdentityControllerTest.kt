package org.example.team

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.Executor

class TeamIdentityControllerTest {
    @Test
    fun obsoleteResultsCannotReplaceTheNewSaveOrRestoreClearedIdentity() {
        val queued = mutableListOf<Runnable>()
        val controller =
            TeamIdentityController(
                loader = { path -> TeamIdentity(path.toString(), 0xFFFF00) },
                backgroundExecutor = Executor(queued::add),
                uiExecutor = Executor(Runnable::run),
            )
        controller.load(Path.of("first"))
        controller.load(Path.of("second"))
        queued.removeAt(1).run()
        queued.removeAt(0).run()
        assertEquals("second", controller.identity?.name)
        assertEquals(0xFFFF00, controller.identity?.rgb)

        controller.load(Path.of("third"))
        assertNull(controller.identity)
        controller.clear()
        queued.removeAt(0).run()
        assertNull(controller.identity)
        assertNull(controller.targetPath)
        assertFalse(controller.isLoading)
    }
}
