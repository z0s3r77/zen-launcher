package com.zenlauncher.zen.domain.notes

import kotlinx.coroutines.flow.Flow

/**
 * Dictado, siempre en el dispositivo.
 *
 * Es una interfaz para que el ViewModel de captura no toque `SpeechRecognizer` ni sepa
 * nada de sesiones de audio: el dictado se prueba entonces sin microfono y sin Android.
 */
interface Dictation {

    /**
     * Si este dispositivo puede transcribir **sin red**.
     *
     * Cuando es false, la fila de dictar no se pinta. Es la regla de siempre: lo que no
     * tiene nada detras no se ensena, y un "Dictar" que al tocarlo dijera que no se
     * puede seria un control que existe solo para negarse.
     */
    val available: Boolean

    /**
     * Escucha hasta que se cancele la recoleccion o el reconocedor se pare solo.
     *
     * Emite el texto segun se habla —para que se vea aparecer, que es lo que dice que
     * te esta oyendo— y una ultima vez ya corregido. **El audio no se guarda en ningun
     * sitio**: lo unico que sale de aqui es texto.
     */
    fun listen(): Flow<DictationEvent>
}

sealed interface DictationEvent {

    /** Texto provisional mientras se habla. Se reemplaza entero en cada emision. */
    data class Partial(val text: String) : DictationEvent

    /** Texto ya cerrado. Es el que se queda en la nota. */
    data class Final(val text: String) : DictationEvent

    /**
     * Se acabo: silencio, error o el propio reconocedor que se cierra.
     *
     * No se distingue el error del final normal a proposito. Para quien esta dictando
     * una idea, "no te he entendido" y "has dejado de hablar" se resuelven igual: se
     * vuelve a tocar. Un mensaje de error aqui interrumpiria la captura para contar un
     * detalle tecnico que no cambia lo que hay que hacer.
     */
    data object Stopped : DictationEvent
}
