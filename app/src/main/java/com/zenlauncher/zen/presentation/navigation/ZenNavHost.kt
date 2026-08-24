package com.zenlauncher.zen.presentation.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.presentation.apps.AppDrawerScreen
import com.zenlauncher.zen.presentation.apps.AppDrawerViewModel
import com.zenlauncher.zen.presentation.apps.HomeAppsScreen
import com.zenlauncher.zen.presentation.apps.HomeAppsViewModel
import com.zenlauncher.zen.presentation.apps.RestrictedAppsScreen
import com.zenlauncher.zen.presentation.apps.RestrictedAppsViewModel
import com.zenlauncher.zen.presentation.breathe.BreatheScreen
import com.zenlauncher.zen.presentation.home.HomeScreen
import com.zenlauncher.zen.presentation.home.HomeViewModel
import com.zenlauncher.zen.presentation.notes.NoteDetailScreen
import com.zenlauncher.zen.presentation.notes.NoteDetailViewModel
import com.zenlauncher.zen.presentation.notes.NotesScreen
import com.zenlauncher.zen.presentation.notes.NotesViewModel
import com.zenlauncher.zen.presentation.notes.QuickNoteScreen
import com.zenlauncher.zen.presentation.notes.QuickNoteViewModel
import com.zenlauncher.zen.presentation.notifications.NotificationsScreen
import com.zenlauncher.zen.presentation.notifications.NotificationsViewModel
import com.zenlauncher.zen.presentation.session.SessionSetupScreen
import com.zenlauncher.zen.presentation.session.SessionViewModel
import com.zenlauncher.zen.presentation.settings.SettingsScreen
import com.zenlauncher.zen.presentation.settings.SettingsViewModel
import com.zenlauncher.zen.presentation.stats.StatsScreen
import com.zenlauncher.zen.presentation.stats.StatsViewModel
import com.zenlauncher.zen.presentation.theme.ZenMotion

@Composable
fun ZenNavHost(
    navController: NavHostController,
    factory: ViewModelProvider.Factory,
    sessionViewModel: SessionViewModel,
    isDefaultLauncher: Boolean,
    doubleTapLockEnabled: Boolean,
    nowPlayingEnabled: Boolean,
    onRequestHomeRole: () -> Unit,
    onToggleDoubleTapLock: () -> Unit,
    onToggleNowPlaying: () -> Unit,
    onOpenBatterySaver: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onExitZen: () -> Unit,
    onOpenLink: (String) -> Unit,
    onStartSession: (ZenDuration) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = ZenRoute.HOME,
        // Una pantalla que aparece de golpe obliga a releerla entera; una que entra
        // desde su lado ya dijo de donde viene antes de terminar de llegar. Se declaran
        // aqui y no en cada destino para que ninguna pantalla nueva se quede fuera.
        enterTransition = { ZenMotion.ScreenEnter },
        exitTransition = { ZenMotion.ScreenExit },
        popEnterTransition = { ZenMotion.ScreenPopEnter },
        popExitTransition = { ZenMotion.ScreenPopExit },
    ) {

        composable(ZenRoute.HOME) {
            val homeViewModel: HomeViewModel = viewModel(factory = factory)
            val state by homeViewModel.state.collectAsStateWithLifecycle()

            // La pantalla de inicio es el final del camino: aqui "atras" no lleva a
            // ninguna parte, y dejarlo activo cerraria el launcher. Con la barra de
            // gestos oculta, esto ademas deja el gesto sin efecto en vez de invisible.
            BackHandler(enabled = true) { /* deliberadamente vacio */ }

            HomeScreen(
                state = state,
                onLaunchApp = homeViewModel::launch,
                onOpenDrawer = { navController.navigate(ZenRoute.DRAWER) },
                onOpenHomeApps = { navController.navigate(ZenRoute.HOME_APPS) },
                onOpenNotes = { navController.navigate(ZenRoute.NOTES) },
                onStartSession = { navController.navigate(ZenRoute.SESSION_SETUP) },
                onBreathe = { navController.navigate(ZenRoute.BREATHE) },
                onOpenRestricted = { navController.navigate(ZenRoute.RESTRICTED) },
                onOpenStats = { navController.navigate(ZenRoute.STATS) },
                onOpenSettings = { navController.navigate(ZenRoute.SETTINGS) },
                onOpenNotifications = { packageName ->
                    navController.navigate(ZenRoute.notifications(packageName))
                },
                onExitZen = onExitZen,
                onPreviousTrack = homeViewModel::previousTrack,
                onTogglePlayback = homeViewModel::togglePlayback,
                onNextTrack = homeViewModel::nextTrack,
                onOpenPlayer = homeViewModel::openNowPlaying,
            )
        }

        composable(ZenRoute.DRAWER) {
            val drawerViewModel: AppDrawerViewModel = viewModel(factory = factory)
            val state by drawerViewModel.state.collectAsStateWithLifecycle()

            AppDrawerScreen(
                state = state,
                onQueryChange = drawerViewModel::onQueryChange,
                onLaunchApp = drawerViewModel::launch,
                onBack = navController::popBackStack,
            )
        }

        composable(
            route = ZenRoute.NOTIFICATIONS_ROUTE,
            arguments = listOf(
                navArgument(ZenRoute.NOTIFICATIONS_PACKAGE_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val notificationsViewModel: NotificationsViewModel = viewModel(factory = factory)
            val state by notificationsViewModel.state.collectAsStateWithLifecycle()

            NotificationsScreen(
                state = state,
                focusPackage = entry.arguments?.getString(ZenRoute.NOTIFICATIONS_PACKAGE_ARG),
                onOpenApp = notificationsViewModel::open,
                onGrantAccess = onGrantNotificationAccess,
                onBack = navController::popBackStack,
            )
        }

        composable(ZenRoute.NOTES) {
            val notesViewModel: NotesViewModel = viewModel(factory = factory)
            val state by notesViewModel.state.collectAsStateWithLifecycle()

            val query by notesViewModel.query.collectAsStateWithLifecycle()

            NotesScreen(
                state = state,
                query = query,
                nowMillis = System.currentTimeMillis(),
                onQueryChange = notesViewModel::onQueryChange,
                onQuickNote = { navController.navigate(ZenRoute.NOTES_QUICK) },
                onOpenNote = { navController.navigate(ZenRoute.note(it.id)) },
                onBack = navController::popBackStack,
            )
        }

        composable(ZenRoute.NOTES_QUICK) {
            val quickNoteViewModel: QuickNoteViewModel = viewModel(factory = factory)
            val state by quickNoteViewModel.state.collectAsStateWithLifecycle()

            val context = LocalContext.current

            // El microfono se pide **al tocar Dictar**, no al abrir la pantalla ni al
            // instalar: quien solo escribe no ve nunca el dialogo. Denegarlo no abre
            // ningun aviso; se apunta como estado y la fila lo dice en texto.
            val microphone = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    quickNoteViewModel.toggleDictation()
                } else {
                    quickNoteViewModel.onMicrophoneDenied()
                }
            }

            // Selector de fotos del sistema: NO pide ningun permiso. Devuelve solo la
            // imagen elegida, asi que Zen nunca ve el carrete entero y no hay nada que
            // conceder ni que explicar.
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia(),
            ) { uri -> uri?.let { quickNoteViewModel.addImage(it.toString()) } }

            QuickNoteScreen(
                state = state,
                onTextChange = quickNoteViewModel::onTextChange,
                onDictate = {
                    val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    // Parar no necesita permiso: si ya esta escuchando, es que lo hay.
                    if (granted || state.listening) {
                        quickNoteViewModel.toggleDictation()
                    } else {
                        microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onPickImage = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onSave = quickNoteViewModel::save,
                // Salir sin guardar se lleva las imagenes ya copiadas: sin esto, cada
                // captura abandonada dejaria una carpeta que no aparece en ninguna nota.
                onBack = {
                    quickNoteViewModel.discard()
                    navController.popBackStack()
                },
                // Guardar devuelve a la pantalla de inicio de un salto, sin pasar por
                // Notas: capturar, guardar y volver al mundo. Quedarse en la lista
                // seria invitar a quedarse leyendo lo que ya se escribio.
                onSaved = {
                    navController.popBackStack(ZenRoute.HOME, inclusive = false)
                },
            )
        }

        composable(
            route = ZenRoute.NOTE_ROUTE,
            arguments = listOf(navArgument(ZenRoute.NOTE_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val detailViewModel: NoteDetailViewModel = viewModel(factory = factory)
            val state by detailViewModel.state.collectAsStateWithLifecycle()
            val noteId = entry.arguments?.getString(ZenRoute.NOTE_ID_ARG)

            LaunchedEffect(noteId) { noteId?.let(detailViewModel::open) }

            NoteDetailScreen(
                state = state,
                nowMillis = System.currentTimeMillis(),
                onOpenLink = onOpenLink,
                onOpenNote = { navController.navigate(ZenRoute.note(it)) },
                onAccept = detailViewModel::accept,
                onIgnore = detailViewModel::ignore,
                onDelete = {
                    detailViewModel.delete()
                    navController.popBackStack()
                },
                onBack = navController::popBackStack,
            )
        }

        composable(ZenRoute.SESSION_SETUP) {
            val state by sessionViewModel.state.collectAsStateWithLifecycle()

            SessionSetupScreen(
                state = state,
                onStart = onStartSession,
                onBack = navController::popBackStack,
            )
        }

        composable(ZenRoute.BREATHE) {
            // Sin ViewModel: el ejercicio no guarda nada ni consulta nada. Lo unico que
            // hay es el tiempo transcurrido, y vive lo que dura la pantalla a proposito
            // —salir a mitad de un minuto de respiracion es dejarlo, no pausarlo.
            BreatheScreen(onBack = navController::popBackStack)
        }

        composable(ZenRoute.RESTRICTED) {
            val restrictedViewModel: RestrictedAppsViewModel = viewModel(factory = factory)
            val state by restrictedViewModel.state.collectAsStateWithLifecycle()

            RestrictedAppsScreen(
                state = state,
                onQueryChange = restrictedViewModel::onQueryChange,
                onToggle = restrictedViewModel::toggle,
                onBack = navController::popBackStack,
            )
        }

        composable(ZenRoute.STATS) {
            val statsViewModel: StatsViewModel = viewModel(factory = factory)
            val stats by statsViewModel.state.collectAsStateWithLifecycle()

            StatsScreen(stats = stats, onBack = navController::popBackStack)
        }

        composable(ZenRoute.HOME_APPS) {
            val homeAppsViewModel: HomeAppsViewModel = viewModel(factory = factory)
            val state by homeAppsViewModel.state.collectAsStateWithLifecycle()

            HomeAppsScreen(
                state = state,
                onQueryChange = homeAppsViewModel::onQueryChange,
                onAdd = homeAppsViewModel::add,
                onRemove = homeAppsViewModel::remove,
                onBack = navController::popBackStack,
            )
        }

        composable(ZenRoute.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            val state by settingsViewModel.state.collectAsStateWithLifecycle()

            SettingsScreen(
                state = state,
                isDefaultLauncher = isDefaultLauncher,
                doubleTapLockEnabled = doubleTapLockEnabled,
                nowPlayingEnabled = nowPlayingEnabled,
                onOpenHomeApps = { navController.navigate(ZenRoute.HOME_APPS) },
                onSetDuration = settingsViewModel::setPreferredDuration,
                onRequestHomeRole = onRequestHomeRole,
                onToggleDoubleTapLock = onToggleDoubleTapLock,
                onToggleNowPlaying = onToggleNowPlaying,
                onOpenBatterySaver = onOpenBatterySaver,
                onOpenAccessibility = onOpenAccessibility,
                onBack = navController::popBackStack,
            )
        }
    }
}
