package com.zenlauncher.zen.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zenlauncher.zen.ZenContainer
import com.zenlauncher.zen.presentation.apps.AppDrawerViewModel
import com.zenlauncher.zen.presentation.apps.HomeAppsViewModel
import com.zenlauncher.zen.presentation.apps.RestrictedAppsViewModel
import com.zenlauncher.zen.presentation.home.HomeViewModel
import com.zenlauncher.zen.presentation.news.NewsViewModel
import com.zenlauncher.zen.presentation.notes.DevelopIdeaViewModel
import com.zenlauncher.zen.presentation.notes.NoteDetailViewModel
import com.zenlauncher.zen.presentation.notes.NotesViewModel
import com.zenlauncher.zen.presentation.notes.ProjectDetailViewModel
import com.zenlauncher.zen.presentation.notes.ProjectsViewModel
import com.zenlauncher.zen.presentation.notes.QuickNoteViewModel
import com.zenlauncher.zen.presentation.notifications.NotificationsViewModel
import com.zenlauncher.zen.presentation.reading.LibraryViewModel
import com.zenlauncher.zen.presentation.reading.ReaderViewModel
import com.zenlauncher.zen.presentation.session.SessionViewModel
import com.zenlauncher.zen.presentation.settings.SettingsViewModel
import com.zenlauncher.zen.presentation.stats.StatsViewModel
import com.zenlauncher.zen.presentation.usage.UsageViewModel
import com.zenlauncher.zen.presentation.weather.WeatherViewModel

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
            clock = container.clock,
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
        DevelopIdeaViewModel(
            notes = container.notes,
            indexer = container.noteIndexer,
            ideaDevelopment = container.ideaDevelopment,
            dictation = container.dictation,
            clock = container.clock,
            appScope = container.appScope,
        )
    }
    initializer {
        ProjectsViewModel(notes = container.notes)
    }
    initializer {
        ProjectDetailViewModel(notes = container.notes, appScope = container.appScope)
    }
    initializer {
        NewsViewModel(
            news = container.news,
            preferences = container.preferences,
            clock = container.clock,
        )
    }
    initializer {
        LibraryViewModel(
            books = container.books,
            importer = container.bookImporter,
            covers = container.bookCovers,
            appScope = container.appScope,
        )
    }
    initializer {
        ReaderViewModel(
            books = container.books,
            covers = container.bookCovers,
            preferences = container.preferences,
            clock = container.clock,
            appScope = container.appScope,
        )
    }
    initializer {
        StatsViewModel(sessions = container.sessions)
    }
    initializer {
        UsageViewModel(
            usage = container.usage,
            installedApps = container.installedApps,
            preferences = container.preferences,
            clock = container.clock,
        )
    }
    initializer {
        WeatherViewModel(
            weather = container.weather,
            preferences = container.preferences,
            clock = container.clock,
        )
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
