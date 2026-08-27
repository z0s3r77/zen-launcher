package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.data.apps.LocalAppRestrictionManager
import com.zenlauncher.zen.domain.apps.SeedEssentialFavourites
import com.zenlauncher.zen.domain.media.NowPlaying
import com.zenlauncher.zen.domain.battery.BatteryStatus
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.session.DefaultZenSessionManager
import com.zenlauncher.zen.fakes.FakeBatteryReader
import com.zenlauncher.zen.fakes.FakeInstalledAppsRepository
import com.zenlauncher.zen.fakes.FakeMediaTransport
import com.zenlauncher.zen.fakes.FakeNotificationsRepository
import com.zenlauncher.zen.fakes.FakePreferencesRepository
import com.zenlauncher.zen.fakes.FakeSessionRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.fakes.RecordingAlarmScheduler
import com.zenlauncher.zen.fakes.RecordingRestrictionManager
import com.zenlauncher.zen.fakes.appNotification
import com.zenlauncher.zen.fakes.installedApp
import com.zenlauncher.zen.presentation.home.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apps = listOf(
        installedApp("com.phone", "Teléfono"),
        installedApp("com.notes", "Notas"),
        installedApp("com.instagram.android", "Instagram"),
    )

    /** Un dispositivo con parte de las esenciales instaladas y una ladrona de tiempo. */
    private val realWorldApps = listOf(
        installedApp("com.google.android.googlequicksearchbox", "Google"),
        installedApp("com.whatsapp", "WhatsApp"),
        installedApp("com.google.android.dialer", "Teléfono"),
        installedApp("com.android.settings", "Ajustes"),
        installedApp("com.spotify.music", "Spotify"),
        installedApp("com.instagram.android", "Instagram"),
    )

    private fun viewModel(
        preferences: FakePreferencesRepository = FakePreferencesRepository(),
        battery: FakeBatteryReader = FakeBatteryReader(),
        media: FakeMediaTransport = FakeMediaTransport(),
        notifications: FakeNotificationsRepository = FakeNotificationsRepository(),
        installed: List<InstalledApp> = apps,
        repository: FakeInstalledAppsRepository = FakeInstalledAppsRepository(installed),
    ): HomeViewModel {
        val clock = FakeZenClock()
        return HomeViewModel(
            preferences = preferences,
            installedApps = repository,
            restrictions = LocalAppRestrictionManager(preferences),
            sessionManager = DefaultZenSessionManager(
                preferences = preferences,
                sessions = FakeSessionRepository(),
                battery = battery,
                restrictions = RecordingRestrictionManager(preferences),
                alarms = RecordingAlarmScheduler(),
                clock = clock,
            ),
            media = media,
            notifications = notifications,
            seedFavourites = SeedEssentialFavourites(
                preferences = preferences,
                installedApps = repository,
                restrictions = LocalAppRestrictionManager(preferences),
            ),
            clock = clock,
        )
    }

    @Test
    fun `el primer estado ya trae la hora para que el reloj no aparezca de golpe`() {
        // Regresion: el estado inicial llegaba vacio y lo mas grande de la pantalla se
        // pintaba un instante despues. El combine espera a la lista de aplicaciones.
        val state = viewModel().state.value

        assertTrue(state.nowMillis > 0)
    }

    @Test
    fun `los favoritos respetan el orden elegido y no el alfabetico`() = runTest {
        val preferences = FakePreferencesRepository()
        preferences.setFavourites(listOf("com.notes", "com.phone"))

        viewModel(preferences).state.test {
            awaitItem() // estado inicial

            val loaded = awaitItem()
            assertEquals(listOf("Notas", "Teléfono"), loaded.homeApps.map { it.label })
            assertFalse(loaded.usingEssentials)
        }
    }

    @Test
    fun `mover una aplicacion escribe el nuevo orden en los favoritos`() = runTest {
        val preferences = FakePreferencesRepository()
        preferences.setFavourites(listOf("com.phone", "com.notes", "com.instagram.android"))
        val viewModel = viewModel(preferences)

        viewModel.state.test {
            awaitItem()
            awaitItem()

            viewModel.moveHomeApp(from = 0, to = 2)
            // `runCurrent` y no `advanceUntilIdle`: el reloj de la home late para
            // siempre, asi que adelantar hasta que no quede nada no termina nunca.
            runCurrent()

            assertEquals(
                listOf("Notas", "Instagram", "Teléfono"),
                awaitItem().homeApps.map { it.label },
            )
        }

        assertEquals(
            listOf("com.notes", "com.instagram.android", "com.phone"),
            preferences.favouritePackages.first(),
        )
    }

    @Test
    fun `mover no borra una favorita restringida`() = runTest {
        // Regresion: se reescribian los favoritos con lo que la home estaba pintando, y
        // una restringida no se pinta. Reordenar cualquier otra la borraba, asi que al
        // levantar la restriccion la aplicacion ya no volvia al inicio.
        val preferences = FakePreferencesRepository(initialRestricted = setOf("com.instagram.android"))
        preferences.setFavourites(listOf("com.phone", "com.instagram.android", "com.notes"))
        val viewModel = viewModel(preferences)

        viewModel.state.test {
            awaitItem()
            awaitItem()

            viewModel.moveHomeApp(from = 0, to = 1)
            runCurrent()
            awaitItem()
        }

        assertEquals(
            listOf("com.notes", "com.instagram.android", "com.phone"),
            preferences.favouritePackages.first(),
        )
    }

    @Test
    fun `mover con las esenciales puestas convierte el orden en una eleccion`() = runTest {
        // Sin nada elegido la home va con las esenciales, que no estan guardadas. Lo que
        // el usuario acaba de ordenar con el dedo pasa a ser su lista.
        val preferences = FakePreferencesRepository()
        val viewModel = viewModel(preferences, installed = realWorldApps)

        viewModel.state.test {
            awaitItem()
            val cargado = awaitItem()
            assertTrue(cargado.usingEssentials)

            viewModel.moveHomeApp(from = 0, to = 1)
            runCurrent()
            assertFalse(awaitItem().usingEssentials)
        }

        val esperado = listOf(
            "com.whatsapp",
            "com.google.android.googlequicksearchbox",
            "com.google.android.dialer",
            "com.android.settings",
            "com.spotify.music",
        )
        assertEquals(esperado, preferences.favouritePackages.first())
    }

    @Test
    fun `un movimiento que no cambia nada no escribe`() = runTest {
        val preferences = FakePreferencesRepository()
        val viewModel = viewModel(preferences)

        viewModel.state.test {
            awaitItem()
            awaitItem()

            viewModel.moveHomeApp(from = 1, to = 1)
            runCurrent()
        }

        assertTrue(preferences.favouritePackages.first().isEmpty())
    }

    @Test
    fun `una aplicacion restringida no aparece entre los favoritos`() = runTest {
        val preferences = FakePreferencesRepository(initialRestricted = setOf("com.instagram.android"))
        preferences.setFavourites(listOf("com.phone", "com.instagram.android"))

        viewModel(preferences).state.test {
            awaitItem()

            val loaded = awaitItem()
            assertEquals(listOf("Teléfono"), loaded.homeApps.map { it.label })
            assertEquals(1, loaded.restrictedCount)
        }
    }

    @Test
    fun `un favorito desinstalado se descarta en silencio`() = runTest {
        val preferences = FakePreferencesRepository()
        preferences.setFavourites(listOf("com.phone", "com.ya.no.existe"))

        viewModel(preferences).state.test {
            awaitItem()

            assertEquals(listOf("Teléfono"), awaitItem().homeApps.map { it.label })
        }
    }

    @Test
    fun `sin favoritos elegidos la pantalla arranca con las aplicaciones esenciales`() = runTest {
        // Un launcher recien puesto no puede aparecer vacio: hasta que el usuario elija,
        // manda la lista de las que no quitan tiempo.
        viewModel(installed = realWorldApps).state.test {
            awaitItem()

            val loaded = awaitItem()
            assertEquals(
                listOf("Google", "WhatsApp", "Teléfono", "Ajustes", "Spotify"),
                loaded.homeApps.map { it.label },
            )
            assertTrue(loaded.usingEssentials)
        }
    }

    @Test
    fun `una esencial restringida no se cuela en el inicio`() = runTest {
        val preferences = FakePreferencesRepository(initialRestricted = setOf("com.whatsapp"))

        viewModel(preferences, installed = realWorldApps).state.test {
            awaitItem()

            val loaded = awaitItem()
            assertEquals(
                listOf("Google", "Teléfono", "Ajustes", "Spotify"),
                loaded.homeApps.map { it.label },
            )
        }
    }

    @Test
    fun `en cuanto el usuario elige favoritos, las esenciales dejan de mandar`() = runTest {
        val preferences = FakePreferencesRepository()
        preferences.setFavourites(listOf("com.instagram.android"))

        viewModel(preferences, installed = realWorldApps).state.test {
            awaitItem()

            val loaded = awaitItem()
            assertEquals(listOf("Instagram"), loaded.homeApps.map { it.label })
            assertFalse(loaded.usingEssentials)
        }
    }

    @Test
    fun `el mando manda las tres ordenes al reproductor`() = runTest {
        val media = FakeMediaTransport()
        val viewModel = viewModel(media = media)

        viewModel.previousTrack()
        viewModel.togglePlayback()
        viewModel.nextTrack()

        assertEquals(listOf("previous", "playPause", "next"), media.commands)
    }

    @Test
    fun `al pausar, el estado cambia sin esperar al reproductor`() = runTest {
        val media = FakeMediaTransport(playing = true)
        val viewModel = viewModel(media = media)

        viewModel.state.test {
            awaitItem()
            assertTrue(awaitItem().mediaPlaying)

            viewModel.togglePlayback()

            assertFalse(awaitItem().mediaPlaying)
        }
    }

    @Test
    fun `si no hay ningun reproductor escuchando, no se finge un cambio de estado`() = runTest {
        // Regresion: el boton cambiaba de forma aunque la tecla no llegara a nadie, y
        // la pantalla decia SONANDO con el telefono en silencio.
        val media = FakeMediaTransport(playing = false, accepts = false)
        val viewModel = viewModel(media = media)

        viewModel.state.test {
            awaitItem()
            assertFalse(awaitItem().mediaPlaying)

            viewModel.togglePlayback()

            expectNoEvents()
            assertEquals(listOf("playPause"), media.commands)
        }
    }

    @Test
    fun `el titulo y el artista de lo que suena llegan al estado`() = runTest {
        val media = FakeMediaTransport(
            nowPlaying = NowPlaying(title = "Nosotros", artist = "Hijos de la Ruina", playing = true),
        )

        viewModel(media = media).state.test {
            awaitItem()

            val loaded = awaitItem()
            assertEquals("Nosotros", loaded.nowPlaying?.title)
            assertEquals("Hijos de la Ruina", loaded.nowPlaying?.artist)
        }
    }

    @Test
    fun `con metadatos manda la sesion y no el nivel de audio`() = runTest {
        // Un reproductor silenciado sigue en reproduccion: isMusicActive diria que no.
        val media = FakeMediaTransport(
            playing = false,
            nowPlaying = NowPlaying(title = "Nosotros", artist = "", playing = true),
        )

        viewModel(media = media).state.test {
            awaitItem()

            assertTrue(awaitItem().mediaPlaying)
        }
    }

    @Test
    fun `cambiar de cancion se refleja sin tocar la pantalla`() = runTest {
        val media = FakeMediaTransport(
            nowPlaying = NowPlaying(title = "Nosotros", artist = "Hijos de la Ruina", playing = true),
        )
        val viewModel = viewModel(media = media)

        viewModel.state.test {
            awaitItem()
            assertEquals("Nosotros", awaitItem().nowPlaying?.title)

            media.emitNowPlaying(NowPlaying(title = "Aquellas Noches", artist = "Marea", playing = true))

            assertEquals("Aquellas Noches", awaitItem().nowPlaying?.title)
        }
    }

    @Test
    fun `sin acceso concedido no hay metadatos pero el mando sigue vivo`() = runTest {
        val media = FakeMediaTransport(playing = true)
        val viewModel = viewModel(media = media)

        viewModel.state.test {
            awaitItem()

            val loaded = awaitItem()
            assertNull(loaded.nowPlaying)
            assertTrue(loaded.mediaPlaying)
        }

        viewModel.nextTrack()
        assertEquals(listOf("next"), media.commands)
    }

    @Test
    fun `tocar la cancion abre el reproductor que la publica`() = runTest {
        val installedApps = FakeInstalledAppsRepository(realWorldApps)
        val media = FakeMediaTransport(
            nowPlaying = NowPlaying(
                title = "Oye",
                artist = "FERNANDOCOSTA",
                playing = true,
                packageName = "com.spotify.music",
            ),
        )
        val viewModel = viewModel(media = media, repository = installedApps)

        viewModel.state.test {
            awaitItem()
            awaitItem() // ya con los metadatos cargados

            viewModel.openNowPlaying()
        }

        assertEquals(listOf("com.spotify.music"), installedApps.launched.map { it.packageName })
    }

    @Test
    fun `sin cancion en pantalla, tocar no abre nada`() = runTest {
        val installedApps = FakeInstalledAppsRepository(realWorldApps)
        val viewModel = viewModel(repository = installedApps)

        viewModel.openNowPlaying()

        assertEquals(emptyList<String>(), installedApps.launched.map { it.packageName })
    }

    @Test
    fun `el mando solo se ensena cuando hay algo que mandar`() = runTest {
        // Regresion: la barra se pintaba siempre, asi que al entrar en Zen sin haber
        // puesto musica en todo el dia habia un reproductor "EN PAUSA" que no mandaba
        // nada. Con una sesion de medios viva —Spotify recien pausado— si tiene sitio.
        val media = FakeMediaTransport(playing = false)
        val viewModel = viewModel(media = media)

        viewModel.state.test {
            awaitItem()
            assertFalse(awaitItem().mediaVisible)

            media.emitNowPlaying(NowPlaying(title = "Nosotros", artist = "", playing = false))

            assertTrue(awaitItem().mediaVisible)
        }
    }

    @Test
    fun `las marcas de aviso solo cuentan aplicaciones que se pueden ver`() = runTest {
        // Una restringida desaparece de Zen por completo: colar su numero en la
        // pantalla de inicio seria la puerta trasera que la restriccion cierra.
        val preferences = FakePreferencesRepository(initialRestricted = setOf("com.instagram.android"))
        val notifications = FakeNotificationsRepository(
            listOf(
                appNotification("com.whatsapp", title = "Ana"),
                appNotification("com.whatsapp", title = "Luis"),
                appNotification("com.instagram.android", title = "5 me gusta"),
                appNotification("com.spotify.music", title = "Nosotros", ongoing = true),
            ),
        )

        viewModel(
            preferences = preferences,
            notifications = notifications,
            installed = realWorldApps,
            repository = FakeInstalledAppsRepository(realWorldApps),
        ).state.test {
            awaitItem() // estado inicial, todavia sin avisos

            val loaded = awaitItem()
            assertEquals(mapOf("com.whatsapp" to 2), loaded.notificationCounts)
            assertEquals(2, loaded.notificationTotal)
        }
    }

    @Test
    fun `el numero del menu cuenta lo mismo que va a listar la pantalla`() = runTest {
        // Regresion: el total se sacaba de las marcas, que solo cuentan aplicaciones
        // lanzables; la pantalla de Notificaciones lista tambien las que no lo son. La
        // fila del menu decia "00" y dentro habia avisos.
        val notifications = FakeNotificationsRepository(
            listOf(
                appNotification("com.whatsapp", title = "Ana"),
                appNotification("com.android.shell", title = "Actualización"),
            ),
        )

        viewModel(
            notifications = notifications,
            installed = realWorldApps,
            repository = FakeInstalledAppsRepository(realWorldApps),
        ).state.test {
            awaitItem() // estado inicial, todavia sin avisos

            val loaded = awaitItem()
            // La marca solo puede colgar de una aplicacion que este en la reticula...
            assertEquals(mapOf("com.whatsapp" to 1), loaded.notificationCounts)
            // ...pero el total incluye lo que la pantalla va a ensenar igualmente.
            assertEquals(2, loaded.notificationTotal)
        }
    }

    @Test
    fun `al volver a primer plano se relee el estado del reproductor`() = runTest {
        // La musica pudo arrancarse desde los auriculares o desde otra aplicacion.
        val media = FakeMediaTransport(playing = false)
        val viewModel = viewModel(media = media)

        viewModel.state.test {
            awaitItem()
            assertFalse(awaitItem().mediaPlaying)

            media.playing = true
            viewModel.onResumed()

            assertTrue(awaitItem().mediaPlaying)
        }
    }
}
