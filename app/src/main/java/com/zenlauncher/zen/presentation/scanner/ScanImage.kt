package com.zenlauncher.zen.presentation.scanner

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlin.math.max

/**
 * Una pagina escaneada, leida del disco a un tamano razonable para la pantalla.
 *
 * Mismo planteamiento que `BookCover` —sin libreria de carga de imagenes y con `remember`
 * por ruta— con **una diferencia que aqui es imprescindible**: se decodifica reducida.
 *
 * Una hoja enderezada mide hasta 2400 px de lado, o sea unos 16 MB de bitmap. Pintar eso
 * en un movil de 1080 px de ancho reserva quince veces la memoria que se ve, y esto corre
 * en el proceso de la pantalla de inicio, que es el que el sistema no deberia tener que
 * matar. `inSampleSize` hace que el decodificador lea directamente reducido, sin llegar a
 * construir el grande.
 *
 * @param revision cambia cada vez que se reescribe el fichero. La ruta no cambia al
 *   reprocesar una pagina, asi que sin esto `remember` seguiria ensenando el filtro
 *   anterior. Ver `ScanPage.revision`.
 */
@Composable
internal fun ScanImage(
    path: String,
    revision: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
) {
    val bitmap: ImageBitmap? = remember(path, revision, maxEdgePx) {
        runCatching { decodeScaled(path, maxEdgePx)?.asImageBitmap() }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

/**
 * Dos pasadas: la primera solo lee las medidas, la segunda decodifica ya reducida.
 *
 * `inSampleSize` solo admite potencias de dos, asi que se busca la mayor que deje la
 * imagen por encima del limite pedido: quedarse corto es una imagen borrosa, y pasarse es
 * memoria que no se ve.
 */
private fun decodeScaled(path: String, maxEdgePx: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null

    var sample = 1
    while (longest / (sample * 2) >= maxEdgePx) sample *= 2

    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Suficiente para la pantalla mas grande sin reservar la foto entera. */
private const val DEFAULT_MAX_EDGE_PX = 1440
