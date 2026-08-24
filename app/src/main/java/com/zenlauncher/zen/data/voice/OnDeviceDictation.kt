package com.zenlauncher.zen.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.zenlauncher.zen.domain.notes.Dictation
import com.zenlauncher.zen.domain.notes.DictationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

/**
 * Dictado con el reconocedor **en el dispositivo** de Android.
 *
 * No se empotra ningun modelo ni se descarga nada: Android ya trae reconocimiento de voz
 * sin conexion, y usarlo cuesta cero megabytes de APK. Es
 * `createOnDeviceSpeechRecognizer` y no el normal a proposito: el normal manda el audio
 * a los servidores del reconocedor, y en Zen nada de lo que se apunta sale del telefono.
 *
 * Si el dispositivo no lo trae, o le falta el paquete de voz del idioma, [available] es
 * false y la fila de dictar no llega a pintarse.
 */
class OnDeviceDictation(
    context: Context,
    private val locale: Locale = Locale.getDefault(),
) : Dictation {

    private val appContext = context.applicationContext

    override val available: Boolean
        get() = runCatching {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        }.getOrDefault(false)

    override fun listen(): Flow<DictationEvent> = callbackFlow {
        // SpeechRecognizer es de hilo principal: crearlo o hablarle desde otro lanza.
        // El `flowOn(Main)` de abajo es lo que garantiza que este bloque corra ahi.
        val recognizer = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        }.getOrNull()

        if (recognizer == null) {
            trySend(DictationEvent.Stopped)
            close()
            return@callbackFlow
        }

        val listener = object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstResult()?.let { trySend(DictationEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                results.firstResult()?.let { trySend(DictationEvent.Final(it)) }
                trySend(DictationEvent.Stopped)
                close()
            }

            override fun onError(error: Int) {
                // Todos los errores acaban igual: se para y se vuelve a tocar. Ver
                // [DictationEvent.Stopped].
                trySend(DictationEvent.Stopped)
                close()
            }

            override fun onEndOfSpeech() {
                // Aqui no se cierra: el reconocedor todavia tiene que entregar el
                // resultado final por `onResults`. Cerrar en cuanto se deja de hablar
                // se comia la ultima frase dictada.
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent())

        awaitClose {
            // Se llama al cancelar la recoleccion (el usuario para el dictado) y al
            // cerrar desde dentro. `destroy` suelta el microfono: sin esto, salir de la
            // pantalla dictando dejaria el microfono cogido por el launcher.
            runCatching {
                recognizer.stopListening()
                recognizer.destroy()
            }
        }
    }.flowOn(Dispatchers.Main)

    private fun intent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // Que el texto aparezca segun se habla es lo que dice que te esta oyendo.
            // Sin esto, dictar es mirar una pantalla quieta y esperar.
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            // Cinturon y tirantes: el reconocedor ya es el de dispositivo, pero si un
            // fabricante lo resolviera a otro, esto le prohibe salir a la red igual.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
}
