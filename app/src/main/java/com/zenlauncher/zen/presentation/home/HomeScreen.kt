package com.zenlauncher.zen.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.apps.EssentialApps
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.domain.system.SwipeUpPolicy
import com.zenlauncher.zen.presentation.components.MediaTransportBar
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenAppGrid
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenMotion
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/**
 * Recorrido minimo para que un arrastre cuente como "abrir la lista": por encima del
 * toque accidental y por debajo de lo que cansa el pulgar.
 */
private const val SWIPE_UP_THRESHOLD_PX = 160f

/**
 * Pantalla de inicio.
 *
 * Jerarquia: la hora manda, con el boton de sesion a su derecha; debajo, el mando del
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
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onLaunchApp: (InstalledApp) -> Unit,
    onOpenDrawer: () -> Unit,
    onStartSession: () -> Unit,
    onOpenRestricted: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: (String?) -> Unit,
    onExitZen: () -> Unit,
    onPreviousTrack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenPlayer: () -> Unit,
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

    // Deslizar hacia arriba abre la lista completa, como en cualquier launcher. Sustituye
    // a la fila "Todas las aplicaciones", que ocupaba sitio permanente en la pantalla que
    // mas se mira para algo que se hace de vez en cuando. La lista sigue estando ademas
    // en el menu, para que el gesto no sea la unica via de acceso.
    //
    // El borde inferior es del sistema, no de Zen: ver [SwipeUpPolicy].
    val systemEdgePx = WindowInsets.systemGestures.getBottom(LocalDensity.current).toFloat()
    val minSystemEdgePx = with(LocalDensity.current) {
        SwipeUpPolicy.MIN_SYSTEM_EDGE_DP.dp.toPx()
    }
    val systemEdge = maxOf(systemEdgePx, minSystemEdgePx)

    // Donde toco el dedo de verdad. No vale el punto que da `onDragStart`: ese es donde
    // el arrastre supero el umbral de deteccion, ya lejos del borde, y con el la franja
    // del sistema no protegia nada. Se observa en la pasada Initial y **sin consumir**,
    // asi que ningun otro gesto de la pantalla cambia de comportamiento.
    val touchDownY = remember { mutableFloatStateOf(0f) }
    val trackTouchDown = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touchDownY.floatValue = down.position.y
        }
    }

    val swipeUp = Modifier.pointerInput(onOpenDrawer, systemEdge) {
        var accumulated = 0f
        detectVerticalDragGestures(
            onDragStart = { accumulated = 0f },
            onDragEnd = {
                val opens = SwipeUpPolicy.opensDrawer(
                    startY = touchDownY.floatValue,
                    height = size.height.toFloat(),
                    systemEdge = systemEdge,
                    dragged = accumulated,
                    threshold = SWIPE_UP_THRESHOLD_PX,
                )
                if (opens) onOpenDrawer()
            },
        ) { change, dragAmount ->
            accumulated += dragAmount
            change.consume()
        }
    }

    ZenScreen(
        modifier = modifier.then(trackTouchDown).then(swipeUp),
        // Solo con el menu abierto: en la home no hay a donde volver, y un gesto que a
        // veces hace algo y a veces no es peor que uno que nunca hace nada.
        onSwipeBack = if (menuOpen) ({ menuOpen = false }) else null,
    ) {
        // La franja de cabecera es lo unico que sobrevive a abrir el menu: da igual
        // donde estes, el dia y el estado de la sesion siguen en el mismo pixel. Por
        // eso queda fuera del intercambio, quieta.
        ZenHeaderStrip(
            left = ZenDateFormats.date(state.nowMillis, locale),
            right = stringResource(R.string.home_header_idle),
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
                        onOpenDrawer = onOpenDrawer,
                        onStartSession = onStartSession,
                        onOpenNotifications = onOpenNotifications,
                        onOpenRestricted = onOpenRestricted,
                        onOpenStats = onOpenStats,
                        onOpenSettings = onOpenSettings,
                        onExitZen = onExitZen,
                    )
                } else {
                    HomeBody(
                        state = state,
                        onLaunchApp = onLaunchApp,
                        onStartSession = onStartSession,
                        onOpenSettings = onOpenSettings,
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
    onLaunchApp: (InstalledApp) -> Unit,
    onStartSession: () -> Unit,
    onOpenSettings: () -> Unit,
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
        // Arrancar una sesion es la unica accion que merece un boton propio, y esta
        // donde cae el pulgar al mirar la hora.
        ZenTagButton(
            text = stringResource(R.string.home_zen_button),
            onClick = onStartSession,
            onClickLabel = stringResource(R.string.home_zen_button_label),
        )
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

    Spacer(Modifier.height(ZenSpacing.Large))

    // Solo se rotula lo que necesita explicarse. La reticula de favoritos se entiende
    // sola: preguntarle al usuario "¿QUE NECESITAS?" cada vez que mira la hora era la
    // aplicacion hablando por hablar.
    if (state.usingEssentials) {
        MonoLabel(text = stringResource(R.string.home_essentials))
        Spacer(Modifier.height(ZenSpacing.Small))
    }

    ZenAppGrid(
        apps = state.homeApps,
        notificationCounts = state.notificationCounts,
        onLaunchApp = onLaunchApp,
        onOpenNotifications = { app -> onOpenNotifications(app.packageName) },
    )
    ZenHairline()

    // Solo si no hay nada que ensenar: mientras las esenciales resuelvan, pedir que se
    // elijan aplicaciones seria ruido.
    if (state.homeApps.isEmpty()) {
        ZenListRow(
            label = stringResource(R.string.home_choose_apps),
            index = "··",
            labelColor = ZenColors.Disabled,
            onClick = onOpenSettings,
        )
        ZenHairline()
    }

    // Todo el aire sobrante va aqui: las notas quedan pegadas a la fila del menu y la
    // parte de arriba no se mueve.
    Spacer(Modifier.weight(1f).fillMaxWidth())

    ZenHairline()
    // Sitio reservado para las notas rapidas con IA: recordatorios que se generan solos
    // e ideas que se enlazan entre si. Vive abajo, con "Menu", y no encima de la
    // reticula: lo de arriba es para lo que se usa cada dia. Todavia no hace nada, y por
    // eso **no es pulsable**: una fila que se traga el toque en silencio ensena al
    // usuario a desconfiar de las que si funcionan.
    ZenListRow(
        label = stringResource(R.string.home_notes),
        index = "··",
        labelColor = ZenColors.Muted,
        trailing = { MonoLabel(text = stringResource(R.string.home_notes_soon)) },
    )
}

/** La otra cara: solo las acciones de administracion. */
@Composable
private fun ColumnScope.MenuBody(
    state: HomeUiState,
    onOpenDrawer: () -> Unit,
    onStartSession: () -> Unit,
    onOpenNotifications: (String?) -> Unit,
    onOpenRestricted: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitZen: () -> Unit,
) {
    Spacer(Modifier.height(ZenSpacing.XLarge))

    MonoLabel(text = stringResource(R.string.home_menu_section))
    Spacer(Modifier.height(ZenSpacing.Small))

    HomeActions(
        restrictedCount = state.restrictedCount,
        notificationTotal = state.notificationTotal,
        onOpenDrawer = onOpenDrawer,
        onOpenNotifications = { onOpenNotifications(null) },
        onExitZen = onExitZen,
        onStartSession = onStartSession,
        onOpenRestricted = onOpenRestricted,
        onOpenStats = onOpenStats,
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
    onOpenDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onExitZen: () -> Unit,
    onStartSession: () -> Unit,
    onOpenRestricted: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ZenHairline()
    ZenListRow(
        label = stringResource(R.string.action_start_zen),
        onClick = onStartSession,
    )
    ZenHairline()
    // El gesto no puede ser la unica puerta a la lista completa: un gesto que no se ve
    // no existe para quien no lo conoce.
    ZenListRow(
        label = stringResource(R.string.home_all_apps),
        labelColor = ZenColors.Secondary,
        onClick = onOpenDrawer,
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
