package com.zenlauncher.zen.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zenlauncher.zen.data.prefs.DataStorePreferencesRepository
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsHeadline
import com.zenlauncher.zen.domain.news.NewsPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * La portada guardada, de punta a punta: se escribe en DataStore y se vuelve a leer.
 *
 * Aparte del resto de preferencias y con Robolectric porque la portada se serializa con
 * `org.json`, que es una clase de Android: sobre la maquina virtual pelada solo hay un
 * esqueleto que lanza al primer uso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreNewsTest {

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

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun portada(fetchedAtMillis: Long, titular: String) = NewsEdition(
        headline = NewsHeadline(titular, "La bajada de la portada"),
        points = listOf(
            NewsPoint(
                index = "01",
                title = "Un titular con dos puntos: y una coma, dentro",
                summary = "El resumen del punto.",
                url = "https://noticiasdoxa.es/cluster/1095/",
                section = "Política",
            ),
        ),
        fetchedAtMillis = fetchedAtMillis,
        editionLabel = "2026-08-25",
    )

    @Test
    fun `sin nada bajado no hay portada`() = runTest {
        assertNull(repository.lastNews.first())
    }

    @Test
    fun `la portada se guarda entera y se vuelve a leer igual`() = runTest {
        val edition = portada(1_700_000_000_000L, "Titular de hoy")

        repository.setLastNews(edition)

        assertEquals(edition, repository.lastNews.first())
    }

    /**
     * La de hoy sustituye a la de ayer: no se guarda un historico. Nadie vuelve a la
     * portada de anteayer, y acumularlas haria crecer sin tope el fichero que el
     * launcher lee en cada arranque.
     */
    @Test
    fun `bajar otra portada sustituye a la anterior`() = runTest {
        repository.setLastNews(portada(1_000L, "La de ayer"))

        repository.setLastNews(portada(2_000L, "La de hoy"))

        val leida = repository.lastNews.first()!!
        assertEquals("La de hoy", leida.headline.title)
        assertEquals(2_000L, leida.fetchedAtMillis)
    }
}
