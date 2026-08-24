package com.zenlauncher.zen.presentation

import android.Manifest
import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.zenlauncher.zen.R
import com.zenlauncher.zen.ZenApplication
import com.zenlauncher.zen.domain.battery.BatterySaverController
import com.zenlauncher.zen.domain.media.MediaTransport
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.notifications.NotificationsRepository
import com.zenlauncher.zen.domain.system.LockTaskAction
import com.zenlauncher.zen.domain.system.ScreenLocker
import com.zenlauncher.zen.domain.system.LockTaskDecision
import com.zenlauncher.zen.domain.system.LockTaskState
import com.zenlauncher.zen.domain.system.SystemBarsPolicy
import com.zenlauncher.zen.presentation.components.LocalDoubleTapToLock
import com.zenlauncher.zen.presentation.home.HomeViewModel
import com.zenlauncher.zen.presentation.navigation.ZenNavHost
import com.zenlauncher.zen.presentation.session.ActiveSessionScreen
import com.zenlauncher.zen.presentation.session.SessionSummaryScreen
import com.zenlauncher.zen.presentation.session.SessionViewModel
import com.zenlauncher.zen.presentation.theme.ZenTheme
import com.zenlauncher.zen.system.HomeRoleTarget
import com.zenlauncher.zen.system.toInsetsTypeMask

/**
 * Unica Activity de Zen, y a la vez la pantalla de inicio del dispositivo.
 *
 * Cuando hay una sesion activa, esta Activity **es** la sesion: no se navega a ella,
 * la sustituye. Eso hace que pulsar Inicio durante una sesion lleve al cronometro y no
 * a una lista de aplicaciones, que es justo lo que se quiere evitar.
 */
class ZenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applySystemBars()

        val container = (application as ZenApplication).container
        val factory = zenViewModelFactory(container)

        setContent {
            ZenTheme {
                CompositionLocalProvider(
                    LocalDoubleTapToLock provides { container.screenLocker.lock() },
                ) {
                    ZenRoot(
                        factory = factory,
                        screenLocker = container.screenLocker,
                        media = container.mediaTransport,
                        notifications = container.postedNotifications,
                        onSessionActiveChanged = ::applyLockTask,
                    )
                }
            }
        }
    }

    /**
     * Ancla la pantalla mientras dura la sesion.
     *
     * `startLockTask` sin Device Owner deja el sistema en modo **anclado**: bloquea el
     * panel de notificaciones, Inicio y Recientes. Android garantiza una salida
     * manteniendo Atras y Recientes a la vez, y esa salida no puede quitarse sin Device
     * Owner: por eso esto no es un kiosco de verdad, y no se anuncia como tal.
     */
    private fun applyLockTask(sessionActive: Boolean): LockTaskState {
        val action = LockTaskDecision.decide(sessionActive, currentLockTaskState())
        try {
            when (action) {
                LockTaskAction.START -> startLockTask()
                LockTaskAction.STOP -> stopLockTask()
                LockTaskAction.NONE -> Unit
            }
        } catch (error: IllegalStateException) {
            // El sistema puede rechazarlo, por ejemplo si la Activity no esta al frente.
            Log.w("ZenActivity", "No se pudo cambiar el anclado de pantalla", error)
        }
        // Se devuelve el estado real, no el pretendido: si el sistema rechazo el
        // anclado, la pantalla de sesion no debe prometer que no se puede salir.
        return currentLockTaskState()
    }

    private fun currentLockTaskState(): LockTaskState {
        val manager = getSystemService(ActivityManager::class.java)
        return when (manager?.lockTaskModeState) {
            ActivityManager.LOCK_TASK_MODE_PINNED -> LockTaskState.PINNED
            ActivityManager.LOCK_TASK_MODE_LOCKED -> LockTaskState.LOCKED
            else -> LockTaskState.NONE
        }
    }

    /**
     * Volver de otra aplicacion, o descartar el panel de notificaciones, devuelve las
     * barras del sistema. Hay que volver a ocultarlas al recuperar el foco.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    /**
     * Zen oculta la barra de gestos y **deja la de estado** (ver [SystemBarsPolicy]).
     *
     * Se usa BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, no un bloqueo: **es lo maximo que
     * puede hacer una aplicacion sin Device Owner**. Deslizando desde un borde la barra
     * de gestos vuelve un momento, porque ocultarla busca quitar el estimulo constante,
     * no el acceso deliberado. **Durante una sesion** el anclado de pantalla si bloquea
     * de verdad Inicio, Recientes y el panel de notificaciones (ver [applyLockTask]).
     *
     * Los iconos de la barra de estado se fuerzan en claro: el fondo de Zen es negro
     * siempre, y si el sistema esta en tema claro los pintaria oscuros —invisibles—
     * porque `enableEdgeToEdge` los elige segun el tema del sistema, no segun el de la
     * aplicacion.
     *
     * El contenido no se descoloca porque las pantallas se separan con
     * WindowInsets.safeDrawing, que ahora reserva tambien el alto de la barra de estado.
     */
    private fun applySystemBars() {
        window.insetsController?.apply {
            systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(SystemBarsPolicy.hidden.toInsetsTypeMask())
            setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        }
    }
}

@Composable
private fun ZenRoot(
    factory: androidx.lifecycle.ViewModelProvider.Factory,
    screenLocker: ScreenLocker,
    media: MediaTransport,
    notifications: NotificationsRepository,
    onSessionActiveChanged: (Boolean) -> LockTaskState,
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val sessionViewModel: SessionViewModel = viewModel(factory = factory)
    val homeViewModel: HomeViewModel = viewModel(factory = factory)

    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val finished by sessionViewModel.finished.collectAsStateWithLifecycle()
    val confirming by sessionViewModel.confirmingFinish.collectAsStateWithLifecycle()

    var isDefaultLauncher by remember { mutableStateOf(context.holdsHomeRole()) }
    var doubleTapLockEnabled by remember { mutableStateOf(screenLocker.canLock()) }
    var lockTaskState by remember { mutableStateOf(LockTaskState.NONE) }
    var nowPlayingEnabled by remember { mutableStateOf(media.hasMetadataAccess()) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { isDefaultLauncher = context.holdsHomeRole() }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { doubleTapLockEnabled = screenLocker.canLock() }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Si se deniega, la sesion funciona igual: solo se pierde el aviso. */ }

    // Al volver a primer plano hay que cerrar la sesion si venció mientras no se veia,
    // y refrescar si Zen sigue siendo el launcher por defecto.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.onResumed()
                isDefaultLauncher = context.holdsHomeRole()
                // El administrador puede haberse revocado desde Ajustes de Android.
                doubleTapLockEnabled = screenLocker.canLock()
                // Y el acceso a notificaciones se concede o se quita en esa misma
                // pantalla del sistema, de la que se vuelve por aqui.
                nowPlayingEnabled = media.hasMetadataAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val active = sessionState.active

    // El anclado sigue a la sesion, no al ciclo de vida: entra al empezar y se suelta
    // al terminar, venga el final del usuario o de la alarma.
    LaunchedEffect(active != null) { lockTaskState = onSessionActiveChanged(active != null) }

    when {
        // La sesion activa manda sobre cualquier ruta.
        active != null -> {
            // Durante la sesion, atras no lleva a ninguna parte: no hay a donde volver.
            BackHandler(enabled = true) { /* deliberadamente vacio */ }

            ActiveSessionScreen(
                state = sessionState,
                session = active,
                confirming = confirming,
                pinned = lockTaskState != LockTaskState.NONE,
                onRequestFinish = sessionViewModel::requestFinish,
                onCancelFinish = sessionViewModel::cancelFinish,
                onConfirmFinish = sessionViewModel::confirmFinish,
                onTimerReachedZero = sessionViewModel::onTimerReachedZero,
            )
        }

        finished != null -> {
            BackHandler(enabled = true) { sessionViewModel.consumeSummary() }

            SessionSummaryScreen(
                session = finished!!,
                onBack = {
                    sessionViewModel.consumeSummary()
                    navController.popBackStack(
                        route = com.zenlauncher.zen.presentation.navigation.ZenRoute.HOME,
                        inclusive = false,
                    )
                },
            )
        }

        else -> ZenNavHost(
            navController = navController,
            factory = factory,
            sessionViewModel = sessionViewModel,
            isDefaultLauncher = isDefaultLauncher,
            doubleTapLockEnabled = doubleTapLockEnabled,
            nowPlayingEnabled = nowPlayingEnabled,
            // Conceder y revocar viven en la misma pantalla del sistema: Android no
            // permite quitarlo desde la aplicacion, igual que con el administrador.
            onToggleNowPlaying = { context.safeStartActivity(media.metadataAccessIntent()) },
            // Es la misma pantalla del sistema y la misma concesion que la informacion
            // de la cancion: se pregunta al repositorio de avisos para que la pantalla
            // que lo pide no dependa del reproductor.
            onGrantNotificationAccess = {
                context.safeStartActivity(notifications.accessIntent())
            },
            // Salir de Zen es elegir otra pantalla de inicio: Android no deja renunciar
            // al rol desde la aplicacion, solo abrir el selector. Se usa el mismo
            // lanzador con resultado que el resto para que al volver se refresque si
            // Zen sigue siendo el launcher.
            onExitZen = { context.requestHomeRole(alreadyHome = true, roleLauncher::launch) },
            // Abrir un enlace de una nota en el navegador. `safeStartActivity` degrada
            // solo: en un dispositivo sin navegador esto avisa en vez de reventar el
            // launcher, que es lo que haria una excepcion sin capturar aqui.
            onOpenLink = { url ->
                context.safeStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
            onToggleDoubleTapLock = {
                // Revocar no se puede hacer desde la aplicacion: Android exige que el
                // usuario lo quite el mismo en Ajustes, y eso esta bien asi.
                if (doubleTapLockEnabled) {
                    context.safeStartActivity(screenLocker.disableIntent())
                } else {
                    deviceAdminLauncher.launch(screenLocker.enableIntent())
                }
            },
            onRequestHomeRole = {
                context.requestHomeRole(isDefaultLauncher, roleLauncher::launch)
            },
            onOpenBatterySaver = {
                when (val result = sessionViewModel.requestBatterySaver()) {
                    is BatterySaverController.RequestResult.RequiresUserAction ->
                        context.safeStartActivity(result.intent)

                    BatterySaverController.RequestResult.AlreadyEnabled -> Unit
                    BatterySaverController.RequestResult.Unsupported ->
                        context.toast(R.string.error_no_settings_screen)
                }
            },
            onOpenAccessibility = {
                context.safeStartActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onStartSession = { duration: ZenDuration ->
                // Se pide el permiso justo antes de la primera sesion, no al arrancar:
                // asi la peticion llega con el contexto que la justifica. Si ya esta
                // concedido o denegado permanentemente, el sistema no muestra nada.
                // No hace falta comprobar la version: minSdk 34 > TIRAMISU.
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                sessionViewModel.start(duration)
            },
        )
    }
}

private fun Context.holdsHomeRole(): Boolean {
    val roleManager = getSystemService(RoleManager::class.java) ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
}

/**
 * `ROLE_HOME` es la via publica para pedir ser el launcher. Si la ROM no ofrece ese
 * dialogo, se cae a la pantalla de ajustes de aplicacion de inicio.
 *
 * Si Zen **ya** tiene el rol, `createRequestRoleIntent` no sirve (el sistema no ofrece
 * un dialogo para renunciar a un rol que ya se tiene): hay que abrir el selector de
 * aplicacion de inicio, que es donde se elige otro launcher. Sin esto, Zen seria una
 * via de un solo sentido.
 */
private fun Context.requestHomeRole(alreadyHome: Boolean, launch: (Intent) -> Unit) {
    val roleManager = getSystemService(RoleManager::class.java)
    val target = HomeRoleTarget.of(
        alreadyHome = alreadyHome,
        roleAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true,
    )
    val intent = when (target) {
        HomeRoleTarget.REQUEST_ROLE ->
            checkNotNull(roleManager).createRequestRoleIntent(RoleManager.ROLE_HOME)

        HomeRoleTarget.HOME_SETTINGS -> Intent(Settings.ACTION_HOME_SETTINGS)
    }
    launch(intent)
}

private fun Context.safeStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        Log.w("ZenActivity", "No hay actividad para $intent", error)
        toast(R.string.error_no_settings_screen)
    }
}

private fun Context.toast(messageRes: Int) {
    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}
