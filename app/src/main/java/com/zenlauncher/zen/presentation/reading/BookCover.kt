package com.zenlauncher.zen.presentation.reading

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.zenlauncher.zen.presentation.util.rememberScaledBitmap
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * La portada de un libro, leida del almacenamiento propio.
 *
 * Mismo planteamiento que `NoteImage` y por las mismas razones: sin libreria de carga de
 * imagenes, `remember` por ruta para no releer el JPEG en cada fotograma de la lista, y
 * **si el fichero no esta no se pinta nada**. Un marco vacio donde deberia haber una
 * portada solo dice que a la aplicacion le falta algo que el usuario no puede arreglar.
 *
 * Es la unica imagen en color del launcher junto a las fotos de las notas. Lleva filete
 * alrededor para que la hoja blanca de un PDF no quede flotando sobre el negro.
 */
@Composable
fun BookCover(
    absolutePath: String,
    modifier: Modifier = Modifier,
) {
    // Ya reducida y fuera del hilo principal: ver [rememberScaledBitmap]. En una lista de
    // fichas, decodificar la portada entera dentro de la composicion es leer del disco en
    // mitad del desplazamiento.
    val bitmap by rememberScaledBitmap(absolutePath, MAX_EDGE_PX)

    bitmap?.let { image ->
        Image(
            bitmap = image,
            // Sin descripcion: la ficha entera ya lleva una, con el titulo y el autor.
            contentDescription = null,
            modifier = modifier.border(ZenSpacing.Hairline, ZenColors.Border),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/** Una portada de ficha no pasa de un tercio del ancho de la pantalla. */
private const val MAX_EDGE_PX = 512
