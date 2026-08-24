package com.zenlauncher.zen.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.notes.AttachmentKind
import com.zenlauncher.zen.domain.notes.AttachmentStore
import com.zenlauncher.zen.domain.notes.Dictation
import com.zenlauncher.zen.domain.notes.DictationEvent
import com.zenlauncher.zen.domain.notes.LinkExtractor
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteAttachment
import com.zenlauncher.zen.domain.notes.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class QuickNoteUiState(
    val text: String = "",
    val images: List<NoteAttachment> = emptyList(),
    /** Este dispositivo puede transcribir sin red. Si no, la fila no se pinta. */
    val canDictate: Boolean = false,
    val listening: Boolean = false,
    /** El usuario dijo que no al microfono. Se dice como texto, no como error. */
    val micDenied: Boolean = false,
    /** Ya se ha guardado: la pantalla se va sola. */
    val saved: Boolean = false,
) {
    /**
     * Una nota con una foto y sin una palabra sigue siendo una nota: se fotografia una
     * pizarra o una pagina y el texto es justamente lo que no hace falta escribir.
     */
    val canSave: Boolean get() = text.isNotBlank() || images.isNotEmpty()
}

/**
 * Captura.
 *
 * Todo lo que aqui podria costar tiempo —titulo, resumen, etiquetas, conexiones— queda
 * fuera a proposito: se guarda el texto tal cual y la nota sale con `enrichedAtMillis`
 * a null, que es como se apunta sola a la cola del asistente. Si generar un titulo
 * pudiera retrasar el guardado, la idea que el usuario acaba de tener dependeria de que
 * un modelo responda a tiempo.
 */
class QuickNoteViewModel(
    private val notes: NotesRepository,
    private val attachments: AttachmentStore,
    private val dictation: Dictation,
    private val clock: ZenClock,
    /**
     * Scope que sobrevive a esta pantalla.
     *
     * Guardar y limpiar **no pueden** ir en `viewModelScope`: al guardar, la pantalla
     * navega a la home en el mismo fotograma, el ViewModel se destruye y ese scope se
     * cancela; la escritura se quedaba a medias y la nota recien capturada se perdia
     * justo al guardarla, que es el unico fallo que esta funcion no se puede permitir.
     */
    private val appScope: CoroutineScope,
) : ViewModel() {

    /**
     * El id existe desde que se abre la pantalla, no desde que se guarda.
     *
     * Las imagenes se copian al almacenamiento propio en cuanto se eligen —la URI del
     * selector caduca— y necesitan saber a que carpeta van. Si el usuario se va sin
     * guardar, [discard] se lleva esa carpeta.
     */
    private val noteId = UUID.randomUUID().toString()

    private val _state = MutableStateFlow(QuickNoteUiState(canDictate = dictation.available))
    val state: StateFlow<QuickNoteUiState> = _state.asStateFlow()

    private var dictationJob: Job? = null

    /**
     * El texto que habia antes de empezar a dictar.
     *
     * Los resultados parciales llegan **completos y corregidos** en cada emision, no
     * como trozos que se suman: sin guardar el punto de partida, cada parcial borraria
     * lo que ya estaba escrito o lo duplicaria segun se acumulara.
     */
    private var textBeforeDictation: String = ""

    fun onTextChange(value: String) {
        _state.update { it.copy(text = value) }
    }

    /**
     * Copia la imagen elegida y la anade.
     *
     * Si falla no pasa nada visible: la nota sigue escribiendose y se guarda sin esa
     * foto. Un aviso de error aqui interrumpiria una captura que va con prisa para
     * contar algo que el usuario no puede arreglar.
     */
    fun addImage(sourceUri: String) {
        viewModelScope.launch {
            val attachment = attachments.storeImage(noteId, sourceUri) ?: return@launch
            _state.update { it.copy(images = it.images + attachment) }
        }
    }

    /**
     * Empieza o para de dictar.
     *
     * Parar es cancelar la recoleccion: [Dictation.listen] suelta el microfono en su
     * `awaitClose`. Se puede escribir con el teclado mientras se dicta, y lo escrito
     * antes no se pierde: lo dictado se anade detras.
     */
    fun toggleDictation() {
        if (_state.value.listening) {
            stopDictation()
            return
        }
        if (!dictation.available) return

        textBeforeDictation = _state.value.text
        _state.update { it.copy(listening = true, micDenied = false) }

        dictationJob = viewModelScope.launch {
            dictation.listen().collect { event ->
                when (event) {
                    is DictationEvent.Partial ->
                        _state.update { it.copy(text = joined(textBeforeDictation, event.text)) }

                    is DictationEvent.Final -> {
                        textBeforeDictation = joined(textBeforeDictation, event.text)
                        _state.update { it.copy(text = textBeforeDictation) }
                    }

                    DictationEvent.Stopped -> _state.update { it.copy(listening = false) }
                }
            }
            // Tambien al terminar el flujo por su cuenta: si el reconocedor se cierra
            // sin decir nada, la fila no puede quedarse diciendo ESCUCHANDO para siempre.
            _state.update { it.copy(listening = false) }
        }
    }

    private fun stopDictation() {
        dictationJob?.cancel()
        dictationJob = null
        _state.update { it.copy(listening = false) }
    }

    /**
     * El usuario denego el microfono.
     *
     * Se apunta como estado y se lee como texto —igual que BLOQUEADA o SONANDO—, no
     * como un aviso que interrumpe. Nada vuelve a pedirlo: si cambia de idea, se
     * concede desde Ajustes de Android.
     */
    fun onMicrophoneDenied() {
        _state.update { it.copy(listening = false, micDenied = true) }
    }

    /** Une lo escrito con lo dictado sin pegar palabras ni dejar dobles espacios. */
    private fun joined(before: String, spoken: String): String =
        if (before.isBlank()) spoken else "${before.trimEnd()} $spoken"

    /**
     * Guarda y marca la salida.
     *
     * El guardado se lanza en [appScope] y **no** se espera para dar por hecha la
     * salida: la escritura es una insercion local de unos milisegundos, y dejar la
     * pantalla congelada hasta que el disco confirme convertiria la captura rapida en
     * una espera. Si no hay nada que guardar, no se guarda nada.
     */
    fun save() {
        val current = _state.value
        val text = current.text.trim()
        if (!current.canSave) return

        val now = clock.wallTimeMillis()
        // Los enlaces salen del propio texto: ver [LinkExtractor]. No hay boton de
        // adjuntar enlace porque un enlace siempre llega pegado, y pedir un toque para
        // clasificar lo que se acaba de pegar es la friccion que esto viene a quitar.
        val links = LinkExtractor.extract(text).mapIndexed { index, url ->
            NoteAttachment(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                kind = AttachmentKind.LINK,
                value = url,
                // Se separan un milisegundo entre si para que el orden de lectura sea
                // el de aparicion en el texto: el almacen los devuelve por fecha.
                createdAtMillis = now + index,
            )
        }

        appScope.launch {
            notes.save(
                Note(
                    id = noteId,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    body = text,
                    attachments = current.images + links,
                ),
            )
        }
        _state.update { it.copy(saved = true) }
    }

    /**
     * Salir sin guardar.
     *
     * Se lleva las imagenes que ya se habian copiado. Sin esto, cada captura abandonada
     * despues de elegir una foto dejaria una carpeta que nadie va a mirar nunca y que
     * no aparece en ninguna nota: basura invisible que solo crece.
     */
    fun discard() {
        stopDictation()
        if (_state.value.saved) return
        appScope.launch { attachments.deleteFor(noteId) }
    }
}
