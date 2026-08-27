package com.zenlauncher.zen.presentation.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.zenlauncher.zen.presentation.util.rememberScaledBitmap

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
    // Por ruta, ya reducido y **fuera del hilo principal**: ver [rememberScaledBitmap].
    // Se decodificaba a tamano completo dentro de la composicion, o sea leyendo y
    // descomprimiendo un JPEG de hasta 2048 px en mitad del desplazamiento de la lista.
    val bitmap by rememberScaledBitmap(absolutePath, MAX_EDGE_PX)

    bitmap?.let { image ->
        Image(
            bitmap = image,
            // Sin descripcion: no hay nada que decir de la imagen que Zen sepa, y una
            // descripcion inventada ("imagen") es ruido para quien usa lector.
            contentDescription = null,
            modifier = modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/**
 * Una nota se lee en el ancho de un movil: por encima de esto se descomprimen pixeles
 * que no llegan a pintarse. 1080 es el ancho del Phone (2a).
 */
private const val MAX_EDGE_PX = 1080
