package com.zenlauncher.zen.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.apps.EssentialApps
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.usage.UsageMood
import com.zenlauncher.zen.domain.usage.UsageReading
import com.zenlauncher.zen.presentation.components.MediaTransportBar
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.HomeTile
import com.zenlauncher.zen.presentation.components.ZenAppGrid
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.usage.UsagePulse
import com.zenlauncher.zen.presentation.usage.usageFaceDescription
import com.zenlauncher.zen.presentation.usage.usageFaceLabel
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenMotion
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/**
 * Pantalla de inicio.
 *
 * Jerarquia: la hora manda, con los tres botones propios de Zen a su derecha —empezar
 * una sesion, respirar un minuto y leer la portada del dia—; debajo, el mando del
 * reproductor y la reticula de aplicaciones que **no** quitan tiempo, ambos en la zona
 * del pulgar. Todo lo que administra la aplicacion —empezar una sesion desde el
 * principio, restringidas, registro y ajustes— vive plegado al final: son cosas que se
 * hacen una vez cada muchos dias y no tienen por que ocupar la pantalla que se mira
 * cincuenta veces al dia.
 *
 * Al abrir el menu, la pantalla **se sustituye entera**: solo queda la franja de
 * cabecera con la fecha y el estado de la sesion.
 *
 * **No se desplaza.** Una pantalla de inicio que se arrastra deja de ser un sitio fijo
 * y se convierte en una lista que explorar, que es justo lo que Zen evita: el reloj
 * tiene que estar siempre en el mismo pixel. El hueco sobrante se reparte con un peso,
 * asi que el menu queda anclado abajo en cualquier alto de pantalla.
 *
 * A cambio, con `fontScale` muy alto el contenido se recorta en lugar de poder
 * arrastrarse. Se compensa acotando lo que puede crecer: la reticula nunca pasa de
 * [EssentialApps.MAX_HOME_APPS] y el menu sustituye a la pantalla en lugar de sumarse
 * a ella.
 *
 * **Sin gestos propios de navegacion.** Aqui no hay ningun deslizamiento que abra una
 * pantalla: las tres filas fijas —lista completa, notas y menu— son lo que se toca. El
 * gesto de arrastrar hacia arriba abria la lista de aplicaciones desde cualquier punto,
 * tambien encima de la reticula y con el menu abierto, y lo que el usuario veia era una
 * pantalla que se le iba sola. En una pantalla de inicio, lo que abre algo tiene que
 * verse.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    usageReading: UsageReading,
    /**
     * El tiempo llega aparte del estado de la home, igual que la lectura de uso: su
     * dueno es `WeatherViewModel`, que vive en el ambito de la Activity porque el mismo
     * dato se ensena aqui y en su propia pantalla. Null cuando no hay ciudad elegida,
     * no hubo red todavia o el ultimo dato envejecio.
     */
    weather: WeatherReading?,
    onLaunchApp: (InstalledApp) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenHomeApps: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenReading: () -> Unit,
    onStartSession: () -> Unit,
    onBreathe: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenRestricted: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: (String?) -> Unit,
    onExitZen: () -> Unit,
    onPreviousTrack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenWeather: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    // `rememberSaveable`: el menu se cierra al salir de la aplicacion, pero no al girar
    // el telefono ni al volver de otra pantalla.
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    // Con el menu abierto, atras cierra en vez de no hacer nada. En la home el gesto se
    // traga a proposito —no hay a donde volver—, pero aqui si lo hay: el menu es la
    // unica cara de la pantalla de inicio de la que se puede salir, y quien viene de
    // cualquier otra aplicacion de Android espera que atras la cierre.
    //
    // El gesto de atras de Android llega tarde: con las barras ocultas, el sistema se
    // queda el primer deslizamiento para sacarlas. Zen lo reconoce por su cuenta y
    // cierra al primer intento (ver `EdgeBackPolicy`); esto es la otra via, la de la
    // tecla o el gesto que si llega.
    BackHandler(enabled = menuOpen) { menuOpen = false }

    ZenScreen(
        modifier = modifier,
        // Solo con el menu abierto: en la home no hay a donde volver, y un gesto que a
        // veces hace algo y a veces no es peor que uno que nunca hace nada.
        onSwipeBack = if (menuOpen) ({ menuOpen = false }) else null,
    ) {
        // La franja de cabecera es lo unico que sobrevive a abrir el menu: da igual
        // donde estes, el dia y el estado de la sesion siguen en el mismo pixel. Por
        // eso queda fuera del intercambio, quieta.
        // El slot derecho llevaba "SIN SESIÓN", que en esta pantalla es una constante:
        // si hubiera sesion, la sesion sustituye a la pantalla entera y esto no se ve.
        // Un rotulo que no puede decir otra cosa es exactamente el "00 permanente" que
        // Zen evita, asi que ahora lleva el unico resumen del dia que cabe en dos
        // caracteres.
        //
        // La cara no anade una fila ni mueve un pixel del reloj: vive en la franja que
        // ya estaba. Es la unica forma de meter algo permanente en la pantalla de inicio
        // sin que la pantalla crezca.
        //
        // Al lado de la cara va el tiempo, y por la misma razon: es el otro dato que se
        // mira cincuenta veces al dia y no cabia en ninguna otra parte de la home sin
        // anadir una fila. Aparece solo si la aplicacion del tiempo del telefono lo esta
        // publicando —lo que no tiene nada detras no se pinta—, asi que en un telefono
        // sin ella la franja queda exactamente como estaba.
        val face = UsageMood.face(usageReading)
        ZenHeaderStrip(
            left = ZenDateFormats.date(state.nowMillis, locale),
            right = face.glyph,
            // `:)` es texto, pero no es texto que se pueda leer en voz alta.
            rightDescription = stringResource(usageFaceDescription(face)),
            onRightClick = onOpenUsage,
            secondary = weather?.let { weatherGlyph(it) },
            secondaryDescription = weather?.let { weatherDescription(it) },
            onSecondaryClick = onOpenWeather,
        )

        // El menu ocupa la pantalla entera, no un hueco dentro de la home: al abrirlo se
        // va el reloj, el boton ZEN, la bateria y el mando. Son cosas que se hacen una
        // vez cada muchos dias y merecen atencion sin nada al lado; y sin
        // desplazamiento, compartir pantalla dejaba las ultimas acciones fuera.
        //
        // El cambio se anima para decir de donde sale: ver [ZenMotion.swap].
        AnimatedContent(
            targetState = menuOpen,
            transitionSpec = { ZenMotion.swap(openingMenu = targetState) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "home-menu",
        ) { open ->
            Column(Modifier.fillMaxSize()) {
                if (open) {
                    MenuBody(
                        state = state,
                        usageReading = usageReading,
                        onStartSession = onStartSession,
                        onOpenNotifications = onOpenNotifications,
                        onOpenRestricted = onOpenRestricted,
                        onOpenStats = onOpenStats,
                        onOpenUsage = onOpenUsage,
                        onOpenSettings = onOpenSettings,
                        onExitZen = onExitZen,
                    )
                } else {
                    HomeBody(
                        state = state,
                        usageReading = usageReading,
                        onLaunchApp = onLaunchApp,
                        onOpenDrawer = onOpenDrawer,
                        onOpenHomeApps = onOpenHomeApps,
                        onOpenNotes = onOpenNotes,
                        onOpenReading = onOpenReading,
                        onStartSession = onStartSession,
                        onBreathe = onBreathe,
                        onOpenNews = onOpenNews,
                        onOpenUsage = onOpenUsage,
                        onOpenNotifications = onOpenNotifications,
                        onPreviousTrack = onPreviousTrack,
                        onTogglePlayback = onTogglePlayback,
                        onNextTrack = onNextTrack,
                        onOpenPlayer = onOpenPlayer,
                    )
                }
            }
        }

        // La fila que abre y cierra tampoco se mueve: es el ancla de las dos caras, y lo
        // que se toco para abrir tiene que seguir donde estaba para cerrar.
        ZenHairline(color = ZenColors.Border)
        MenuToggleRow(open = menuOpen, onToggle = { menuOpen = !menuOpen })
        ZenHairline()
    }
}

/** La cara de siempre: reloj, estado del dispositivo y las aplicaciones. */
@Composable
private fun ColumnScope.HomeBody(
    state: HomeUiState,
    usageReading: UsageReading,
    onLaunchApp: (InstalledApp) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenHomeApps: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenReading: () -> Unit,
    onStartSession: () -> Unit,
    onBreathe: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenNotifications: (String?) -> Unit,
    onPreviousTrack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    Spacer(Modifier.height(ZenSpacing.XLarge))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = ZenDateFormats.time(state.nowMillis),
            style = ZenTextStyles.Clock,
            color = ZenColors.Foreground,
        )
        // Las acciones con boton propio, apiladas donde cae el pulgar al mirar la
        // hora: arrancar una sesion, respirar un minuto y leer la portada del dia.
        // Ninguna abre una aplicacion ajena; son las cosas que Zen sabe hacer por si
        // mismo, y por eso no bajan al menu.
        //
        // NOTICIAS es el tercero y **el ultimo que cabe**: los tres marcos miden mas de
        // alto que el reloj que tienen al lado, y el cuarto se comeria el aire que la
        // reticula reparte abajo. Lo siguiente que se quiera anadir va al menu.
        //
        // Va aqui abajo y no en la reticula porque no es una aplicacion —no se lanza
        // nada, se abre una pantalla de Zen— y porque leer noticias es justo lo que un
        // launcher de este tipo no debe poner en el camino: hay que ir a buscarlo, como
        // Respira.
        //
        // `IntrinsicSize.Max` mide el rotulo mas largo —NOTICIAS— y los tres marcos
        // salen con ese ancho: apilados, marcos de anchos distintos se leen como un
        // fallo de maquetacion.
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.End,
        ) {
            ZenTagButton(
                text = stringResource(R.string.home_zen_button),
                onClick = onStartSession,
                onClickLabel = stringResource(R.string.home_zen_button_label),
                modifier = Modifier.fillMaxWidth(),
                stretch = true,
            )
            ZenTagButton(
                text = stringResource(R.string.home_breathe_button),
                onClick = onBreathe,
                onClickLabel = stringResource(R.string.home_breathe_button_label),
                modifier = Modifier.fillMaxWidth(),
                stretch = true,
            )
            ZenTagButton(
                text = stringResource(R.string.home_news_button),
                onClick = onOpenNews,
                onClickLabel = stringResource(R.string.home_news_button_label),
                modifier = Modifier.fillMaxWidth(),
                stretch = true,
            )
        }
    }

    // Aqui vivian el medidor de bateria y la franja de conexiones. Se quitaron al dejar
    // de ocultar la barra de estado (ver `SystemBarsPolicy`): decian exactamente lo
    // mismo que el sistema dibuja dos centimetros mas arriba, y repetir un dato no lo
    // hace mas legible, solo llena la pantalla. La hora se queda porque su tamano **es**
    // la pantalla de inicio, no un indicador mas.

    // El mando solo existe si hay algo que mandar: ver `HomeUiState.mediaVisible`. Crece
    // y se encoge en su sitio en vez de aparecer de golpe, que era lo que daba el salto
    // al empezar a sonar algo. La primera composicion no anima: si ya sonaba musica al
    // entrar en Zen, el mando esta ahi desde el primer fotograma.
    AnimatedVisibility(
        visible = state.mediaVisible,
        enter = ZenMotion.RevealEnter,
        exit = ZenMotion.RevealExit,
    ) {
        Column {
            Spacer(Modifier.height(ZenSpacing.Large))

            MediaTransportBar(
                playing = state.mediaPlaying,
                nowPlaying = state.nowPlaying,
                onPrevious = onPreviousTrack,
                onTogglePlayback = onTogglePlayback,
                onNext = onNextTrack,
                onOpenPlayer = onOpenPlayer,
            )
        }
    }

    // El pulso del dia. Misma regla que el mando de arriba —lo que no tiene nada detras
    // no se pinta—: con el dia en calma o en normal esto no existe y la home queda
    // exactamente como estaba. No es una cuarta fila permanente; es una fila que casi
    // siempre no esta. Ver [UsagePulse].
    AnimatedVisibility(
        visible = usageReading.worthShowing,
        enter = ZenMotion.RevealEnter,
        exit = ZenMotion.RevealExit,
    ) {
        Column {
            Spacer(Modifier.height(ZenSpacing.Large))
            ZenHairline()
            UsagePulse(reading = usageReading, onOpen = onOpenUsage)
            ZenHairline()
        }
    }

    Spacer(Modifier.height(ZenSpacing.Large))

    // Solo se rotula lo que necesita explicarse. La reticula de favoritos se entiende
    // sola: preguntarle al usuario "¿QUE NECESITAS?" cada vez que mira la hora era la
    // aplicacion hablando por hablar.
    if (state.usingEssentials) {
        MonoLabel(text = stringResource(R.string.home_essentials))
        Spacer(Modifier.height(ZenSpacing.Small))
    }

    // Notas y Lectura son celdas mas de la reticula, no filas de abajo junto a "Menu".
    // Estuvo ahi y se leia como una opcion de administracion —mismo filete, mismo tono,
    // mismos dos puntos que "Menu"— cuando es el sitio donde se escribe, algo que se
    // abre a diario y se usa como una aplicacion. Aqui lo parece porque lo es.
    //
    // Y la home no crece: la fila de ancho completo que ocupaba (64dp) se va, y en su
    // lugar entra media fila de reticula (60dp). Con un numero impar de aplicaciones cae
    // en el hueco que la ultima fila ya dejaba vacio y no ocupa ni eso.
    //
    // Lectura entra al lado de Notas y por la misma razon: es algo que se abre a diario
    // y se usa como una aplicacion, no una opcion de administracion. Las dos juntas
    // ocupan **una fila entera** de reticula, asi que con un numero par de aplicaciones
    // la home crece una fila (60dp) respecto a tener solo Notas. Es el limite: la
    // reticula no puede pasar de [EssentialApps.MAX_HOME_APPS] y **estas dos celdas son
    // las unicas que no son aplicaciones**. La siguiente idea de "algo siempre visible"
    // vuelve a ser el menu.
    ZenAppGrid(
        tiles = state.homeApps.map { app ->
            HomeTile(
                label = app.label,
                onClick = { onLaunchApp(app) },
                notifications = state.notificationCounts[app.packageName] ?: 0,
                onOpenNotifications = { onOpenNotifications(app.packageName) },
            )
        } + HomeTile(
            label = stringResource(R.string.home_notes),
            onClick = onOpenNotes,
        ) + HomeTile(
            label = stringResource(R.string.home_reading),
            onClick = onOpenReading,
        ),
    )
    ZenHairline()

    // La lista completa, a la vista y pegada a la reticula: es "lo que no cabe aqui",
    // y se lee justo despues de lo que si cabe. Estuvo escondida tras un deslizamiento
    // hacia arriba, y ese gesto se quito: se disparaba desde cualquier punto de la
    // pantalla de inicio —tambien con el menu abierto y sobre la propia reticula—, asi
    // que la lista se abria sola en mitad de cualquier otra intencion. Un launcher no
    // puede tener una puerta que se abre cuando no la tocas.
    ZenListRow(
        label = stringResource(R.string.home_all_apps),
        index = "··",
        labelColor = ZenColors.Secondary,
        onClick = onOpenDrawer,
    )
    ZenHairline()

    // Solo si no hay nada que ensenar: mientras las esenciales resuelvan, pedir que se
    // elijan aplicaciones seria ruido. Lleva a la pantalla que solo hace eso, no a
    // Ajustes enteros: quien acaba de instalar Zen quiere elegir, no configurar.
    if (state.homeApps.isEmpty()) {
        ZenListRow(
            label = stringResource(R.string.home_choose_apps),
            index = "··",
            labelColor = ZenColors.Disabled,
            onClick = onOpenHomeApps,
        )
        ZenHairline()
    }

    // Todo el aire sobrante va aqui: la fila del menu queda anclada abajo y la parte de
    // arriba no se mueve.
    Spacer(Modifier.weight(1f).fillMaxWidth())

}

/** La otra cara: solo las acciones de administracion. */
@Composable
private fun ColumnScope.MenuBody(
    state: HomeUiState,
    usageReading: UsageReading,
    onStartSession: () -> Unit,
    onOpenNotifications: (String?) -> Unit,
    onOpenRestricted: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitZen: () -> Unit,
) {
    Spacer(Modifier.height(ZenSpacing.XLarge))

    MonoLabel(text = stringResource(R.string.home_menu_section))
    Spacer(Modifier.height(ZenSpacing.Small))

    HomeActions(
        restrictedCount = state.restrictedCount,
        notificationTotal = state.notificationTotal,
        usageReading = usageReading,
        onOpenNotifications = { onOpenNotifications(null) },
        onExitZen = onExitZen,
        onStartSession = onStartSession,
        onOpenRestricted = onOpenRestricted,
        onOpenStats = onOpenStats,
        onOpenUsage = onOpenUsage,
        onOpenSettings = onOpenSettings,
    )

    Spacer(Modifier.weight(1f).fillMaxWidth())
}

/**
 * Color del rotulo "Menú": **encendido cuando el menu esta abierto**.
 *
 * Es la misma fila en las dos caras de la pantalla, asi que sin esto nada distinguia
 * "puedes abrir" de "estas dentro". Blanco no es decorativo: es el mismo tono que el
 * resto del texto principal, y solo lo lleva la fila cuando manda. Nunca es la unica
 * senal —al lado sigue leyendose ABRIR o CERRAR—, para no depender del contraste.
 */
internal fun menuLabelColor(open: Boolean): Color =
    if (open) ZenColors.Foreground else ZenColors.Muted

/**
 * La fila que abre y cierra el menu. Es la misma en las dos caras para que el dedo no
 * tenga que buscar: lo que se toco para abrir esta donde se toca para cerrar.
 */
@Composable
private fun MenuToggleRow(open: Boolean, onToggle: () -> Unit) {
    ZenListRow(
        label = stringResource(R.string.home_menu),
        index = "··",
        labelColor = menuLabelColor(open),
        onClick = onToggle,
        trailing = {
            MonoLabel(
                text = stringResource(
                    if (open) R.string.home_menu_hide else R.string.home_menu_show,
                ),
            )
        },
    )
}

/**
 * Las acciones de administracion, guardadas tras la fila "Menu".
 *
 * Guardadas y no a la vista porque cada fila permanente en la pantalla de inicio es una
 * invitacion a tocarla; a un toque y no en otra pantalla porque son cosas que se hacen
 * una vez cada muchos dias, pero cuando se hacen no deben costar una busqueda.
 */
@Composable
private fun HomeActions(
    restrictedCount: Int,
    notificationTotal: Int,
    usageReading: UsageReading,
    onOpenNotifications: () -> Unit,
    onExitZen: () -> Unit,
    onStartSession: () -> Unit,
    onOpenRestricted: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ZenHairline()
    ZenListRow(
        label = stringResource(R.string.action_start_zen),
        onClick = onStartSession,
    )
    ZenHairline()
    // La via de siempre es la marca de la propia aplicacion; esta fila esta para lo que
    // no tiene marca porque no esta en la reticula.
    ZenListRow(
        label = stringResource(R.string.action_notifications),
        labelColor = ZenColors.Secondary,
        onClick = onOpenNotifications,
        trailing = { MonoLabel(text = "%02d".format(notificationTotal)) },
    )
    ZenHairline()
    ZenListRow(
        label = stringResource(R.string.action_restricted),
        labelColor = ZenColors.Secondary,
        onClick = onOpenRestricted,
        trailing = { MonoLabel(text = "%02d".format(restrictedCount)) },
    )
    ZenHairline()
    // El uso del movil entra por aqui y no por una fila fija de la home: en un dia
    // tranquilo no hay nada que decir, y la puerta tiene que existir igual para poder
    // mirarlo cuando a uno le apetezca. El escalon viaja en el rotulo de la derecha,
    // asi que el menu ya lo dice sin abrir nada.
    ZenListRow(
        label = stringResource(R.string.action_usage),
        labelColor = ZenColors.Secondary,
        onClick = onOpenUsage,
        trailing = {
            MonoLabel(
                text = stringResource(usageFaceLabel(UsageMood.face(usageReading))),
                // El ambar esta reservado a las marcas de estado de 6dp, nunca a
                // texto: aqui el escalon se distingue por el tono, y lo que dice es la
                // palabra —USO ALTO, EXCESO—, no el color.
                color = if (usageReading.worthShowing) ZenColors.Foreground else ZenColors.Dim,
            )
        },
    )
    ZenHairline()
    ZenListRow(
        label = stringResource(R.string.action_stats),
        labelColor = ZenColors.Secondary,
        onClick = onOpenStats,
    )
    ZenHairline()
    ZenListRow(
        label = stringResource(R.string.action_settings),
        labelColor = ZenColors.Secondary,
        onClick = onOpenSettings,
    )
    ZenHairline()
    // Una pantalla de inicio de la que no se sabe salir es una trampa. Android no deja
    // renunciar al rol desde la aplicacion, asi que esto abre el selector del sistema:
    // es la salida, y esta a la vista en lugar de escondida en Ajustes.
    ZenListRow(
        label = stringResource(R.string.action_exit_zen),
        // El unico rojo de la aplicacion: seis filas te mueven dentro de Zen y esta te
        // saca. Ver [ZenColors.Danger].
        labelColor = ZenColors.Danger,
        onClick = onExitZen,
    )
}
