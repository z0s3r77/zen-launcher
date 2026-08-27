package com.zenlauncher.zen.presentation.util

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lee un JPEG del disco **fuera del hilo principal y ya reducido**.
 *
 * Antes cada pantalla lo hacia por su cuenta con
 * `remember(ruta) { BitmapFactory.decodeFile(ruta) }`, y eso son dos problemas dentro de
 * un mismo `remember`:
 *
 * 1. **Decodifica en el hilo principal**, dentro de la composicion. En la retícula de
 *    Notas eso es leer y descomprimir un JPEG en mitad de un desplazamiento.
 * 2. **Decodifica a tamano completo.** Las fotos de las notas se guardan reducidas a
 *    2048 px de lado, que siguen siendo hasta 16 MB de bitmap por imagen —y varias a la
 *    vez— dentro del proceso del **launcher**, que es justo el que `LauncherMemory` se
 *    esfuerza en mantener pequeno para que el sistema no lo mate.
 *
 * Aqui se hacen las dos cosas bien: `inJustDecodeBounds` para medir sin reservar nada y
 * `inSampleSize` para descomprimir ya al tamano que se va a pintar. Es la tecnica que
 * `ScanImage` ya usaba; esto la comparte.
 *
 * Devuelve null mientras lee y tambien si el fichero no esta o no se deja leer. Quien
 * llama no pinta nada en ese caso: un hueco con un icono roto dice que a la aplicacion
 * le falta algo que el usuario no puede arreglar.
 *
 * @param revision cambia cuando el fichero cambia sin cambiar de nombre. Sin esto, quien
 *   reescribe la misma ruta seguiria viendo la imagen anterior.
 */
@Composable
fun rememberScaledBitmap(
    absolutePath: String,
    maxEdgePx: Int,
    revision: Int = 0,
): State<ImageBitmap?> = produceState<ImageBitmap?>(
    initialValue = null,
    absolutePath,
    maxEdgePx,
    revision,
) {
    value = withContext(Dispatchers.IO) {
        runCatching { decodeScaled(absolutePath, maxEdgePx)?.asImageBitmap() }.getOrNull()
    }
}

/**
 * Mide primero y descomprime despues, para que el bitmap a tamano completo no llegue a
 * existir en ningun momento.
 */
private fun decodeScaled(path: String, maxEdgePx: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        },
    )
}

/** Potencia de dos, que es lo unico que `inSampleSize` respeta de verdad. */
private fun sampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
    if (maxEdgePx <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / sample > maxEdgePx) sample *= 2
    return sample
}
