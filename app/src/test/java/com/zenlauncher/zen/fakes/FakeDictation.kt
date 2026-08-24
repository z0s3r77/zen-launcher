package com.zenlauncher.zen.fakes

import com.zenlauncher.zen.domain.notes.Dictation
import com.zenlauncher.zen.domain.notes.DictationEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Dictado controlado por el test: el test decide que se oye y cuando se para.
 *
 * Un canal y no una lista fija porque dictar es una conversacion: el ViewModel tiene que
 * reaccionar a cada resultado parcial segun llega, y una lista emitida de golpe no
 * probaria que el texto va apareciendo mientras se habla.
 */
class FakeDictation(
    override val available: Boolean = true,
) : Dictation {

    private val events = Channel<DictationEvent>(capacity = Channel.UNLIMITED)

    /**
     * Cuantas veces se dejo de escuchar.
     *
     * Es lo que en el reconocedor real suelta el microfono (`awaitClose` llama a
     * `destroy`): si esto no sube al parar, el launcher se queda el microfono cogido.
     */
    var released = 0
        private set

    override fun listen(): Flow<DictationEvent> =
        events.receiveAsFlow().onCompletion { released++ }

    /** Lo que se va oyendo mientras se habla. Cada emision reemplaza a la anterior. */
    suspend fun hear(text: String) = events.send(DictationEvent.Partial(text))

    /** El texto ya cerrado, el que se queda. */
    suspend fun settle(text: String) = events.send(DictationEvent.Final(text))

    /** Silencio, error o el reconocedor que se cierra: para el caso, lo mismo. */
    suspend fun stop() = events.send(DictationEvent.Stopped)
}
