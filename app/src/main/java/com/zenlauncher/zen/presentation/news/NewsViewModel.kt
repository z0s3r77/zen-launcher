package com.zenlauncher.zen.presentation.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsRefresh
import com.zenlauncher.zen.domain.news.NewsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewsUiState(
    /** Lo ultimo que se bajo, sea de hoy o de otro dia. Null: nunca se bajo nada. */
    val edition: NewsEdition? = null,
    /** Si lo de arriba es de hoy. Cuando no lo es, la pantalla lo dice. */
    val fromToday: Boolean = false,
    val downloading: Boolean = false,
    /** Se intento bajar y no se pudo: sin red, sitio caido o portada ilegible. */
    val failed: Boolean = false,
)

/**
 * Las noticias del dia: bajarlas una vez, guardarlas y ensenarlas.
 *
 * **Vive en su pantalla y solo en su pantalla**, al reves que el tiempo. El tiempo tiene
 * ambito de Activity porque su dato sale tambien en la franja de la home; aqui no hay
 * nada en la home que ensenar —un titular en la pantalla de inicio seria justo lo que
 * Zen evita: algo que invita a leer cada vez que miras la hora—, asi que la tuberia solo
 * existe mientras la pantalla existe.
 *
 * **La red se toca en un unico caso**: al entrar, y solo si lo guardado no es de hoy.
 * Volver a entrar diez veces el mismo dia no abre ni una conexion. Ver [NewsRefresh].
 */
class NewsViewModel(
    private val news: NewsRepository,
    private val preferences: PreferencesRepository,
    private val clock: ZenClock,
) : ViewModel() {

    private val downloading = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)

    val state: StateFlow<NewsUiState> = combine(
        preferences.lastNews,
        downloading,
        failed,
    ) { edition, downloading, failed ->
        NewsUiState(
            edition = edition,
            fromToday = edition != null &&
                NewsRefresh.isFromToday(edition.fetchedAtMillis, clock.wallTimeMillis()),
            downloading = downloading,
            failed = failed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = NewsUiState(),
    )

    /**
     * Se llama al abrir la pantalla, y solo baja de verdad si toca.
     *
     * Aqui no hay marca de "ultimo intento" como en el tiempo, y es a proposito: la
     * marca del tiempo existe porque el tiempo se pide **solo**, al volver a la home, y
     * sin ella un telefono sin cobertura saldria a la red en cada vuelta. Esto no se
     * pide solo nunca: hace falta que alguien toque NOTICIAS. Si el intento falla y el
     * usuario vuelve a entrar, es que quiere reintentarlo.
     */
    fun load() {
        viewModelScope.launch {
            val stored = preferences.lastNews.first()
            if (!NewsRefresh.shouldDownload(stored, clock.wallTimeMillis())) return@launch
            download()
        }
    }

    /** Bajar ahora, sin mirar el dia: lo pidio el usuario a mano. */
    fun refreshNow() {
        viewModelScope.launch { download() }
    }

    private suspend fun download() {
        // Dos descargas a la vez serian dos peticiones para escribir lo mismo: pasa al
        // tocar ACTUALIZAR mientras la de la entrada sigue en marcha.
        if (downloading.value) return
        downloading.value = true
        failed.value = false
        val edition = news.frontPage()
        // Un fallo **no borra** lo que hubiera: la portada de ayer, dicha como de ayer,
        // es mejor que una pantalla en blanco.
        if (edition == null) failed.value = true else preferences.setLastNews(edition)
        downloading.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
