package com.zenlauncher.zen.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.zenlauncher.zen.data.db.SqliteSessionRepository
import com.zenlauncher.zen.domain.model.SessionOutcome
import com.zenlauncher.zen.domain.model.ZenSession
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric para ejercitar SQLite de verdad: la persistencia es justo la parte que un
 * doble en memoria no probaria.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SqliteSessionRepositoryTest {

    private lateinit var repository: SqliteSessionRepository

    @Before
    fun setUp() {
        repository = SqliteSessionRepository(
            context = ApplicationProvider.getApplicationContext(),
            io = UnconfinedTestDispatcher(),
        )
    }

    private fun session(
        id: String,
        startedAt: Long = 1_000L,
        completed: Boolean = true,
    ) = ZenSession(
        id = id,
        startedAtMillis = startedAt,
        endedAtMillis = startedAt + 30 * 60_000L,
        plannedDurationMillis = 30 * 60_000L,
        actualDurationMillis = 30 * 60_000L,
        initialBatteryPercent = 84,
        finalBatteryPercent = 80,
        initialCharging = false,
        finalCharging = false,
        outcome = if (completed) SessionOutcome.COMPLETED else SessionOutcome.ABANDONED,
        restrictedAppsCount = 4,
    )

    @Test
    fun `guarda y recupera una sesion con todos sus campos`() = runTest {
        val original = session("a", completed = false)

        assertTrue(repository.recordIfAbsent(original))

        val stored = repository.all().single()
        assertEquals(original, stored)
        assertEquals(SessionOutcome.ABANDONED, stored.outcome)
        assertFalse(stored.completed)
    }

    @Test
    fun `recordIfAbsent ignora un id que ya existe`() = runTest {
        repository.recordIfAbsent(session("a", startedAt = 1_000L))
        val duplicate = session("a", startedAt = 9_999L)

        assertFalse(repository.recordIfAbsent(duplicate))

        // Se conserva la primera, no la segunda.
        assertEquals(1, repository.all().size)
        assertEquals(1_000L, repository.all().single().startedAtMillis)
    }

    @Test
    fun `devuelve las sesiones de la mas reciente a la mas antigua`() = runTest {
        repository.recordIfAbsent(session("vieja", startedAt = 1_000L))
        repository.recordIfAbsent(session("nueva", startedAt = 9_000L))
        repository.recordIfAbsent(session("media", startedAt = 5_000L))

        assertEquals(
            listOf("nueva", "media", "vieja"),
            repository.all().map { it.id },
        )
    }

    @Test
    fun `los datos sobreviven a recrear el repositorio`() = runTest {
        repository.recordIfAbsent(session("persistente"))

        val reopened = SqliteSessionRepository(
            context = ApplicationProvider.getApplicationContext(),
            io = UnconfinedTestDispatcher(),
        )

        assertEquals("persistente", reopened.all().single().id)
    }

    @Test
    fun `busca una sesion por id`() = runTest {
        repository.recordIfAbsent(session("a"))
        repository.recordIfAbsent(session("b", startedAt = 5_000L))

        assertEquals(5_000L, repository.find("b")?.startedAtMillis)
        assertNull(repository.find("no-existe"))
    }

    @Test
    fun `deleteAll vacia el registro`() = runTest {
        repository.recordIfAbsent(session("a"))
        repository.recordIfAbsent(session("b"))

        repository.deleteAll()

        assertTrue(repository.all().isEmpty())
    }
}
