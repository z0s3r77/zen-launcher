package com.zenlauncher.zen.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.notes.Dictation
import com.zenlauncher.zen.domain.notes.DictationEvent
import com.zenlauncher.zen.domain.notes.IdeaDevelopmentModel
import com.zenlauncher.zen.domain.notes.IdeaPrompts
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteIndexer
import com.zenlauncher.zen.domain.notes.NoteStage
import com.zenlauncher.zen.domain.notes.NotesRepository
import com.zenlauncher.zen.domain.notes.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Menos de tres notas relacionadas no es un patron, es una coincidencia. */
private const val MIN_RELATED_FOR_PROJECT = 3

data class DevelopIdeaUiState(
    val related: List<Note> = emptyList(),
    val prompts: IdeaPrompts = IdeaPrompts(),
    /** Este dispositivo puede transcribir sin red. Si no, la fila no se pinta. */
    val canDictate: Boolean = false,
    val listening: Boolean = false,
    val micDenied: Boolean = false,
    /** Ya se guardo (como nota o como proyecto): la pantalla se va sola. */
    val saved: Boolean = false,
    val loading: Boolean = true,
) {
    val canConvertToProject: Boolean get() = related.size >= MIN_RELATED_FOR_PROJECT
}

/**
 * "Desarrollar una idea": conexiones, pregunta central, enfoques y preguntas, todo
 * anclado a datos reales de la propia idea. Ver [IdeaDevelopmentModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevelopIdeaViewModel(
    private val notes: NotesRepository,
    private val indexer: NoteIndexer,
    private val ideaDevelopment: IdeaDevelopmentModel,
    private val dictation: Dictation,
    private val clock: ZenClock,
    /** Guardar no puede morir con la pantalla: mismo motivo que [QuickNoteViewModel]. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    /** Nota de origen si se llego con "Desarrollar esta idea". Null: idea nueva. */
    private var sourceNote: Note? = null
    private val draftId = UUID.randomUUID().toString()

    /**
     * Lo escrito, **sin pasar por el filtro**.
     *
     * Mismo motivo que [NotesViewModel.query]: [state] viaja por `mapLatest` y una
     * busqueda por significado, asi que llega tarde. El campo tiene que leer de aqui.
     */
    private val _ideaText = MutableStateFlow("")
    val ideaText: StateFlow<String> = _ideaText.asStateFlow()

    private val _saved = MutableStateFlow(false)
    private val _voice = MutableStateFlow(VoiceState(canDictate = dictation.available))

    private var dictationJob: Job? = null
    private var textBeforeDictation: String = ""

    /** Precarga el cuerpo de una nota existente como punto de partida. */
    fun open(existingNoteId: String?) {
        if (existingNoteId == null) return
        viewModelScope.launch {
            val note = notes.note(existingNoteId) ?: return@launch
            sourceNote = note
            _ideaText.value = note.body
        }
    }

    fun onIdeaChange(value: String) {
        _ideaText.value = value
    }

    val state: StateFlow<DevelopIdeaUiState> =
        combine(_ideaText, _saved, _voice) { idea, saved, voice -> Triple(idea, saved, voice) }
            .mapLatest { (idea, saved, voice) ->
                val related = relatedNotes(idea)
                DevelopIdeaUiState(
                    related = related,
                    prompts = ideaDevelopment.generate(idea, related.size),
                    canDictate = voice.canDictate,
                    listening = voice.listening,
                    micDenied = voice.micDenied,
                    saved = saved,
                    loading = false,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DevelopIdeaUiState(),
            )

    /** Notas parecidas a la idea, sin contarse a si misma si viene de una nota existente. */
    private suspend fun relatedNotes(idea: String): List<Note> {
        if (idea.isBlank()) return emptyList()
        val excluded = sourceNote?.id
        return indexer.similarTo(idea)
            .filter { it.noteId != excluded }
            .mapNotNull { notes.note(it.noteId) }
    }

    /** Empieza o para de dictar. Mismo patron que [QuickNoteViewModel.toggleDictation]. */
    fun toggleDictation() {
        if (_voice.value.listening) {
            stopDictation()
            return
        }
        if (!dictation.available) return

        textBeforeDictation = _ideaText.value
        _voice.update { it.copy(listening = true, micDenied = false) }

        dictationJob = viewModelScope.launch {
            dictation.listen().collect { event ->
                when (event) {
                    is DictationEvent.Partial ->
                        _ideaText.value = joined(textBeforeDictation, event.text)

                    is DictationEvent.Final -> {
                        textBeforeDictation = joined(textBeforeDictation, event.text)
                        _ideaText.value = textBeforeDictation
                    }

                    DictationEvent.Stopped -> _voice.update { it.copy(listening = false) }
                }
            }
            _voice.update { it.copy(listening = false) }
        }
    }

    private fun stopDictation() {
        dictationJob?.cancel()
        dictationJob = null
        _voice.update { it.copy(listening = false) }
    }

    fun onMicrophoneDenied() {
        _voice.update { it.copy(listening = false, micDenied = true) }
    }

    private fun joined(before: String, spoken: String): String =
        if (before.isBlank()) spoken else "${before.trimEnd()} $spoken"

    /**
     * Guarda la idea como nota.
     *
     * Si venia de una nota existente, esa nota avanza a [NoteStage.DEVELOPED] como
     * consecuencia de haberla trabajado aqui, no por un selector suelto en la pantalla.
     */
    fun saveAsNote() {
        val idea = _ideaText.value.trim()
        if (idea.isBlank()) return
        stopDictation()
        appScope.launch {
            persistIdea(idea)
            _saved.value = true
        }
    }

    /**
     * Crea un proyecto con la idea actual y sus notas relacionadas.
     *
     * Recalcula las relacionadas en vez de fiarse de [state], que solo avanza mientras
     * alguien la esta observando: menos de tres relacionadas no es un patron.
     */
    fun convertToProject(title: String) {
        val idea = _ideaText.value.trim()
        if (idea.isBlank() || title.isBlank()) return
        stopDictation()

        appScope.launch {
            val related = relatedNotes(idea)
            if (related.size < MIN_RELATED_FOR_PROJECT) return@launch

            val ideaNote = persistIdea(idea)
            val projectId = UUID.randomUUID().toString()
            notes.saveProject(Project(id = projectId, title = title, createdAtMillis = clock.wallTimeMillis()))

            (related.map { it.id } + ideaNote.id).distinct().forEach { id ->
                notes.assignToProject(id, projectId)
                val note = notes.note(id) ?: return@forEach
                notes.save(note.copy(stage = NoteStage.PROJECT))
            }
            _saved.value = true
        }
    }

    /** Guarda la idea como nota, actualizando la de origen o creando una nueva. */
    private suspend fun persistIdea(idea: String): Note {
        val now = clock.wallTimeMillis()
        val origin = sourceNote
        val note = if (origin != null) {
            origin.copy(body = idea, updatedAtMillis = now, stage = NoteStage.DEVELOPED)
        } else {
            Note(
                id = draftId,
                createdAtMillis = now,
                updatedAtMillis = now,
                body = idea,
                stage = NoteStage.DEVELOPED,
            )
        }
        notes.save(note)
        sourceNote = note
        return note
    }

    private data class VoiceState(
        val canDictate: Boolean = false,
        val listening: Boolean = false,
        val micDenied: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
