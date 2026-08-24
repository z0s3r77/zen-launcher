package com.zenlauncher.zen.presentation.notes

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Una imagen de una nota, leida del almacenamiento propio.
 *
 * Sin libreria de carga de imagenes. Coil o Glide traerian cache en disco, transiciones
 * y peticiones de red que aqui no hacen falta: los ficheros son locales, ya vienen
 * reducidos de [com.zenlauncher.zen.data.notes.FileAttachmentStore] y una nota tiene
 * una o dos. Una dependencia mas en un launcher se paga en cada arranque.
 *
 * Si el fichero no esta o no se puede leer **no se pinta nada**: ni marco vacio ni icono
 * roto. El texto de la nota es lo que importa, y un hueco con un aviso de error solo
 * dice que a la aplicacion le falta algo que el usuario no puede arreglar.
 */
@Composable
fun NoteImage(
    absolutePath: String,
    modifier: Modifier = Modifier,
) {
    // `remember` por ruta: sin esto se releeria el fichero del disco en cada
    // recomposicion, que en una lista con desplazamiento es leer JPEG en cada fotograma.
    val bitmap: ImageBitmap? = remember(absolutePath) {
        runCatching { BitmapFactory.decodeFile(absolutePath)?.asImageBitmap() }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            // Sin descripcion: no hay nada que decir de la imagen que Zen sepa, y una
            // descripcion inventada ("imagen") es ruido para quien usa lector.
            contentDescription = null,
            modifier = modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}
