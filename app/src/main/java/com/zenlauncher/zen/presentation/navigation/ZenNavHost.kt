package com.zenlauncher.zen.presentation.navigation

import android.Manifest
import android.content.Intent
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
import com.zenlauncher.zen.presentation.news.NewsScreen
import com.zenlauncher.zen.presentation.news.NewsViewModel
import com.zenlauncher.zen.presentation.notes.DevelopIdeaScreen
import com.zenlauncher.zen.presentation.notes.DevelopIdeaViewModel
import com.zenlauncher.zen.presentation.notes.NoteDetailScreen
import com.zenlauncher.zen.presentation.notes.NoteDetailViewModel
import com.zenlauncher.zen.presentation.notes.ProjectDetailScreen
import com.zenlauncher.zen.presentation.notes.ProjectDetailViewModel
import com.zenlauncher.zen.presentation.notes.ProjectsScreen
import com.zenlauncher.zen.presentation.notes.ProjectsViewModel
import com.zenlauncher.zen.presentation.notes.NotesScreen
import com.zenlauncher.zen.presentation.notes.NotesViewModel
import com.zenlauncher.zen.presentation.notes.QuickNoteScreen
import com.zenlauncher.zen.presentation.notes.QuickNoteViewModel
import com.zenlauncher.zen.presentation.notifications.NotificationsScreen
import com.zenlauncher.zen.presentation.notifications.NotificationsViewModel
import com.zenlauncher.zen.presentation.reading.LibraryScreen
import com.zenlauncher.zen.presentation.reading.LibraryViewModel
import com.zenlauncher.zen.presentation.reading.ReaderScreen
import com.zenlauncher.zen.presentation.reading.ReaderViewModel
import com.zenlauncher.zen.presentation.scanner.ScannerRoute
import com.zenlauncher.zen.presentation.scanner.ScannerViewModel
import com.zenlauncher.zen.presentation.session.SessionSetupScreen
import com.zenlauncher.zen.presentation.session.SessionViewModel
import com.zenlauncher.zen.presentation.settings.SettingsScreen
import com.zenlauncher.zen.presentation.settings.SettingsViewModel
import com.zenlauncher.zen.presentation.stats.StatsScreen
import com.zenlauncher.zen.presentation.stats.StatsViewModel
import com.zenlauncher.zen.presentation.usage.UsageScreen
import com.zenlauncher.zen.presentation.usage.UsageWeekScreen
import com.zenlauncher.zen.presentation.usage.UsageViewModel
import com.zenlauncher.zen.presentation.weather.WeatherScreen
import com.zenlauncher.zen.presentation.weather.WeatherViewModel
import com.zenlauncher.zen.presentation.theme.ZenMotion

@Composable
fun ZenNavHost(
    navController: NavHostController,
    factory: ViewModelProvider.Factory,
    sessionViewModel: SessionViewModel,
    // Los dos ViewModel de ambito de Activity se reciben, no se resuelven aqui dentro:
    // `viewModel()` dentro de un `composable` se engancha a su entrada de la pila de
    // navegacion, asi que resolverlos otra vez crearia una segunda instancia con su
    // propia tuberia de flujos al lado de la que ya vive en `ZenRoot`.
    homeViewModel: HomeViewModel,
    usageViewModel: UsageViewModel,
    weatherViewModel: WeatherViewModel,
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
    onGrantUsageAccess: () -> Unit,
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
            val state by homeViewModel.state.collectAsStateWithLifecycle()
            val usage by usageViewModel.state.collectAsStateWithLifecycle()
            val weather by weatherViewModel.state.collectAsStateWithLifecycle()

            // La pantalla de inicio es el final del camino: aqui "atras" no lleva a
            // ninguna parte, y dejarlo activo cerraria el launcher. Con la barra de
            // gestos oculta, esto ademas deja el gesto sin efecto en vez de invisible.
            BackHandler(enabled = true) { /* deliberadamente vacio */ }

            HomeScreen(
                state = state,
                usageReading = usage.reading,
                onLaunchApp = homeViewModel::launch,
                onMoveApp = homeViewModel::moveHomeApp,
                onOpenDrawer = { navController.navigate(ZenRoute.DRAWER) },
                onOpenHomeApps = { navController.navigate(ZenRoute.HOME_APPS) },
                onOpenNotes = { navController.navigate(ZenRoute.NOTES) },
                onOpenReading = { navController.navigate(ZenRoute.READING) },
                onOpenScanner = { navController.navigate(ZenRoute.SCANNER) },
                onStartSession = { navController.navigate(ZenRoute.SESSION_SETUP) },
                onBreathe = { navController.navigate(ZenRoute.BREATHE) },
                onOpenNews = { navController.navigate(ZenRoute.NEWS) },
                onOpenRestricted = { navController.navigate(ZenRoute.RESTRICTED) },
                onOpenStats = { navController.navigate(ZenRoute.STATS) },
                onOpenUsage = { navController.navigate(ZenRoute.USAGE) },
                onOpenSettings = { navController.navigate(ZenRoute.SETTINGS) },
                onOpenNotifications = { packageName ->
                    navController.navigate(ZenRoute.notifications(packageName))
                },
                onExitZen = onExitZen,
                onPreviousTrack = homeViewModel::previousTrack,
                onTogglePlayback = homeViewModel::togglePlayback,
                onNextTrack = homeViewModel::nextTrack,
                onOpenPlayer = homeViewModel::openNowPlaying,
                onOpenWeather = { navController.navigate(ZenRoute.WEATHER) },
                weather = weather.reading,
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
                onDevelopIdea = { navController.navigate(ZenRoute.develop()) },
                onOpenNote = { navController.navigate(ZenRoute.note(it.id)) },
                onAcceptClusterSuggestion = notesViewModel::acceptClusterSuggestion,
                onIgnoreClusterSuggestion = notesViewModel::ignoreClusterSuggestion,
                onOpenProjects = { navController.navigate(ZenRoute.PROJECTS) },
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
                onDevelop = { navController.navigate(ZenRoute.develop(noteId)) },
                onAssignProject = detailViewModel::assignToProject,
                onCreateProject = detailViewModel::createProjectAndAssign,
                onDelete = {
                    detailViewModel.delete()
                    navController.popBackStack()
                },
                onBack = navController::popBackStack,
            )
        }

        composable(
            route = ZenRoute.DEVELOP_ROUTE,
            arguments = listOf(
                navArgument(ZenRoute.DEVELOP_NOTE_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val developViewModel: DevelopIdeaViewModel = viewModel(factory = factory)
            val state by developViewModel.state.collectAsStateWithLifecycle()
            val ideaText by developViewModel.ideaText.collectAsStateWithLifecycle()
            val developNoteId = entry.arguments?.getString(ZenRoute.DEVELOP_NOTE_ARG)

            LaunchedEffect(developNoteId) { developViewModel.open(developNoteId) }

            val context = LocalContext.current
            val microphone = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    developViewModel.toggleDictation()
                } else {
                    developViewModel.onMicrophoneDenied()
                }
            }

            DevelopIdeaScreen(
                state = state,
                ideaText = ideaText,
                nowMillis = System.currentTimeMillis(),
                onIdeaChange = developViewModel::onIdeaChange,
                onDictate = {
                    val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted || state.listening) {
                        developViewModel.toggleDictation()
                    } else {
                        microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onOpenNote = { navController.navigate(ZenRoute.note(it)) },
                onSave = developViewModel::saveAsNote,
                onConvertToProject = developViewModel::convertToProject,
                onBack = navController::popBackStack,
                onSaved = navController::popBackStack,
            )
        }

        composable(ZenRoute.READING) {
            val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
            val state by libraryViewModel.state.collectAsStateWithLifecycle()

            val context = LocalContext.current

            // Selector de documentos del sistema: **no pide ningun permiso**. Devuelve
            // solo el fichero elegido, asi que Zen nunca ve el almacenamiento entero y
            // no hay nada que conceder ni que explicar. Mismo planteamiento que el
            // selector de fotos de Notas.
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                // El permiso persistente deja poder volver a abrir el fichero despues de
                // reiniciar el telefono. No es imprescindible —el texto se guarda entero
                // al importar y el libro se lee aunque el PDF desaparezca— pero sin el, la
                // portada no se podria volver a generar. Si el proveedor no lo ofrece, se
                // importa igual: de ahi el `runCatching`.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                libraryViewModel.import(uri.toString())
            }

            LibraryScreen(
                state = state,
                onAddBook = { picker.launch(arrayOf(PDF_MIME_TYPE)) },
                onOpenBook = { navController.navigate(ZenRoute.book(it.id)) },
                onDismissImport = libraryViewModel::acknowledgeImport,
                coverPath = libraryViewModel::coverPath,
                onBack = navController::popBackStack,
            )
        }

        composable(
            route = ZenRoute.BOOK_ROUTE,
            arguments = listOf(navArgument(ZenRoute.BOOK_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val readerViewModel: ReaderViewModel = viewModel(factory = factory)
            val state by readerViewModel.state.collectAsStateWithLifecycle()
            val query by readerViewModel.query.collectAsStateWithLifecycle()
            val bookId = entry.arguments?.getString(ZenRoute.BOOK_ID_ARG)

            LaunchedEffect(bookId) { bookId?.let(readerViewModel::open) }

            ReaderScreen(
                state = state,
                query = query,
                onQueryChange = readerViewModel::onQueryChange,
                onPositionVisible = readerViewModel::onPositionVisible,
                onTextStep = readerViewModel::setTextStep,
                onLeadingStep = readerViewModel::setLeadingStep,
                onMarginStep = readerViewModel::setMarginStep,
                onToggleSerif = readerViewModel::toggleSerif,
                onAddBookmark = readerViewModel::addBookmark,
                onDeleteBookmark = readerViewModel::deleteBookmark,
                onHighlight = { block, start, end, text, page, note ->
                    readerViewModel.putHighlight(block, start, end, text, page, note)
                },
                onSetNote = readerViewModel::setNote,
                onDeleteHighlight = readerViewModel::deleteHighlight,
                onDelete = readerViewModel::delete,
                onBack = navController::popBackStack,
            )
        }

        composable(ZenRoute.SCANNER) {
            // El ViewModel cuelga de esta entrada de la pila de navegacion, asi que al
            // salir se limpia solo: con el se van el detector, el reconocedor de texto, el
            // modelo de OCR y la memoria nativa de los Mat. Es deliberado que no viva en el
            // ambito de la Activity como el de la home.
            //
            // Lo que **no** se recupera es la biblioteca nativa en si: una vez cargada se
            // queda mapeada hasta que muera el proceso, porque Java no puede descargarla.
            // Comprobado en el dispositivo. Lo que se acota aqui es lo que crece —los Mat
            // y el modelo—, no los 15 MB de codigo mapeado.
            val scannerViewModel: ScannerViewModel = viewModel(factory = factory)

            ScannerRoute(
                viewModel = scannerViewModel,
                onLeave = navController::popBackStack,
            )
        }

        composable(ZenRoute.PROJECTS) {
            val projectsViewModel: ProjectsViewModel = viewModel(factory = factory)
            val state by projectsViewModel.state.collectAsStateWithLifecycle()

            ProjectsScreen(
                state = state,
                onOpenProject = { navController.navigate(ZenRoute.project(it)) },
                onBack = navController::popBackStack,
            )
        }

        composable(
            route = ZenRoute.PROJECT_ROUTE,
            arguments = listOf(navArgument(ZenRoute.PROJECT_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            val projectDetailViewModel: ProjectDetailViewModel = viewModel(factory = factory)
            val state by projectDetailViewModel.state.collectAsStateWithLifecycle()
            val projectId = entry.arguments?.getString(ZenRoute.PROJECT_ID_ARG)

            LaunchedEffect(projectId) { projectId?.let(projectDetailViewModel::open) }

            ProjectDetailScreen(
                state = state,
                nowMillis = System.currentTimeMillis(),
                onOpenNote = { navController.navigate(ZenRoute.note(it)) },
                onMarkDone = projectDetailViewModel::markDone,
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

        composable(ZenRoute.NEWS) {
            val newsViewModel: NewsViewModel = viewModel(factory = factory)
            val state by newsViewModel.state.collectAsStateWithLifecycle()

            // La descarga se dispara **al abrir la pantalla**, no en el arranque de Zen
            // ni al volver a la home: es la unica funcion de la aplicacion que baja algo
            // de internet a peticion, y solo debe hacerlo cuando alguien viene a leerlo.
            // Volver a entrar el mismo dia no abre ninguna conexion, asi que este efecto
            // puede correr las veces que haga falta (ver `NewsRefresh`).
            LaunchedEffect(Unit) { newsViewModel.load() }

            NewsScreen(
                state = state,
                onOpenLink = onOpenLink,
                onRefresh = newsViewModel::refreshNow,
                onBack = navController::popBackStack,
            )
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

        composable(ZenRoute.USAGE) {
            // El mismo ViewModel que alimenta el pulso de la home y el aviso: la
            // pantalla de detalle no vuelve a medir nada por su cuenta.
            val usage by usageViewModel.state.collectAsStateWithLifecycle()
            val weather by weatherViewModel.state.collectAsStateWithLifecycle()

            UsageScreen(
                state = usage,
                onBack = navController::popBackStack,
                onGrantAccess = onGrantUsageAccess,
                onOpenWeek = { navController.navigate(ZenRoute.USAGE_WEEK) },
            )
        }

        composable(ZenRoute.USAGE_WEEK) {
            val week by usageViewModel.week.collectAsStateWithLifecycle()

            // Los siete dias se leen al entrar, no antes: es el unico sitio de Zen que
            // hace siete consultas seguidas, y solo las hace quien viene a mirarlas.
            LaunchedEffect(Unit) { usageViewModel.loadWeek() }

            UsageWeekScreen(
                state = week,
                onBack = navController::popBackStack,
                onOpenRestricted = { navController.navigate(ZenRoute.RESTRICTED) },
                onGrantAccess = onGrantUsageAccess,
            )
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
                onOpenWeather = { navController.navigate(ZenRoute.WEATHER) },
                onBack = navController::popBackStack,
            )
        }

        composable(ZenRoute.WEATHER) {
            // El mismo ViewModel que alimenta la franja de la home, no uno nuevo: dos
            // instancias serian dos tuberias pidiendo lo mismo a la red.
            val state by weatherViewModel.state.collectAsStateWithLifecycle()
            val search by weatherViewModel.search.collectAsStateWithLifecycle()
            val query by weatherViewModel.query.collectAsStateWithLifecycle()

            WeatherScreen(
                state = state,
                search = search,
                query = query,
                onQueryChange = weatherViewModel::onQueryChange,
                onSearch = weatherViewModel::searchPlaces,
                onChoose = weatherViewModel::choose,
                onClearPlace = weatherViewModel::clearPlace,
                onRefresh = weatherViewModel::refreshNow,
                onBack = navController::popBackStack,
            )
        }
    }
}

/** Lo unico que acepta el selector: Lectura solo sabe leer PDF. */
private const val PDF_MIME_TYPE = "application/pdf"
