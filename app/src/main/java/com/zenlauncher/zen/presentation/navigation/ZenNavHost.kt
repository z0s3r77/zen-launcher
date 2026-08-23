package com.zenlauncher.zen.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.zenlauncher.zen.presentation.apps.RestrictedAppsScreen
import com.zenlauncher.zen.presentation.apps.RestrictedAppsViewModel
import com.zenlauncher.zen.presentation.home.HomeScreen
import com.zenlauncher.zen.presentation.home.HomeViewModel
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
                onStartSession = { navController.navigate(ZenRoute.SESSION_SETUP) },
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

        composable(ZenRoute.SESSION_SETUP) {
            val state by sessionViewModel.state.collectAsStateWithLifecycle()

            SessionSetupScreen(
                state = state,
                onStart = onStartSession,
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

        composable(ZenRoute.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
            val state by settingsViewModel.state.collectAsStateWithLifecycle()

            SettingsScreen(
                state = state,
                isDefaultLauncher = isDefaultLauncher,
                doubleTapLockEnabled = doubleTapLockEnabled,
                nowPlayingEnabled = nowPlayingEnabled,
                onQueryChange = settingsViewModel::onQueryChange,
                onToggleFavourite = settingsViewModel::toggleFavourite,
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
