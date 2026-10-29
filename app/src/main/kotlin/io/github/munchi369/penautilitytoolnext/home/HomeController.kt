package io.github.munchi369.penautilitytoolnext.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.munchi369.penautilitytoolnext.savedata.LatestRequest
import java.nio.file.Path
import java.util.concurrent.Executor

internal fun interface HomeLoader {
    fun load(path: Path): Result<HomeData>
}

internal class HomeController(
    private val loader: HomeLoader,
    background: Executor,
    ui: Executor,
) {
    private val request = LatestRequest(background, ui)
    var path: Path? = null
        private set
    var data by mutableStateOf<HomeData?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var month by mutableStateOf(1)
        private set
    var selectedDay by mutableStateOf<Int?>(null)
        private set
    var showingMatch by mutableStateOf(false)
        private set

    fun clear() {
        request.cancel()
        path = null
        data = null
        error = null
        loading = false
        selectedDay = null
        showingMatch = false
    }

    fun load(target: Path) {
        val old = data
        val sameTarget = path == target
        if (!sameTarget) clear()
        path = target
        loading = true
        error = null
        request.run(work = { loader.load(target) }, onFailure = { Result.failure(it) }) { result ->
            loading = false
            data = result.getOrNull()
            error = result.exceptionOrNull()?.let { "ホームを読み込めません。再読み込みしてください。\n${it.message.orEmpty()}" }
            val loaded = data
            if (loaded != null && (!sameTarget || old?.season != loaded.season)) {
                month = loaded.today?.month ?: loaded.days.firstOrNull()?.month ?: 1
                selectedDay = loaded.today?.day
                showingMatch = false
            } else if (loaded == null || loaded.days.none { it.day == selectedDay && loaded.calendarGamesOn(it).isNotEmpty() }) {
                showingMatch = false
            }
        }
    }

    fun reload() {
        path?.let(::load)
    }

    fun changeMonth(value: Int) {
        month = value.coerceIn(1, 12)
        showingMatch = false
    }

    fun today() {
        data?.today?.let {
            month = it.month
            selectedDay = it.day
            showingMatch = false
        }
    }

    fun select(day: SeasonDay) {
        selectedDay = day.day
        showingMatch = false
    }

    fun openMatch(day: SeasonDay) {
        selectedDay = day.day
        showingMatch = data?.calendarGamesOn(day)?.isNotEmpty() == true
    }

    fun focusDay(day: SeasonDay) {
        month = day.month
        selectedDay = day.day
        showingMatch = false
    }

    fun focusMonth(value: Int) {
        month = value.coerceIn(1, 12)
        selectedDay = null
        showingMatch = false
    }

    fun backToCalendar() {
        showingMatch = false
    }
}
