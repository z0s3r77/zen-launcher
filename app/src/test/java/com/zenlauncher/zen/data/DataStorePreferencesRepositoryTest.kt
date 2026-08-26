package com.zenlauncher.zen.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zenlauncher.zen.data.prefs.DataStorePreferencesRepository
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.weather.WeatherCondition
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Cada test estrena fichero: DataStore admite una unica instancia por fichero, asi que
 * compartirlo entre tests los volveria dependientes del orden de ejecucion.
 */
class DataStorePreferencesRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var repository: DataStorePreferencesRepository

    private fun storeOn(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = DataStorePreferencesRepository(
            storeOn(temporaryFolder.newFile("zen.preferences_pb"), scope),
        )
    }

    @Test
    fun `sin nada guardado devuelve los valores por defecto`() = runTest {
        assertTrue(repository.restrictedPackages.first().isEmpty())
        assertTrue(repository.favouritePackages.first().isEmpty())
        assertEquals(
            ZenDuration.Default.wholeMinutes,
            repository.preferredDuration.first().wholeMinutes,
        )
        assertNull(repository.activeSession.first())
    }

    @Test
    fun `marca y desmarca aplicaciones restringidas`() = runTest {
        repository.setRestricted("com.instagram.android", true)
        repository.setRestricted("com.zhiliaoapp.musically", true)

        assertEquals(
            setOf("com.instagram.android", "com.zhiliaoapp.musically"),
            repository.currentRestrictedPackages(),
        )

        repository.setRestricted("com.instagram.android", false)

        assertEquals(setOf("com.zhiliaoapp.musically"), repository.currentRestrictedPackages())
    }

    @Test
    fun `los favoritos conservan el orden elegido por el usuario`() = runTest {
        val order = listOf("com.c", "com.a", "com.b")

        repository.setFavourites(order)

        // Un Set habria perdido el orden; por eso se guardan como lista.
        assertEquals(order, repository.favouritePackages.first())
    }

    @Test
    fun `una lista de favoritos vacia se lee como vacia y no como un elemento en blanco`() =
        runTest {
            repository.setFavourites(listOf("com.a"))
            repository.setFavourites(emptyList())

            assertTrue(repository.favouritePackages.first().isEmpty())
        }

    @Test
    fun `guarda y limpia la sesion activa entera`() = runTest {
        val session = ActiveSession(
            id = "abc",
            startedAtWallMillis = 1_700_000_000_000,
            startedAtElapsedMillis = 500_000,
            plannedDurationMillis = 30 * 60_000L,
            initialBatteryPercent = 84,
            initialCharging = true,
            restrictedAppsCount = 6,
        )

        repository.putActiveSession(session)

        assertEquals(session, repository.currentActiveSession())

        repository.clearActiveSession()

        assertNull(repository.currentActiveSession())
    }

    @Test
    fun `la sesion activa sobrevive a cerrar y reabrir el almacen`() = runTest {
        val file = temporaryFolder.newFile("persistente.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val session = ActiveSession(
            id = "persistente",
            startedAtWallMillis = 42,
            startedAtElapsedMillis = 7,
            plannedDurationMillis = 15 * 60_000L,
            initialBatteryPercent = 50,
            initialCharging = false,
            restrictedAppsCount = 1,
        )

        DataStorePreferencesRepository(storeOn(file, firstScope)).putActiveSession(session)
        // Cerrar el almacen: es lo mas parecido a que el proceso muera.
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = DataStorePreferencesRepository(storeOn(file, secondScope))
            assertEquals(session, reopened.currentActiveSession())
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun `el resumen pendiente se guarda y se borra`() = runTest {
        assertNull(repository.pendingSummarySessionId.first())

        repository.setPendingSummary("sesion-7")
        assertEquals("sesion-7", repository.pendingSummarySessionId.first())

        repository.clearPendingSummary()
        assertNull(repository.pendingSummarySessionId.first())
    }

    @Test
    fun `el resumen pendiente sobrevive a cerrar y reabrir el almacen`() = runTest {
        // Es lo que permite que un resumen espere al usuario aunque el proceso muera
        // entre que la alarma cierra la sesion y el usuario vuelve a abrir Zen.
        val file = temporaryFolder.newFile("resumen.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        DataStorePreferencesRepository(storeOn(file, firstScope)).setPendingSummary("sesion-9")
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = DataStorePreferencesRepository(storeOn(file, secondScope))
            assertEquals("sesion-9", reopened.pendingSummarySessionId.first())
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun `guarda la duracion preferida`() = runTest {
        repository.setPreferredDuration(ZenDuration.ofMinutes(90))

        assertEquals(90, repository.preferredDuration.first().wholeMinutes)
    }
    @Test
    fun `la ciudad del tiempo sobrevive con sus coordenadas`() = runTest {
        val madrid = WeatherPlace("Madrid, España", 40.4165, -3.7026)

        repository.setWeatherPlace(madrid)

        val leido = repository.weatherPlace.first()
        assertEquals(madrid, leido)
        // Los decimales importan: redondear la latitud mueve la consulta de ciudad.
        assertEquals(40.4165, leido!!.latitude, 0.00001)
    }

    @Test
    fun `la ultima lectura del tiempo se guarda con su hora`() = runTest {
        repository.setWeatherPlace(WeatherPlace("Madrid", 40.41, -3.70))
        val lectura = WeatherReading(18, WeatherCondition.LLUVIA, 1_700_000_000_000)

        repository.setLastWeather(lectura)

        assertEquals(lectura, repository.lastWeather.first())
    }

    /**
     * Regresion: el dato de la ciudad anterior sobreviviendo a un cambio de ciudad
     * saldria en la franja de la pantalla de inicio sin nada que lo explique, y ademas
     * seria de un sitio donde el usuario ya no esta.
     */
    @Test
    fun `cambiar de ciudad tira lo que se sabia de la anterior`() = runTest {
        repository.setWeatherPlace(WeatherPlace("Madrid", 40.41, -3.70))
        repository.setLastWeather(WeatherReading(18, WeatherCondition.DESPEJADO, 1_000L))
        repository.setLastWeatherAttemptAt(1_000L)

        repository.setWeatherPlace(WeatherPlace("Oviedo", 43.36, -5.84))

        assertNull(repository.lastWeather.first())
        // Y el intento tambien, para que la ciudad nueva se pida ya y no dentro de media hora.
        assertNull(repository.lastWeatherAttemptAtMillis.first())
    }

    @Test
    fun `quitar la ciudad lo apaga todo`() = runTest {
        repository.setWeatherPlace(WeatherPlace("Madrid", 40.41, -3.70))
        repository.setLastWeather(WeatherReading(18, WeatherCondition.DESPEJADO, 1_000L))

        repository.setWeatherPlace(null)

        assertNull(repository.weatherPlace.first())
        assertNull(repository.lastWeather.first())
    }

    /**
     * Regresion: `MutablePreferences.remove` devuelve el valor borrado, y sobre una
     * clave ausente eso es un null que Kotlin desempaqueta a `long` y revienta. Limpiar
     * una sesion que no existe pasa de verdad —al arrancar tras una limpieza del
     * sistema— y una excepcion ahi deja el telefono sin pantalla de inicio.
     */
    @Test
    fun `limpiar una sesion que no existe no revienta`() = runTest {
        repository.clearActiveSession()
        repository.clearPendingSummary()

        assertNull(repository.activeSession.first())
        assertNull(repository.pendingSummarySessionId.first())
    }

}
