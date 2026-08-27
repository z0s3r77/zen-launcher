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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.zenlauncher.zen.domain.usage.UsageRepository
import com.zenlauncher.zen.presentation.components.LocalDoubleTapToLock
import com.zenlauncher.zen.presentation.home.HomeViewModel
import com.zenlauncher.zen.presentation.navigation.ZenNavHost
import com.zenlauncher.zen.presentation.navigation.ZenRoute
import com.zenlauncher.zen.presentation.session.ActiveSessionScreen
import com.zenlauncher.zen.presentation.session.SessionSummaryScreen
import com.zenlauncher.zen.presentation.session.SessionViewModel
import com.zenlauncher.zen.presentation.theme.ZenTheme
import com.zenlauncher.zen.presentation.usage.DistractionScreen
import com.zenlauncher.zen.presentation.usage.UsageViewModel
import com.zenlauncher.zen.presentation.weather.WeatherViewModel
import com.zenlauncher.zen.system.HomeRoleTarget
import com.zenlauncher.zen.system.LauncherMemory
import com.zenlauncher.zen.system.toInsetsTypeMask

/**
 * Unica Activity de Zen, y a la vez la pantalla de inicio del dispositivo.
 *
 * Cuando hay una sesion activa, esta Activity **es** la sesion: no se navega a ella,
 * la sustituye. Eso hace que pulsar Inicio durante una sesion lleve al cronometro y no
 * a una lista de aplicaciones, que es justo lo que se quiere evitar.
 */
class ZenActivity : ComponentActivity() {

    /**
     * Cuantas veces se ha pulsado Inicio con Zen ya delante.
     *
     * Con `launchMode="singleTask"` y `CATEGORY_HOME`, el gesto de Inicio no arranca
     * nada: entrega un intent nuevo a esta Activity, que ya esta viva. Sin atenderlo,
     * quien estaba en Notas o en el lector se quedaba ahi —pulsar Inicio no llevaba a la
     * pantalla de inicio, que es lo primero que se espera de un launcher—.
     *
     * Es un contador y no un booleano porque hay que poder distinguir dos pulsaciones
     * seguidas: con un booleano, la segunda no cambiaria el estado y `LaunchedEffect` no
     * volveria a dispararse.
     */
    private val homePresses = MutableStateFlow(0)

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
                        usage = container.usage,
                        memory = container.memory,
                        homePresses = homePresses,
                        onFirstFrame = ::releaseWindowBackground,
                        onSessionActiveChanged = ::applyLockTask,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Solo el gesto de Inicio, no cualquier intent que llegue a la Activity.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.value++
    }

    /**
     * Suelta el fondo de la ventana en cuanto hay algo pintado.
     *
     * `Theme.Zen` pone un `windowBackground` opaco para que el arranque no ensene un
     * destello claro, y `ZenScreen` pinta **otro** fondo opaco encima: son dos capas a
     * pantalla completa dibujandose en cada fotograma. La primera solo hace falta hasta
     * que existe la segunda, asi que en cuanto Compose entrega el primer fotograma se
     * quita y el sobredibujado desaparece.
     */
    private fun releaseWindowBackground() {
        window.setBackgroundDrawable(null)
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
    usage: UsageRepository,
    memory: LauncherMemory,
    homePresses: StateFlow<Int>,
    onFirstFrame: () -> Unit,
    onSessionActiveChanged: (Boolean) -> LockTaskState,
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val sessionViewModel: SessionViewModel = viewModel(factory = factory)
    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val usageViewModel: UsageViewModel = viewModel(factory = factory)
    val weatherViewModel: WeatherViewModel = viewModel(factory = factory)

    // **Solo si hay sesion**, no el estado completo. El estado completo empieza por un
    // `tickerFlow` de un segundo y arrastra el receptor de bateria, y esto se colecta en
    // todas las pantallas: colectarlo aqui hacia que la pantalla de inicio quieta
    // despertase el hilo principal una vez por segundo, para siempre. Ver
    // [SessionViewModel.active].
    val active = sessionViewModel.active.collectAsStateWithLifecycle().value
    val finished by sessionViewModel.finished.collectAsStateWithLifecycle()
    val confirming by sessionViewModel.confirmingFinish.collectAsStateWithLifecycle()
    val distraction by usageViewModel.distraction.collectAsStateWithLifecycle()

    // Adonde ir cuando el aviso de distraccion se cierre. No se navega desde el propio
    // aviso: mientras esta en pantalla el NavHost no esta compuesto, y en un arranque en
    // frio que aterrizase directamente en el aviso su grafo todavia no existiria.
    // Llamar a `navigate` ahi tira una excepcion, y una excepcion aqui deja el telefono
    // sin pantalla de inicio.
    var routeAfterDistraction by remember { mutableStateOf<String?>(null) }

    // Las tres arrancan en false y las rellena el primer ON_RESUME, que llega
    // inmediatamente despues de la primera composicion. Leerlas aqui costaba tres
    // llamadas bloqueantes al sistema **antes del primer fotograma de la pantalla de
    // inicio**, que es justo lo que el usuario esta esperando. Las tres se leen solo en
    // Ajustes, a varios toques de distancia: para cuando alguien puede verlas ya son
    // ciertas.
    var isDefaultLauncher by remember { mutableStateOf(false) }
    var doubleTapLockEnabled by remember { mutableStateOf(false) }
    var lockTaskState by remember { mutableStateOf(LockTaskState.NONE) }
    var nowPlayingEnabled by remember { mutableStateOf(false) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { isDefaultLauncher = context.holdsHomeRole() }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { doubleTapLockEnabled = screenLocker.canLock() }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Si se deniega, la sesion funciona igual: solo se pierde el aviso. */ }

    // Soltar lo que se leyo para arrancar, con la pantalla de inicio ya dibujada. Antes
    // del primer fotograma solo retrasaria lo que el usuario esta esperando; ver
    // [LauncherMemory]. El fondo de ventana se suelta en el mismo momento y por la misma
    // razon: hasta aqui hacia falta, a partir de aqui solo es una capa opaca de mas.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        memory.releaseAfterFirstFrame()
        onFirstFrame()
    }

    // Pulsar Inicio con Zen ya delante vuelve a la pantalla de inicio. Ver
    // [ZenActivity.homePresses]: sin esto, el gesto de Inicio no hacia nada y quien
    // estaba en Notas o en el lector se quedaba donde estaba.
    val homePress by homePresses.collectAsStateWithLifecycle()
    LaunchedEffect(homePress) {
        // El valor inicial no es una pulsacion: solo se navega a partir de la primera.
        if (homePress == 0) return@LaunchedEffect
        // Con una sesion, un resumen o un aviso en pantalla, el NavHost **no esta
        // compuesto** y su grafo puede no existir todavia: tocarlo ahi lanza excepcion y
        // una excepcion aqui deja el telefono sin pantalla de inicio. Es la misma
        // precaucion que toma la navegacion diferida del aviso de distraccion, unas
        // lineas mas arriba. Ademas no habria nada que hacer: esas tres pantallas ya
        // sustituyen a la home entera.
        if (navController.currentDestination == null) return@LaunchedEffect
        // `popBackStack` y no `navigate`: la home es la raiz del grafo, asi que navegar
        // apilaria una segunda encima de la que ya esta debajo.
        navController.popBackStack(route = ZenRoute.HOME, inclusive = false)
    }

    // La navegacion diferida del aviso: se espera a que el NavHost vuelva a componerse.
    LaunchedEffect(distraction, routeAfterDistraction) {
        val route = routeAfterDistraction ?: return@LaunchedEffect
        if (distraction != null) return@LaunchedEffect
        routeAfterDistraction = null
        navController.navigate(route)
    }

    // Al volver a primer plano hay que cerrar la sesion si venció mientras no se veia,
    // y refrescar si Zen sigue siendo el launcher por defecto.
    //
    // Las tres concesiones se releen **fuera del hilo principal**. Las tres son llamadas
    // bloqueantes al sistema —`RoleManager`, `DevicePolicyManager` y una consulta a
    // `Settings.Secure`, que es un proveedor de contenido— y las tres estaban en el
    // camino critico entre "vuelvo de WhatsApp" y "veo la hora", cincuenta veces al dia.
    // Cambian una vez cada varios meses: pueden llegar un fotograma tarde, y lo que se
    // pinta mientras tanto es el valor anterior, que es el correcto casi siempre.
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.onResumed()
                // El unico momento en que Zen mide el uso del movil: se vuelve de una
                // aplicacion, que es cuando el dato cambio y cuando se puede decir algo
                // sin interrumpir nada. Ver [UsageViewModel].
                usageViewModel.refresh()
                // Y el tiempo, con el mismo trato: al volver a la pantalla de inicio y
                // nunca por su cuenta. Solo sale a la red si toca (ver `WeatherRefresh`)
                // y solo si hay una ciudad elegida.
                weatherViewModel.refresh()
                scope.launch {
                    val home = withContext(Dispatchers.IO) { context.holdsHomeRole() }
                    // El administrador puede haberse revocado desde Ajustes de Android.
                    val canLock = withContext(Dispatchers.IO) { screenLocker.canLock() }
                    // Y el acceso a notificaciones se concede o se quita en esa misma
                    // pantalla del sistema, de la que se vuelve por aqui.
                    val metadata = withContext(Dispatchers.IO) { media.hasMetadataAccess() }
                    isDefaultLauncher = home
                    doubleTapLockEnabled = canLock
                    nowPlayingEnabled = metadata
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // El anclado sigue a la sesion, no al ciclo de vida: entra al empezar y se suelta
    // al terminar, venga el final del usuario o de la alarma.
    LaunchedEffect(active != null) { lockTaskState = onSessionActiveChanged(active != null) }

    when {
        // La sesion activa manda sobre cualquier ruta.
        active != null -> {
            // Durante la sesion, atras no lleva a ninguna parte: no hay a donde volver.
            BackHandler(enabled = true) { /* deliberadamente vacio */ }

            // El cronometro y la bateria se colectan **aqui dentro**, dentro de la rama
            // que solo existe con sesion: asi el latido de un segundo y el receptor de
            // `ACTION_BATTERY_CHANGED` viven exactamente lo que dura la sesion.
            val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()

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

        // El aviso de distraccion va por debajo de la sesion: quien esta en una sesion
        // Zen ya tomo la decision que el aviso pretende provocar, y por encima del
        // NavHost porque sustituye a la pantalla entera, no se pone encima de ella.
        distraction != null -> {
            DistractionScreen(
                state = distraction!!,
                onBreathe = {
                    routeAfterDistraction = ZenRoute.BREATHE
                    usageViewModel.dismissDistraction()
                },
                onStartSession = {
                    routeAfterDistraction = ZenRoute.SESSION_SETUP
                    usageViewModel.dismissDistraction()
                },
                onDismiss = usageViewModel::dismissDistraction,
            )
        }

        finished != null -> {
            BackHandler(enabled = true) { sessionViewModel.consumeSummary() }

            SessionSummaryScreen(
                session = finished!!,
                onBack = {
                    sessionViewModel.consumeSummary()
                    navController.popBackStack(
                        route = ZenRoute.HOME,
                        inclusive = false,
                    )
                },
            )
        }

        else -> ZenNavHost(
            navController = navController,
            factory = factory,
            sessionViewModel = sessionViewModel,
            homeViewModel = homeViewModel,
            usageViewModel = usageViewModel,
            weatherViewModel = weatherViewModel,
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
            // Mismo trato que el acceso a notificaciones: se concede y se revoca en la
            // pantalla del sistema, y al volver de ella `onResume` vuelve a preguntar.
            onGrantUsageAccess = { context.safeStartActivity(usage.accessIntent()) },
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
