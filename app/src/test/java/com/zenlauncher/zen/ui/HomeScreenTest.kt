package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.media.NowPlaying
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.usage.AppUsage
import com.zenlauncher.zen.domain.usage.UsageLevel
import com.zenlauncher.zen.domain.usage.UsageReading
import com.zenlauncher.zen.domain.weather.WeatherCondition
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.presentation.home.HomeScreen
import com.zenlauncher.zen.presentation.home.HomeUiState
import com.zenlauncher.zen.presentation.home.menuLabelColor
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private var notesOpened = 0
    private var readingOpened = 0
    private var settingsOpened = 0
    private var sessionsStarted = 0
    private var breathsOpened = 0
    private var newsOpened = 0
    private var restrictedOpened = 0
    private var statsOpened = 0
    private val launched = mutableListOf<InstalledApp>()
    private val moves = mutableListOf<Pair<Int, Int>>()
    private val mediaCommands = mutableListOf<String>()
    private val notificationsOpened = mutableListOf<String?>()
    private var exits = 0
    private var playerOpened = 0
    private var usageOpened = 0
    private var weatherOpened = 0

    private fun render(
        homeApps: List<InstalledApp>,
        usingEssentials: Boolean = false,
        mediaPlaying: Boolean = false,
        nowPlaying: NowPlaying? = null,
        notificationCounts: Map<String, Int> = emptyMap(),
        usageReading: UsageReading = calma(),
        weather: WeatherReading? = null,
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
                    usageReading = usageReading,
                    onLaunchApp = { launched += it },
                    onMoveApp = { from, to -> moves += from to to },
                    onOpenDrawer = { drawerOpened++ },
                    onOpenHomeApps = { homeAppsOpened++ },
                    onOpenNotes = { notesOpened++ },
                    onOpenReading = { readingOpened++ },
                    onOpenScanner = {},
                    onStartSession = { sessionsStarted++ },
                    onBreathe = { breathsOpened++ },
                    onOpenNews = { newsOpened++ },
                    onOpenRestricted = { restrictedOpened++ },
                    onOpenStats = { statsOpened++ },
                    onOpenUsage = { usageOpened++ },
                    onOpenSettings = { settingsOpened++ },
                    onOpenNotifications = { notificationsOpened += it },
                    onExitZen = { exits++ },
                    onPreviousTrack = { mediaCommands += "previous" },
                    onTogglePlayback = { mediaCommands += "playPause" },
                    onNextTrack = { mediaCommands += "next" },
                    onOpenPlayer = { playerOpened++ },
                    onOpenWeather = { weatherOpened++ },
                    weather = weather,
                    locale = Locale.forLanguageTag("es-ES"),
                )
            }
        }
    }

    /** Un dia tranquilo: el pulso de uso no se pinta. */
    private fun calma() = UsageReading(
        level = UsageLevel.CALMA,
        screenMillis = 0L,
        unlocks = 0,
        topApp = null,
        measured = true,
    )

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

    /** Mas de las que caben en la pantalla: para eso existe el desplazamiento. */
    private fun veinteApps() = (1..20).map { app("com.app$it", "App $it") }

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
    fun `respirar tiene boton propio bajo ZEN y no baja al menu`() {
        // Las dos cosas que Zen sabe hacer por si mismo —una sesion y un minuto de
        // respiracion— son las dos unicas con boton propio. Respirar en el menu seria
        // esconder tras dos toques justo lo que se busca cuando uno esta agitado.
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("RESPIRA")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, breathsOpened)
        // Y no arranca una sesion por error: son dos botones pegados.
        assertEquals(0, sessionsStarted)

        composeRule.onNodeWithText("Menú").performClick()
        composeRule.onNodeWithText("RESPIRA").assertDoesNotExist()
    }

    @Test
    fun `el boton ZEN sigue siendo el de arriba`() {
        // Regresion: los dos botones comparten sitio y estilo, asi que el orden es lo
        // unico que los distingue de un vistazo. ZEN arriba, pegado a la hora.
        render(homeApps = ochoApps())

        val zen = composeRule.onNodeWithText("ZEN").getUnclippedBoundsInRoot()
        val respira = composeRule.onNodeWithText("RESPIRA").getUnclippedBoundsInRoot()

        assertTrue("ZEN debe quedar encima de RESPIRA", zen.top < respira.top)
    }

    @Test
    fun `las noticias tienen boton propio, el tercero, y no estan en la reticula`() {
        // Tercero y ultimo de la pila: los tres marcos ya miden mas de alto que el
        // reloj que tienen al lado, asi que un cuarto se comeria el aire que la
        // reticula reparte abajo. Va aqui y no en la reticula porque no lanza ninguna
        // aplicacion, y no en el menu porque leer la portada es algo de cada dia.
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("NOTICIAS")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(1, newsOpened)
        // Y no dispara a sus vecinos: son tres botones pegados del mismo ancho.
        assertEquals(0, breathsOpened)
        assertEquals(0, sessionsStarted)
    }

    @Test
    fun `el orden de los tres botones es ZEN, RESPIRA y NOTICIAS`() {
        // Regresion: los tres comparten sitio y estilo, asi que el orden es lo unico
        // que los distingue de un vistazo. Lo de arriba es lo que mas se usa.
        render(homeApps = ochoApps())

        val respira = composeRule.onNodeWithText("RESPIRA").getUnclippedBoundsInRoot()
        val noticias = composeRule.onNodeWithText("NOTICIAS").getUnclippedBoundsInRoot()

        assertTrue("RESPIRA debe quedar encima de NOTICIAS", respira.top < noticias.top)
    }

    /**
     * La home no crece: con la pantalla llena —ocho aplicaciones, la reticula entera— la
     * fila que abre el menu tiene que seguir a la vista. Es la unica salida de la
     * pantalla de inicio, y un boton mas que la empujara fuera dejaria el menu, los
     * ajustes y la salida de Zen inalcanzables.
     */
    @Test
    fun `con el tercer boton la fila del menu sigue cabiendo`() {
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("Menú").assertIsDisplayed()
        composeRule.onNodeWithText("Todas las aplicaciones").assertIsDisplayed()
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
        // La aplicacion de ejemplo no puede llamarse "Notas": desde que la fila
        // permanente lleva ese nombre, buscar ese texto encontraria dos nodos y el
        // test no estaria comprobando la reticula sino su propio dato.
        val correo = app("com.mail", "Correo")
        render(homeApps = listOf(app("com.phone", "Teléfono"), correo))

        composeRule.onNodeWithText("Correo").performClick()

        assertEquals(listOf(correo), launched)
    }

    @Test
    fun `mantener pulsada una celda y arrastrarla la cambia de hueco`() {
        render(homeApps = ochoApps())

        val origen = composeRule.onNodeWithText("Google").getUnclippedBoundsInRoot()
        val destino = composeRule.onNodeWithText("Teléfono").getUnclippedBoundsInRoot()
        val salto = with(composeRule.density) { (destino.top - origen.top).toPx() }

        composeRule.onNodeWithText("Google").performTouchInput {
            down(center)
            // Por encima del tiempo de pulsacion larga: sin mantener, un arrastre sobre
            // la reticula no puede reordenar nada.
            advanceEventTime(1_000)
            moveTo(center + Offset(0f, salto))
            advanceEventTime(50)
            up()
        }
        composeRule.waitForIdle()

        // Del hueco 01 al 03: una fila mas abajo, misma columna.
        assertEquals(listOf(0 to 2), moves)
    }

    @Test
    fun `un arrastre sin mantener pulsado no mueve nada`() {
        // La reticula esta llena de celdas que se tocan cincuenta veces al dia: un roce
        // al sacar el telefono del bolsillo no puede reordenar la pantalla de inicio.
        render(homeApps = ochoApps())

        val origen = composeRule.onNodeWithText("Google").getUnclippedBoundsInRoot()
        val destino = composeRule.onNodeWithText("Teléfono").getUnclippedBoundsInRoot()
        val salto = with(composeRule.density) { (destino.top - origen.top).toPx() }

        composeRule.onNodeWithText("Google").performTouchInput {
            down(center)
            moveTo(center + Offset(0f, salto))
            up()
        }
        composeRule.waitForIdle()

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `soltar en el mismo hueco no reordena`() {
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("Google").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        composeRule.waitForIdle()

        assertTrue(moves.isEmpty())
    }

    @Test
    fun `arrastrar no abre la aplicacion al soltar`() {
        // Regresion: la celda seguia siendo un `clickable`, asi que levantar el dedo
        // despues de mover abria la aplicacion que se acababa de colocar.
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("Google").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            up()
        }
        composeRule.waitForIdle()

        assertTrue(launched.isEmpty())
    }

    @Test
    fun `las aplicaciones se pueden mover sin arrastrar`() {
        // Arrastrar no existe para quien navega con un lector de pantalla: el mismo
        // movimiento tiene que estar disponible como accion con nombre.
        render(homeApps = ochoApps())

        val acciones = composeRule.onNodeWithText("Teléfono")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]

        assertEquals(
            listOf("Mover al hueco anterior", "Mover al hueco siguiente"),
            acciones.map { it.label },
        )

        acciones.first { it.label == "Mover al hueco anterior" }.action()

        assertEquals(listOf(2 to 1), moves)
    }

    @Test
    fun `la primera y la ultima solo se pueden mover hacia donde hay hueco`() {
        render(homeApps = ochoApps())

        val primera = composeRule.onNodeWithText("Google")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        val ultima = composeRule.onNodeWithText("imagin")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]

        assertEquals(listOf("Mover al hueco siguiente"), primera.map { it.label })
        assertEquals(listOf("Mover al hueco anterior"), ultima.map { it.label })
    }

    @Test
    fun `notas y lectura no se mueven`() {
        // Son las dos unicas celdas que no son aplicaciones y su sitio es una decision
        // de producto: ni se arrastran ni sirven de destino.
        render(homeApps = ochoApps())

        listOf("Notas", "Lectura").forEach { celda ->
            val nodo = composeRule.onNodeWithText(celda).fetchSemanticsNode()
            assertTrue(SemanticsActions.CustomActions !in nodo.config)
        }
    }

    @Test
    fun `la bateria y las conexiones ya no se repiten en la home`() {
        // Se quitaron al dejar de ocultar la barra de estado: decian exactamente lo
        // mismo que el sistema dibuja dos centimetros mas arriba.
        render(homeApps = emptyList())

        // La franja sigue ahi; lo que lleva a la derecha es ahora la cara del dia.
        composeRule.onNodeWithText(":)").assertIsDisplayed()
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
    fun `con las que caben no hay nada que desplazar`() {
        // La home se desplaza desde que la reticula no tiene tope, pero con ocho
        // aplicaciones —lo que cabia antes— la pantalla se comporta igual que siempre:
        // todo a la vista sin tocar nada.
        render(homeApps = ochoApps())

        // La hora formateada depende de la zona horaria del entorno; se comprueba la
        // franja de arriba, que es lo que importa: cabe el principio y cabe el final.
        composeRule.onNodeWithText("ZEN").assertIsDisplayed()
        composeRule.onNodeWithText("imagin").assertIsDisplayed()
        // La fila que se sumo a la home con la reticula llena: si empujara algo fuera,
        // lo primero en caerse seria lo de abajo.
        composeRule.onNodeWithText("Todas las aplicaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Notas").assertIsDisplayed()
        composeRule.onNodeWithText("Menú").assertIsDisplayed()
    }

    @Test
    fun `con el mando sonando y la reticula llena sigue cabiendo todo`() {
        // El peor caso de alto de siempre: ocho aplicaciones y el mando del reproductor
        // abierto. Sigue cabiendo sin desplazarse; que ahora se pueda desplazar no es
        // excusa para empezar a dejar cosas bajo el pliegue.
        composeRule.mainClock.autoAdvance = false
        render(homeApps = ochoApps(), mediaPlaying = true)

        composeRule.onNodeWithText("SONANDO").assertIsDisplayed()
        composeRule.onNodeWithText("imagin").assertIsDisplayed()
        composeRule.onNodeWithText("Todas las aplicaciones").assertIsDisplayed()
        composeRule.onNodeWithText("Notas").assertIsDisplayed()
        composeRule.onNodeWithText("Menú").assertIsDisplayed()
    }

    @Test
    fun `con mas aplicaciones de las que caben la home se desplaza`() {
        // El tope de ocho se quito, asi que la reticula puede pasar del alto de la
        // pantalla y hay que poder ir a buscar lo de abajo.
        render(homeApps = veinteApps())

        composeRule.onNodeWithText("App 20").assertIsNotDisplayed()
        composeRule.onNodeWithText("App 20").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el menu y la cabecera no se van con el desplazamiento`() {
        // Regresion que hundio al `verticalScroll` anterior: la home entera se
        // desplazaba y la fila "Menu" —la unica salida hacia restringidas, ajustes y el
        // resto— se iba bajo el pliegue. Ahora el area desplazable vive **entre** las
        // dos, asi que no hay sitio al que llegar donde falten.
        render(homeApps = veinteApps())

        composeRule.onNodeWithText("App 20").performScrollTo()

        composeRule.onNodeWithText("Menú").assertIsDisplayed()
        // La franja de cabecera: la cara del dia sigue arriba. Se comprueba con el glifo
        // y no con la fecha porque la fecha formateada depende de la zona horaria del
        // entorno donde corra el test.
        composeRule.onNodeWithText(":)").assertIsDisplayed()
    }

    @Test
    fun `desplazarse no hace falta para llegar a la lista completa con pocas apps`() {
        // La fila "Todas las aplicaciones" va dentro del area desplazable, no anclada:
        // con la reticula corta tiene que seguir viendose sin tocar nada.
        render(homeApps = listOf(app("com.phone", "Teléfono")))

        composeRule.onNodeWithText("Todas las aplicaciones").assertIsDisplayed()
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
        composeRule.onNodeWithText(":)").assertIsDisplayed()
        composeRule.onNodeWithText("Registro").assertIsDisplayed()

        composeRule.onNodeWithText("ZEN").assertDoesNotExist()
        composeRule.onNodeWithText("imagin").assertDoesNotExist()
        composeRule.onNodeWithText("Todas las aplicaciones").assertDoesNotExist()
        composeRule.onNodeWithText("Notas").assertDoesNotExist()

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
    fun `la fila de notas lleva a las notas`() {
        // Estuvo marcada PRONTO y sin reaccionar al toque mientras no existia: una fila
        // que se traga el toque en silencio ensena a desconfiar de las que si funcionan.
        // Ahora lleva a alguna parte, asi que se toca y ya no queda ni rastro del aviso.
        render(homeApps = listOf(app("com.phone", "Teléfono")))

        composeRule.onNodeWithText("PRONTO").assertDoesNotExist()
        composeRule.onNodeWithText("Notas").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, notesOpened)
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

    /**
     * El pulso de uso vive bajo la misma regla que el mando del reproductor: lo que no
     * tiene nada detras no se pinta. Con el dia en calma la home queda exactamente como
     * estaba, sin una cuarta fila permanente.
     */
    @Test
    fun `con el dia en calma el pulso de uso no se pinta`() {
        render(homeApps = ochoApps())

        // La cara sí está, en la franja; lo que no está es la fila con las cifras.
        composeRule.onNodeWithText(":)").assertIsDisplayed()
        composeRule.onNodeWithText("USO ALTO").assertDoesNotExist()
        composeRule.onNodeWithText("BIEN").assertDoesNotExist()
    }

    /**
     * Regresión vista en el dispositivo: con dos horas de las que Instagram se llevaba
     * el 77%, el escalón por tiempo decía NORMAL —así que no había pulso— pero la cara
     * ya estaba triste, porque una aplicación acaparando es lo que mira `UsageMood` y el
     * reloj no. Quedaba un `:(` en la franja sin una sola cifra que lo explicara, y al
     * tocarlo se llegaba a una pantalla que ponía NORMAL. El resumen y el detalle tienen
     * que decir lo mismo.
     */
    @Test
    fun `si la cara esta triste, el pulso aparece a explicarlo`() {
        render(
            homeApps = ochoApps(),
            usageReading = UsageReading(
                level = UsageLevel.NORMAL,
                screenMillis = 128 * 60_000L,
                unlocks = 17,
                topApp = AppUsage("com.instagram.android", 8, 98 * 60_000L),
                measured = true,
            ),
        )

        composeRule.onNodeWithText(":(").assertIsDisplayed()
        composeRule.onNodeWithText("USO ALTO").assertIsDisplayed()
        composeRule.onNodeWithText("2h 8m · 17").assertIsDisplayed()
    }

    @Test
    fun `con uso alto el pulso aparece y lleva a la pantalla de uso`() {
        render(
            homeApps = ochoApps(),
            usageReading = UsageReading(
                level = UsageLevel.ALTA,
                screenMillis = 200 * 60_000L,
                unlocks = 70,
                topApp = null,
                measured = true,
            ),
        )

        composeRule.onNodeWithText("USO ALTO").assertIsDisplayed()
        // Las dos cifras que decidieron el escalon, no una barra de progreso.
        composeRule.onNodeWithText("3h 20m · 70").assertIsDisplayed()

        composeRule.onNodeWithText("USO ALTO").performClick()
        assertEquals(1, usageOpened)
    }

    /**
     * Regresion: sin acceso de uso concedido el hueco venia con ceros y el pulso lo leia
     * como un dia ejemplar. No hay pulso sobre un dia que no se ha medido.
     */
    @Test
    fun `sin medida no hay pulso`() {
        render(
            homeApps = ochoApps(),
            usageReading = UsageReading(
                level = UsageLevel.CALMA,
                screenMillis = 0L,
                unlocks = 0,
                topApp = null,
                measured = false,
            ),
        )

        composeRule.onNodeWithText("CALMA").assertDoesNotExist()
    }

    @Test
    fun `el uso del movil se abre desde el menu, no desde una fila fija`() {
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("Uso del móvil").assertDoesNotExist()

        composeRule.onNodeWithText("Menú").performClick()
        composeRule.onNodeWithText("Uso del móvil").performClick()

        assertEquals(1, usageOpened)
    }

    /**
     * Notas vivia abajo, como una fila igual que "Menu", y por eso se leia como una
     * opcion de administracion en lugar de como el sitio donde se escribe. Ahora es una
     * celda mas de la reticula: con ocho aplicaciones le toca el numero 09.
     */
    @Test
    fun `Notas es una celda mas de la reticula, con su numero`() {
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("09").assertIsDisplayed()
        composeRule.onNodeWithText("Notas").performClick()
        assertEquals(1, notesOpened)
    }

    /**
     * Y la home no crece por ello: con un numero impar de aplicaciones, Notas cae en el
     * hueco que la ultima fila ya dejaba vacio y la reticula sigue midiendo lo mismo.
     */
    @Test
    fun `con un numero impar de aplicaciones Notas ocupa el hueco que ya sobraba`() {
        render(homeApps = ochoApps().dropLast(1))

        composeRule.onNodeWithText("08").assertIsDisplayed()
        composeRule.onNodeWithText("Notas").assertIsDisplayed()
    }

    /**
     * Lectura entra al lado de Notas por la misma razon: es algo que se abre a diario y
     * se usa como una aplicacion, no una opcion de administracion. Con ocho aplicaciones
     * le toca el numero 10, justo detras de Notas.
     */
    @Test
    fun `Lectura es la celda de al lado de Notas`() {
        render(homeApps = ochoApps())

        composeRule.onNodeWithText("10").assertIsDisplayed()
        composeRule.onNodeWithText("Lectura").performClick()
        assertEquals(1, readingOpened)
    }

    /**
     * Y las dos son las **unicas** celdas que no son aplicaciones. Este test es el tope:
     * si alguien anade una tercera, la reticula pasa de la ultima fila y empuja el reloj.
     */
    @Test
    fun `solo hay dos celdas que no son aplicaciones`() {
        render(homeApps = ochoApps())

        // Ocho aplicaciones mas Notas (09) y Lectura (10): no puede existir una 11.
        composeRule.onNodeWithText("11").assertDoesNotExist()
    }

    /** Y deja de estar abajo, junto a la fila que abre el menu. */
    @Test
    fun `Notas ya no es una fila al lado de Menu`() {
        render(homeApps = ochoApps())

        val notas = composeRule.onNodeWithText("Notas").getUnclippedBoundsInRoot()
        val menu = composeRule.onNodeWithText("Menú").getUnclippedBoundsInRoot()
        val reticula = composeRule.onNodeWithText("WhatsApp").getUnclippedBoundsInRoot()

        assertTrue(
            "Notas deberia estar en la reticula, no pegada al menu",
            notas.top < menu.top && notas.top > reticula.top,
        )
    }

    /**
     * El slot derecho de la franja llevaba "SIN SESIÓN", que en la home es una
     * constante: si hubiera sesión, la sesión sustituye a la pantalla entera. Ahora
     * lleva el resumen del día, que sí puede decir cosas distintas.
     */
    @Test
    fun `la franja lleva la cara del dia y ya no un rotulo constante`() {
        render(homeApps = ochoApps())

        composeRule.onNodeWithText(":)").assertIsDisplayed()
        composeRule.onNodeWithText("SIN SESIÓN").assertDoesNotExist()
    }

    @Test
    fun `la cara empeora cuando el dia se va de las manos`() {
        render(
            homeApps = ochoApps(),
            usageReading = UsageReading(
                level = UsageLevel.EXCESO,
                screenMillis = 400 * 60_000L,
                unlocks = 120,
                topApp = null,
                measured = true,
            ),
        )

        composeRule.onNodeWithText(":O").assertIsDisplayed()
    }

    /**
     * Sin acceso de uso la cara no opina: poner `:)` sin haber medido nada sería
     * felicitar por un día que no ha ocurrido.
     */
    @Test
    fun `sin medida la cara lo dice en lugar de sonreir`() {
        render(
            homeApps = ochoApps(),
            usageReading = UsageReading(
                level = UsageLevel.CALMA,
                screenMillis = 0L,
                unlocks = 0,
                topApp = null,
                measured = false,
            ),
        )

        composeRule.onNodeWithText(":?").assertIsDisplayed()
    }

    /**
     * `:)` es texto, pero no es texto que se pueda leer en voz alta: sin descripción, el
     * único resumen del día que hay en la home no existiría para quien no ve la pantalla.
     * Y una cara que enseña un problema tiene que llevar al detalle.
     */
    @Test
    fun `la cara se lee en palabras y lleva al detalle`() {
        render(homeApps = ochoApps())

        composeRule.onNodeWithContentDescription("Uso de hoy: bien. Ver el detalle")
            .performClick()

        assertEquals(1, usageOpened)
    }

    /** El tiempo va al lado de la cara: el glifo del cielo y los grados. */
    @Test
    fun `el tiempo se ensena en la franja al lado de la cara`() {
        render(
            homeApps = ochoApps(),
            weather = WeatherReading(18, WeatherCondition.DESPEJADO, observedAtMillis = 1_700_000_000_000),
        )

        composeRule.onNodeWithText("-O- 18°").assertIsDisplayed()
        composeRule.onNodeWithText(":)").assertIsDisplayed()
    }

    /**
     * Lo que no tiene nada detrás no se pinta: sin aplicación del tiempo publicando su
     * aviso, la franja queda exactamente como estaba. Ningún hueco, ningún guion.
     */
    @Test
    fun `sin dato del tiempo la franja no cambia`() {
        render(homeApps = ochoApps(), weather = null)

        // Ni los grados ni un hueco con un guion donde deberían estar.
        composeRule.onAllNodesWithText("°", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText(":)").assertIsDisplayed()
    }

    /**
     * `-O-` es texto, pero no es texto que se pueda leer en voz alta; misma razón que la
     * cara. Y ninguna cifra sin salida: el toque lleva a la pantalla del tiempo, donde
     * están la ciudad y la hora de la lectura.
     */
    @Test
    fun `el tiempo se lee en palabras y lleva al detalle`() {
        render(
            homeApps = ochoApps(),
            weather = WeatherReading(18, WeatherCondition.LLUVIA, observedAtMillis = 1_700_000_000_000),
        )

        composeRule.onNodeWithContentDescription("Tiempo: 18 grados, lluvia. Ver el detalle")
            .performClick()

        assertEquals(1, weatherOpened)
    }

    /** Sin cielo reconocido quedan los grados solos, que es la mitad que se mira. */
    @Test
    fun `sin cielo reconocido quedan los grados`() {
        render(
            homeApps = ochoApps(),
            weather = WeatherReading(7, condition = null, observedAtMillis = 1_700_000_000_000),
        )

        composeRule.onNodeWithText("7°").assertIsDisplayed()
    }
}
