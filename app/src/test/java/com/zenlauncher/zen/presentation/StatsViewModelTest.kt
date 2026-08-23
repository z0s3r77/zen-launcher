package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.model.SessionOutcome
import com.zenlauncher.zen.domain.model.ZenSession
import com.zenlauncher.zen.domain.stats.ZenStats
import com.zenlauncher.zen.fakes.FakeSessionRepository
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.presentation.stats.StatsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun session(id: String, minutes: Int, completed: Boolean) = ZenSession(
        id = id,
        startedAtMillis = 0,
        endedAtMillis = minutes * 60_000L,
        plannedDurationMillis = minutes * 60_000L,
        actualDurationMillis = minutes * 60_000L,
        initialBatteryPercent = 84,
        finalBatteryPercent = 80,
        initialCharging = false,
        finalCharging = false,
        outcome = if (completed) SessionOutcome.COMPLETED else SessionOutcome.ABANDONED,
        restrictedAppsCount = 4,
    )

    @Test
    fun `empieza vacio y se actualiza al registrarse una sesion`() = runTest {
        val repository = FakeSessionRepository()
        val model = StatsViewModel(repository)

        model.state.test {
            assertEquals(ZenStats.Empty, awaitItem())

            repository.recordIfAbsent(session("a", minutes = 30, completed = true))

            val afterFirst = awaitItem()
            assertEquals(30 * 60_000L, afterFirst.totalZenMillis)
            assertEquals(1, afterFirst.completedCount)
            assertEquals(100, afterFirst.completionRatePercent)
            assertTrue(!afterFirst.isEmpty)

            repository.recordIfAbsent(session("b", minutes = 10, completed = false))

            val afterSecond = awaitItem()
            assertEquals(40 * 60_000L, afterSecond.totalZenMillis)
            assertEquals(1, afterSecond.abandonedCount)
            assertEquals(50, afterSecond.completionRatePercent)
            assertEquals(30 * 60_000L, afterSecond.longestSessionMillis)
        }
    }
}
