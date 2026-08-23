package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.media.NowPlaying
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.presentation.home.HomeScreen
import com.zenlauncher.zen.presentation.home.HomeUiState
import com.zenlauncher.zen.presentation.home.menuLabelColor
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
// Un telefono actual, no la pantalla minuscula que Robolectric usa por defecto: la
// home reparte el alto entre reloj, mando y reticula, y medirla en 470dp no dice nada.
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var drawerOpened = 0
    private var homeAppsOpened = 0
    private var settingsOpened = 0
    private var sessionsStarted = 0
    private var restrictedOpened = 0
    private var statsOpened = 0
    private val launched = mutableListOf<InstalledApp>()
    private val mediaCommands = mutableListOf<String>()
    private val notificationsOpened = mutableListOf<String?>()
    private var exits = 0
    private var playerOpened = 0

    private fun render(
        homeApps: List<InstalledApp>,
        usingEssentials: Boolean = false,
        mediaPlaying: Boolean = false,
        nowPlaying: NowPlaying? = null,
        notificationCounts: Map<String, Int> = emptyMap(),
    ) {
        composeRule.setContent {
            ZenTheme {
                HomeScreen(
                    state = HomeUiState(
                        nowMillis = 1_700_000_000_000,
                        homeApps = homeApps,
                        usingEssentials = usingEssentials,
                        restrictedCount = 2,
                        hasApps = true,
                        mediaPlaying = mediaPlaying,
                        nowPlaying = nowPlaying,
                        notificationCounts = notificationCounts,
                    ),
                    onLaunchApp = { launched += it },
                    onOpenDrawer = { drawerOpened++ },
                    onOpenHomeApps = { homeAppsOpened++ },
                    onStartSession = { sessionsStarted++ },
                    onOpenRestricted = { restrictedOpened++ },
                    onOpenStats = { statsOpened++ },
                    onOpenSettings = { settingsOpened++ },
                    onOpenNotifications = { notificationsOpened += it },
                    onExitZen = { exits++ },
                    onPreviousTrack = { mediaCommands += "previous" },
                    onTogglePlayback = { mediaCommands += "playPause" },
                    onNextTrack = { mediaCommands += "next" },
                    onOpenPlayer = { playerOpened++ },
                    locale = Locale.forLanguageTag("es-ES"),
                )
            }
        }
    }

    /** Una pantalla llena: las ocho que caben, para medir el peor caso de alto. */
    private fun ochoApps() = listOf(
        app("com.google.android.googlequicksearchbox", "Google"),
        app("com.whatsapp", "WhatsApp"),
        app("com.google.android.dialer", "Teléfono"),
        app("com.google.android.deskclock", "Reloj"),
        app("com.android.settings", "Ajustes"),
        app("com.google.android.apps.messaging", "Mensajes"),
        app("com.google.android.gm", "Gmail"),
        app("com.imaginbank.app", "imagin"),
    )

    private fun app(pkg: String, label: String) =
        InstalledApp(packageName = pkg, label = label, componentName = "$pkg/.Main")

    @Test
    fun `la lista completa se abre desde la home y ya no esta en el menu`() {
        // Estuvo en la home, luego solo en el menu y tras un deslizamiento, y ahora
        // vuelve a la home: el gesto se disparaba solo (ver la regresion de abajo) y
        // una puerta que se usa a diario no puede pedir dos toques.
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("Todas las aplicaciones")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, drawerOpened)
        assertEquals(0, settingsOpened)

        // Y no se repite dentro del menu: la misma accion en dos sitios obliga a elegir.
        composeRule.onNodeWithText("Menú").performClick()
        composeRule.onNodeWithText("Todas las aplicaciones").assertDoesNotExist()
    }

    @Test
    fun `deslizar hacia arriba ya no abre nada`() {
        // Regresion: el gesto abria la lista completa desde cualquier punto de la
        // pantalla de inicio —tambien sobre la reticula y con el menu abierto—, asi que
        // se colaba en mitad de cualquier otra intencion y parecia que la home se iba
        // sola. En una pantalla de inicio, lo que abre algo tiene que verse.
        render(homeApps = ochoApps())

        composeRule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = top) }

        assertEquals(0, drawerOpened)
    }

    @Test
    fun `deslizar hacia arriba con el menu abierto tampoco`() {
        // La otra cara de la misma regresion: el gesto vivia en la raiz de la pantalla,
        // asi que seguia vivo con el menu delante.
        render(homeApps = ochoApps())
        composeRule.onNodeWithText("Menú").performClick()

        composeRule.onRoot().performTouchInput { swipeUp(startY = centerY, endY = top) }

        assertEquals(0, drawerOpened)
        composeRule.onNodeWithText("Registro").assertIsDisplayed()
    }

    @Test
    fun `sin aplicaciones ofrece elegirlas y lleva a la pantalla que solo hace eso`() {
        // Regresion: llevaba a Ajustes enteros, donde elegir era una lista con todas
        // las aplicaciones del telefono colgando del final.
        render(homeApps = emptyList())

        composeRule.onNodeWithText("Elegir aplicaciones").performClick()

        assertEquals(1, homeAppsOpened)
        assertEquals(0, settingsOpened)
    }

    @Test
    fun `con aplicaciones en la reticula no insiste en elegirlas`() {
        render(homeApps = listOf(app("com.a", "Teléfono")))

        composeRule.onNodeWithText("Elegir aplicaciones").assertDoesNotExist()
    }

    @Test
    fun `las aplicaciones de la reticula se abren al tocarlas`() {
        val notes = app("com.notes", "Notas")
        render(homeApps = listOf(app("com.phone", "Teléfono"), notes))

        composeRule.onNodeWithText("Notas").performClick()

        assertEquals(listOf(notes), launched)
    }

    @Test
    fun `la bateria y las conexiones ya no se repiten en la home`() {
        // Se quitaron al dejar de ocultar la barra de estado: decian exactamente lo
        // mismo que el sistema dibuja dos centimetros mas arriba.
        render(homeApps = emptyList())

        composeRule.onNodeWithText("SIN SESIÓN").assertIsDisplayed()
        composeRule.onNodeWithText("BAT 84%").assertDoesNotExist()
        composeRule.onNodeWithText("WIFI").assertDoesNotExist()
    }

    @Test
    fun `el boton ZEN esta junto a la hora y arranca la sesion`() {
        render(homeApps = emptyList())

        composeRule.onNodeWithText("ZEN")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, sessionsStarted)
    }

    @Test
    fun `la home no se desplaza, todo cabe en la pantalla`() {
        // Regresion: la home era una columna con verticalScroll y el menu quedaba bajo
        // el pliegue. Una pantalla de inicio que se arrastra deja de ser un sitio fijo.
        render(homeApps = ochoApps())

        // La hora formateada depende de la zona horaria del entorno; se comprueba la
        // franja de arriba, que es lo que importa: cabe el principio y cabe el final.
        composeRule.onNodeWithText("ZEN").assertIsDisplayed()
        composeRule.onNodeWithText("imagin").assertIsDisplayed()
        // La fila que se sumo a la home con la reticula llena: si empujara algo fuera,
        // lo primero en caerse seria lo de abajo.
        composeRule.onNodeWithText("Todas las aplicaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Notas rápidas").assertIsDisplayed()
        composeRule.onNodeWithText("Menú").assertIsDisplayed()
    }

    @Test
    fun `con el mando sonando y la reticula llena sigue cabiendo todo`() {
        // El peor caso de alto: ocho aplicaciones, el mando del reproductor abierto y la
        // fila que se sumo a la home. Si algo se saliera, la home no se desplaza para
        // ir a buscarlo.
        composeRule.mainClock.autoAdvance = false
        render(homeApps = ochoApps(), mediaPlaying = true)

        composeRule.onNodeWithText("SONANDO").assertIsDisplayed()
        composeRule.onNodeWithText("imagin").assertIsDisplayed()
        composeRule.onNodeWithText("Todas las aplicaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Notas rápidas").assertIsDisplayed()
        composeRule.onNodeWithText("Menú").assertIsDisplayed()
    }

    @Test
    fun `el menu abierto ocupa la pantalla entera y solo deja la cabecera`() {
        // Son cosas que se hacen una vez cada muchos dias y merecen atencion sin nada
        // al lado; ademas, sin desplazamiento, compartir pantalla con el reloj dejaba
        // las ultimas acciones fuera. Sobrevive la franja de cabecera: da igual donde
        // estes, el dia y el estado de la sesion siguen en el mismo pixel.
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("Menú").performClick()

        // La fecha formateada depende de la zona horaria del entorno; se comprueba el
        // otro extremo de la franja, que es igual de suficiente: la cabecera sigue ahi.
        composeRule.onNodeWithText("SIN SESIÓN").assertIsDisplayed()
        composeRule.onNodeWithText("Registro").assertIsDisplayed()

        composeRule.onNodeWithText("ZEN").assertDoesNotExist()
        composeRule.onNodeWithText("imagin").assertDoesNotExist()
        composeRule.onNodeWithText("Todas las aplicaciones").assertDoesNotExist()
        composeRule.onNodeWithText("Notas rápidas").assertDoesNotExist()

        composeRule.onNodeWithText("Menú").performClick()

        composeRule.onNodeWithText("imagin").assertIsDisplayed()
        composeRule.onNodeWithText("Registro").assertDoesNotExist()
    }

    @Test
    fun `las cuatro acciones estan guardadas hasta que se abre el menu`() {
        // Cada fila permanente en la pantalla de inicio es una invitacion a tocarla.
        render(homeApps = listOf(app("com.phone", "Teléfono")))

        composeRule.onNodeWithText("Iniciar Zen").assertDoesNotExist()
        composeRule.onNodeWithText("Aplicaciones restringidas").assertDoesNotExist()
        composeRule.onNodeWithText("Registro").assertDoesNotExist()
        composeRule.onNodeWithText("Ajustes Zen").assertDoesNotExist()

        composeRule.onNodeWithText("Menú").performClick()

        composeRule.onNodeWithText("Iniciar Zen").assertIsDisplayed()
        composeRule.onNodeWithText("Aplicaciones restringidas").assertIsDisplayed()
        composeRule.onNodeWithText("Registro").assertIsDisplayed()
        composeRule.onNodeWithText("Ajustes Zen").assertIsDisplayed()
    }

    @Test
    fun `las acciones del menu abierto llevan a su pantalla`() {
        render(homeApps = emptyList())

        composeRule.onNodeWithText("Menú").performClick()

        composeRule.onNodeWithText("Aplicaciones restringidas").performClick()
        composeRule.onNodeWithText("Registro").performClick()
        composeRule.onNodeWithText("Ajustes Zen").performClick()
        composeRule.onNodeWithText("Iniciar Zen").performClick()

        assertEquals(1, restrictedOpened)
        assertEquals(1, statsOpened)
        assertEquals(1, settingsOpened)
        assertEquals(1, sessionsStarted)
    }

    @Test
    fun `arrastrar desde el borde cierra el menu al primer intento`() {
        // Regresion: con las barras del sistema ocultas, Android se queda el primer
        // deslizamiento desde el borde para sacarlas y solo el segundo llegaba como
        // "atras". Habia que deslizar dos veces siempre.
        render(homeApps = ochoApps())
        composeRule.onNodeWithText("Menú").performClick()
        composeRule.onNodeWithText("Registro").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            swipeRight(startX = left, endX = centerX)
        }

        composeRule.onNodeWithText("Registro").assertDoesNotExist()
        composeRule.onNodeWithText("imagin").assertIsDisplayed()
    }

    @Test
    fun `en la home ese mismo arrastre no hace nada`() {
        // No hay a donde volver, y un gesto que a veces hace algo y a veces no es peor
        // que uno que nunca hace nada.
        render(homeApps = ochoApps())

        composeRule.onRoot().performTouchInput {
            swipeRight(startX = left, endX = centerX)
        }

        composeRule.onNodeWithText("imagin").assertIsDisplayed()
        assertEquals(0, drawerOpened)
    }

    @Test
    fun `el rotulo Menú se enciende cuando el menu esta abierto`() {
        // Es la misma fila en las dos caras de la pantalla: sin esto nada distinguia
        // "puedes abrir" de "estas dentro". Nunca es la unica senal, por eso al lado
        // sigue leyendose ABRIR o CERRAR.
        assertEquals(ZenColors.Muted, menuLabelColor(open = false))
        assertEquals(ZenColors.Foreground, menuLabelColor(open = true))

        render(homeApps = emptyList())
        composeRule.onNodeWithText("ABRIR").assertIsDisplayed()

        composeRule.onNodeWithText("Menú").performClick()
        composeRule.onNodeWithText("CERRAR").assertIsDisplayed()
    }

    @Test
    fun `el menu vuelve a cerrarse`() {
        render(homeApps = emptyList())

        composeRule.onNodeWithText("Menú").performClick()
        composeRule.onNodeWithText("Registro").assertIsDisplayed()

        composeRule.onNodeWithText("Menú").performClick()
        composeRule.onNodeWithText("Registro").assertDoesNotExist()
    }

    @Test
    fun `el mando del reproductor manda las tres ordenes`() {
        composeRule.mainClock.autoAdvance = false
        render(homeApps = emptyList(), mediaPlaying = true)

        composeRule.onNodeWithContentDescription("Canción anterior").performClick()
        // Con musica sonando el boton central pausa; la orden que se manda es la misma.
        composeRule.onNodeWithContentDescription("Pausar").performClick()
        composeRule.onNodeWithContentDescription("Canción siguiente").performClick()

        assertEquals(listOf("previous", "playPause", "next"), mediaCommands)
    }

    @Test
    fun `el estado del reproductor se lee como texto y no solo por la forma del boton`() {
        composeRule.mainClock.autoAdvance = false
        render(homeApps = emptyList(), mediaPlaying = true)

        composeRule.onNodeWithText("SONANDO").assertIsDisplayed()
        // Con musica sonando, el boton central pausa.
        composeRule.onNodeWithContentDescription("Pausar").assertHasClickAction()
    }

    @Test
    fun `cuando hay metadatos se ven el titulo y el artista`() {
        composeRule.mainClock.autoAdvance = false
        render(
            homeApps = emptyList(),
            mediaPlaying = true,
            nowPlaying = NowPlaying(
                title = "Nosotros",
                artist = "Hijos de la Ruina",
                playing = true,
            ),
        )

        composeRule.onNodeWithText("Nosotros").assertIsDisplayed()
        composeRule.onNodeWithText("Hijos de la Ruina").assertIsDisplayed()
        // El mando sigue estando: la ficha se suma, no sustituye.
        composeRule.onNodeWithContentDescription("Pausar").assertHasClickAction()
    }

    @Test
    fun `tocar la cancion abre el reproductor`() {
        // Las animaciones infinitas del ecualizador no dejan al reloj de test quedarse
        // quieto: se avanza a mano.
        composeRule.mainClock.autoAdvance = false
        render(
            homeApps = emptyList(),
            mediaPlaying = true,
            nowPlaying = NowPlaying(
                title = "Oye",
                artist = "FERNANDOCOSTA",
                playing = true,
                packageName = "com.spotify.music",
            ),
        )

        composeRule.onNodeWithText("Oye").performClick()

        assertEquals(1, playerOpened)
    }

    @Test
    fun `sin saber quien reproduce, la ficha no es pulsable`() {
        // Regresion: una ficha que reacciona a veces si y a veces no es peor que una
        // que no invita a tocarla.
        render(
            homeApps = emptyList(),
            nowPlaying = NowPlaying(title = "Oye", artist = "", playing = false),
        )

        composeRule.onNodeWithText("Oye").assertHasNoClickAction()
    }

    @Test
    fun `sin metadatos pero con audio, el mando sigue estando entero`() {
        // Sin acceso concedido no hay ficha, pero el mando tiene que funcionar igual:
        // el nivel bajo del reproductor no depende de ninguna concesion.
        composeRule.mainClock.autoAdvance = false
        render(homeApps = emptyList(), mediaPlaying = true, nowPlaying = null)

        composeRule.onNodeWithText("REPRODUCTOR").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pausar").assertHasClickAction()
    }

    @Test
    fun `sin nada sonando el reproductor no ocupa la pantalla`() {
        // Regresion: la barra se pintaba siempre, asi que al entrar en Zen sin haber
        // puesto musica en todo el dia habia un mando "EN PAUSA" que no mandaba nada.
        render(homeApps = emptyList(), mediaPlaying = false, nowPlaying = null)

        composeRule.onNodeWithText("REPRODUCTOR").assertDoesNotExist()
        composeRule.onNodeWithText("EN PAUSA").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reproducir").assertDoesNotExist()
    }

    @Test
    fun `un reproductor pausado con ficha sigue a mano para volver a darle`() {
        // Una sesion de medios viva es algo que mandar aunque no suene: es el caso de
        // Spotify recien pausado, donde esconder el mando obligaria a abrir Spotify.
        render(
            homeApps = emptyList(),
            mediaPlaying = false,
            nowPlaying = NowPlaying(title = "Nosotros", artist = "", playing = false),
        )

        composeRule.onNodeWithText("REPRODUCTOR").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reproducir").assertHasClickAction()
    }

    @Test
    fun `un titulo sin artista no pinta una linea en blanco`() {
        render(
            homeApps = emptyList(),
            nowPlaying = NowPlaying(title = "Capítulo 12", artist = "", playing = false),
        )

        composeRule.onNodeWithText("Capítulo 12").assertIsDisplayed()
    }

    @Test
    fun `la home ya no pregunta que necesitas`() {
        // La reticula de favoritos se entiende sola: el rotulo era la aplicacion
        // hablando por hablar en la pantalla que mas se mira.
        render(homeApps = listOf(app("com.phone", "Teléfono")))

        composeRule.onNodeWithText("¿QUÉ NECESITAS?").assertDoesNotExist()
    }

    @Test
    fun `las notas rapidas tienen su sitio y dicen que todavia no estan`() {
        // No es pulsable a proposito: una fila que se traga el toque en silencio ensena
        // a desconfiar de las que si funcionan.
        render(homeApps = listOf(app("com.phone", "Teléfono")))

        composeRule.onNodeWithText("Notas rápidas").assertIsDisplayed().assertHasNoClickAction()
        composeRule.onNodeWithText("PRONTO").assertIsDisplayed()
    }

    @Test
    fun `la marca de avisos ensena el numero y no un punto`() {
        // Un punto solo dice "algo hay" y obliga a abrir la aplicacion para saber si
        // merecia la pena; el numero cierra la pregunta desde la pantalla de inicio.
        render(
            homeApps = listOf(app("com.whatsapp", "WhatsApp"), app("com.phone", "Teléfono")),
            notificationCounts = mapOf("com.whatsapp" to 3),
        )

        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("3 notificaciones de WhatsApp")
            .assertHasClickAction()
    }

    @Test
    fun `tocar la marca abre los avisos de esa aplicacion y no la aplicacion`() {
        val whatsapp = app("com.whatsapp", "WhatsApp")
        render(homeApps = listOf(whatsapp), notificationCounts = mapOf("com.whatsapp" to 1))

        composeRule.onNodeWithContentDescription("1 notificación de WhatsApp").performClick()

        assertEquals(listOf<String?>("com.whatsapp"), notificationsOpened)
        assertEquals(emptyList<InstalledApp>(), launched)
    }

    @Test
    fun `sin avisos no hay marca que mirar`() {
        render(homeApps = listOf(app("com.whatsapp", "WhatsApp")), notificationCounts = emptyMap())

        composeRule.onNodeWithText("WhatsApp").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1 notificación de WhatsApp")
            .assertDoesNotExist()
    }

    @Test
    fun `el menu lleva al panel entero y a la salida de Zen`() {
        // Salir tiene que estar a la vista: una pantalla de inicio de la que no se sabe
        // salir es una trampa.
        render(homeApps = emptyList(), notificationCounts = mapOf("com.a" to 2, "com.b" to 1))

        composeRule.onNodeWithText("Menú").performClick()

        // El menu ya son siete filas: la ultima tiene que seguir cabiendo en pantalla,
        // porque la home no se desplaza.
        composeRule.onNodeWithText("Salir de Zen").assertIsDisplayed()

        composeRule.onNodeWithText("Notificaciones").performClick()
        composeRule.onNodeWithText("Salir de Zen").performClick()

        assertEquals(listOf<String?>(null), notificationsOpened)
        assertEquals(1, exits)
    }

    @Test
    fun `las esenciales se anuncian como tales`() {
        render(homeApps = listOf(app("com.whatsapp", "WhatsApp")), usingEssentials = true)

        composeRule.onNodeWithText("ESENCIALES").assertIsDisplayed()
        composeRule.onNodeWithText("¿QUÉ NECESITAS?").assertDoesNotExist()
    }
}
