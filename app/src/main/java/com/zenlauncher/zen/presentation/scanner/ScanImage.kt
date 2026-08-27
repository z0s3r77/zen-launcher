package com.zenlauncher.zen.presentation.scanner

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.zenlauncher.zen.presentation.util.rememberScaledBitmap

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
    // La lectura y la reduccion viven ahora en [rememberScaledBitmap], compartidas con
    // las imagenes de Notas y las portadas de Lectura. Ademas de dejar de repetir el
    // codigo, la decodificacion sale del hilo principal: aqui era una hoja de hasta
    // 2400 px leida del disco dentro de la composicion.
    val bitmap by rememberScaledBitmap(path, maxEdgePx, revision)

    bitmap?.let { image ->
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

/** Suficiente para la pantalla mas grande sin reservar la foto entera. */
private const val DEFAULT_MAX_EDGE_PX = 1440
