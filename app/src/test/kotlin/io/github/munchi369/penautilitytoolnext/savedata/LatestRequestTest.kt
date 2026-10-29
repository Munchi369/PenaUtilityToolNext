package io.github.munchi369.penautilitytoolnext.savedata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor

class LatestRequestTest {
    private val queued = mutableListOf<Runnable>()
    private val request = LatestRequest(Executor { queued.add(it) }, Executor(Runnable::run))

    private fun runQueued() {
        while (queued.isNotEmpty()) queued.removeAt(0).run()
    }

    @Test
    fun supersededReadsAreSkippedAndOnlyTheNewestIsDelivered() {
        val started = mutableListOf<Int>()
        val delivered = mutableListOf<Int>()
        for (value in 1..3) {
            request.run(work = { value.also(started::add) }, onFailure = { -1 }) { delivered += it }
        }

        runQueued()

        assertEquals(listOf(3), started)
        assertEquals(listOf(3), delivered)
    }

    @Test
    fun writesRunEvenWhenSupersededButOnlyTheNewestResultIsDelivered() {
        val started = mutableListOf<Int>()
        val delivered = mutableListOf<Int>()
        for (value in 1..2) {
            request.run(work = { value.also(started::add) }, onFailure = { -1 }, skipIfSuperseded = false) { delivered += it }
        }

        runQueued()

        assertEquals(listOf(1, 2), started)
        assertEquals(listOf(2), delivered)
    }

    @Test
    fun unexpectedExceptionsBecomeResultsAndCancelDropsThem() {
        val delivered = mutableListOf<String>()
        request.run(work = { error("broken") }, onFailure = { it.message.orEmpty() }) { delivered += it }
        runQueued()
        request.run(work = { "late" }, onFailure = { "" }, skipIfSuperseded = false) { delivered += it }
        request.cancel()
        runQueued()

        assertEquals(listOf("broken"), delivered)
    }
}
