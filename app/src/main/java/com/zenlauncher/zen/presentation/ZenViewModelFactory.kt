package com.zenlauncher.zen.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zenlauncher.zen.ZenContainer
import com.zenlauncher.zen.presentation.apps.AppDrawerViewModel
import com.zenlauncher.zen.presentation.apps.HomeAppsViewModel
import com.zenlauncher.zen.presentation.apps.RestrictedAppsViewModel
import com.zenlauncher.zen.presentation.home.HomeViewModel
import com.zenlauncher.zen.presentation.notes.NoteDetailViewModel
import com.zenlauncher.zen.presentation.notes.NotesViewModel
import com.zenlauncher.zen.presentation.notes.QuickNoteViewModel
import com.zenlauncher.zen.presentation.notifications.NotificationsViewModel
import com.zenlauncher.zen.presentation.session.SessionViewModel
import com.zenlauncher.zen.presentation.settings.SettingsViewModel
import com.zenlauncher.zen.presentation.stats.StatsViewModel

/**
 * Une el contenedor manual con los ViewModel. Al declararse en un solo sitio, cambiar
 * una implementacion de la capa de datos no obliga a tocar ninguna pantalla.
 */
fun zenViewModelFactory(container: ZenContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        HomeViewModel(
            preferences = container.preferences,
            installedApps = container.installedApps,
            restrictions = container.restrictions,
            sessionManager = container.sessionManager,
            media = container.mediaTransport,
            notifications = container.postedNotifications,
            seedFavourites = container.seedEssentialFavourites,
            clock = container.clock,
        )
    }
    initializer {
        AppDrawerViewModel(
            preferences = container.preferences,
            installedApps = container.installedApps,
            restrictions = container.restrictions,
        )
    }
    initializer {
        HomeAppsViewModel(
            preferences = container.preferences,
            installedApps = container.installedApps,
            restrictions = container.restrictions,
        )
    }
    initializer {
        RestrictedAppsViewModel(
            preferences = container.preferences,
            installedApps = container.installedApps,
            restrictions = container.restrictions,
        )
    }
    initializer {
        SessionViewModel(
            sessionManager = container.sessionManager,
            preferences = container.preferences,
            sessions = container.sessions,
            battery = container.battery,
            batterySaver = container.batterySaver,
            clock = container.clock,
        )
    }
    initializer {
        NotificationsViewModel(
            notifications = container.postedNotifications,
            installedApps = container.installedApps,
            restrictions = container.restrictions,
        )
    }
    initializer {
        NotesViewModel(
            notes = container.notes,
            indexer = container.noteIndexer,
            appScope = container.appScope,
        )
    }
    initializer {
        QuickNoteViewModel(
            notes = container.notes,
            attachments = container.noteAttachments,
            dictation = container.dictation,
            clock = container.clock,
            appScope = container.appScope,
        )
    }
    initializer {
        NoteDetailViewModel(
            notes = container.notes,
            attachments = container.noteAttachments,
            clock = container.clock,
            appScope = container.appScope,
        )
    }
    initializer {
        StatsViewModel(sessions = container.sessions)
    }
    initializer {
        SettingsViewModel(
            preferences = container.preferences,
            installedApps = container.installedApps,
            restrictions = container.restrictions,
            batterySaver = container.batterySaver,
        )
    }
}
