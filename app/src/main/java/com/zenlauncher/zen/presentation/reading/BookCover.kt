package com.zenlauncher.zen.presentation.reading

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    val bitmap: ImageBitmap? = remember(absolutePath) {
        runCatching { BitmapFactory.decodeFile(absolutePath)?.asImageBitmap() }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            // Sin descripcion: la ficha entera ya lleva una, con el titulo y el autor.
            contentDescription = null,
            modifier = modifier.border(ZenSpacing.Hairline, ZenColors.Border),
            contentScale = ContentScale.FillWidth,
        )
    }
}
