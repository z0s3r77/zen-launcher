package com.zenlauncher.zen.data

import app.cash.turbine.test
import com.zenlauncher.zen.data.apps.CachedInstalledApps
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.fakes.installedApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Un repositorio que cuenta cuantas veces alguien empieza a leerlo: en el de verdad,
 * cada una de esas veces es un `LauncherApps.getActivityList` completo —IPC mas disco,
 * con el rotulo de cada aplicacion cruzando el proceso— y un callback registrado.
 */
private class CountingApps(apps: List<InstalledApp>) : InstalledAppsRepository {

    val state = MutableStateFlow(apps)
    var reads = 0
        private set

    override fun observeInstalledApps(): Flow<List<InstalledApp>> =
        state.onStart { reads++ }

    override suspend fun launchableApps(): List<InstalledApp> = state.value
    override fun launch(app: InstalledApp): Boolean = true
    override suspend fun launchPackage(packageName: String): Boolean = true
}

@OptIn(ExperimentalCoroutinesApi::class)
class CachedInstalledAppsTest {

    private val apps = listOf(installedApp("com.whatsapp", "WhatsApp"))

    /**
     * La razon de que exista la clase: media docena de pantallas observan las
     * aplicaciones instaladas y sin compartir la fuente cada una abria su propia
     * consulta por IPC.
     */
    @Test
    fun `varios observadores comparten una sola lectura`() = runTest {
        val delegate = CountingApps(apps)
        val cached = CachedInstalledApps(delegate, backgroundScope)

        cached.observeInstalledApps().test {
            awaitItem()
            cached.observeInstalledApps().test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()

        assertEquals(1, delegate.reads)
    }

    /**
     * Regresion: la pantalla de inicio pintaba la reticula vacia y la rellenaba cuando
     * volvia el IPC, asi que volver a Zen desde cualquier aplicacion ensenaba un hueco
     * durante un instante. Con la lista cacheada, el primer fotograma ya la lleva.
     */
    @Test
    fun `la lista cacheada se emite antes de volver a leer`() = runTest {
        val delegate = CountingApps(apps)
        val cached = CachedInstalledApps(delegate, backgroundScope)

        cached.observeInstalledApps().test {
            assertEquals(apps, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // Se deja caducar la fuente compartida: al volver, la unica via de tener la
        // lista en el primer elemento emitido es la cache.
        advanceTimeBy(60_000)
        advanceUntilIdle()

        cached.observeInstalledApps().test {
            assertEquals(apps, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `soltar la cache la vacia`() = runTest {
        val delegate = CountingApps(apps)
        val cached = CachedInstalledApps(delegate, backgroundScope)

        cached.observeInstalledApps().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        advanceTimeBy(60_000)
        advanceUntilIdle()

        cached.release()
        delegate.state.value = emptyList()

        cached.observeInstalledApps().test {
            // Sin soltar, aqui llegaba primero la lista vieja cacheada.
            assertEquals(emptyList<InstalledApp>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
