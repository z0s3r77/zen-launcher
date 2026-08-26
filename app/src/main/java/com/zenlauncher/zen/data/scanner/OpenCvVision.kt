package com.zenlauncher.zen.data.scanner

import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Carga OpenCV una sola vez, y **nunca lanza**.
 *
 * `initLocal` carga la libreria nativa que va empaquetada en el APK; si el telefono no
 * tiene la ABI que se empaqueto, o el `.so` no esta, lanza `UnsatisfiedLinkError`, que es
 * un `Error` y no una `Exception`: un `runCatching` normal no lo atrapa. Aqui se atrapa a
 * proposito el `Throwable` entero, porque al otro lado de este objeto esta la pantalla de
 * inicio del telefono: sin OpenCV el escaner tiene que decir que no puede, no tumbar el
 * launcher.
 *
 * Perezoso ademas de una sola vez: quien no abre el escaner no carga 25 MB de codigo
 * nativo en el proceso del launcher. Es la misma razon por la que el tiempo y las
 * noticias son `by lazy` en `ZenContainer`.
 */
internal object OpenCvVision {

    @Volatile
    private var loaded: Boolean? = null

    val available: Boolean
        get() = loaded ?: synchronized(this) {
            loaded ?: load().also { loaded = it }
        }

    private fun load(): Boolean = try {
        OpenCVLoader.initLocal()
    } catch (error: Throwable) {
        // UnsatisfiedLinkError incluido: ver la nota de la clase.
        Log.w(TAG, "OpenCV no pudo cargarse; el escaner queda no disponible", error)
        false
    }

    private const val TAG = "ZenScanner"
}
