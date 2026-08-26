package com.zenlauncher.zen.presentation.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.ImportFailure
import com.zenlauncher.zen.domain.reading.ImportState
import com.zenlauncher.zen.domain.reading.ReadingProgress
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * Mis libros.
 *
 * Misma reparticion que Notas y por la misma razon: **lo que se hace arriba y fijo, lo
 * que se lee debajo y desplazable**. Anadir un libro es un control y lleva el marco
 * tecnico; cada libro es contenido y no lo lleva.
 *
 * No hay estanterias, ni etiquetas, ni orden por titulo: la lista es "lo ultimo que
 * estuve leyendo primero", que es la unica pregunta que uno le hace a su biblioteca al
 * abrirla. Un libro recien importado y todavia sin abrir cuenta como lo mas reciente.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onAddBook: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onDismissImport: () -> Unit,
    /** Traduce la ruta relativa de la portada a una absoluta. Ver `BookCoverStore`. */
    coverPath: (String) -> String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Salir de la biblioteca da por visto el resultado de la ultima importacion: sin
    // esto, el error de ayer seguiria encima de la lista al volver manana.
    DisposableEffect(Unit) { onDispose(onDismissImport) }

    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.reading_title),
            right = "%02d".format(state.books.size),
            onBack = onBack,
        )

        if (state.available) {
            ZenTagButton(
                text = stringResource(R.string.reading_add_book),
                onClick = onAddBook,
                onClickLabel = stringResource(R.string.reading_add_book_label),
                modifier = Modifier.fillMaxWidth(),
                stretch = true,
            )
        } else {
            // El telefono no sabe extraer texto de un PDF. Se dice antes de ofrecer
            // nada: hacer elegir un fichero para luego no poder abrirlo es peor.
            Spacer(Modifier.height(ZenSpacing.Small))
            Text(
                text = stringResource(R.string.reading_unsupported),
                style = ZenTextStyles.Body,
                color = ZenColors.Secondary,
            )
            Spacer(Modifier.height(ZenSpacing.Small))
        }

        ImportBand(state = state.import, onDismiss = onDismissImport)

        ZenHairline()

        if (state.empty) {
            Spacer(Modifier.height(ZenSpacing.XXLarge))
            MonoLabel(text = stringResource(R.string.reading_empty))
            return@ZenScreen
        }

        Spacer(Modifier.height(ZenSpacing.Medium))
        MonoLabel(text = stringResource(R.string.reading_my_books))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            // El aire final va en el relleno y **no** como un elemento mas: ver la
            // regresion que fija `NotesScreenTest`. Una lista perezosa que empieza vacia
            // se ancla al ultimo elemento y abre la pantalla por la mitad.
            contentPadding = PaddingValues(bottom = ZenSpacing.XXLarge),
        ) {
            items(items = state.books, key = { it.id }) { book ->
                ZenHairline()
                BookRow(book = book, coverPath = coverPath, onClick = { onOpenBook(book) })
            }
        }
    }
}

/**
 * Una ficha de la biblioteca.
 *
 * La barra de progreso son **caracteres**, no un dibujo. Asi cumple sin esfuerzo la
 * regla de que todo estado se lee como texto, y ademas evita una segunda grafica en una
 * aplicacion que tiene una sola a proposito (la de la semana). Al lado va el tanto por
 * ciento, porque doce cuadraditos no se cuentan de un vistazo.
 */
@Composable
private fun BookRow(
    book: Book,
    coverPath: (String) -> String,
    onClick: () -> Unit,
) {
    val percent = ReadingProgress.percent(book.lastBlockIndex, book.blockCount)
    val description = book.author?.let { author ->
        stringResource(R.string.reading_book_description, book.title, author, percent)
    } ?: stringResource(R.string.reading_book_description_no_author, book.title, percent)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(vertical = ZenSpacing.Medium),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Medium),
    ) {
        book.coverPath?.let { relative ->
            BookCover(
                absolutePath = coverPath(relative),
                modifier = Modifier.width(COVER_WIDTH),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = ZenTextStyles.Tile,
                color = ZenColors.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let { author ->
                Spacer(Modifier.height(ZenSpacing.Base))
                Text(
                    text = author,
                    style = ZenTextStyles.Body,
                    color = ZenColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(ZenSpacing.Small))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small)) {
                MonoLabel(
                    text = ReadingProgress.bar(book.lastBlockIndex, book.blockCount),
                    color = ZenColors.Secondary,
                )
                MonoLabel(
                    text = stringResource(R.string.reading_percent, percent),
                    color = ZenColors.Dim,
                )
            }
        }
    }
}

/**
 * En que va la importacion, o como acabo la ultima.
 *
 * Lo que no tiene nada detras no se pinta: sin importacion en marcha ni resultado que
 * contar, esta franja no existe y la biblioteca queda exactamente igual.
 */
@Composable
private fun ImportBand(state: ImportState, onDismiss: () -> Unit) {
    if (state is ImportState.Idle) return

    val text = when (state) {
        is ImportState.Reading ->
            if (state.total > 0) {
                stringResource(R.string.reading_import_pages, state.page, state.total)
            } else {
                stringResource(R.string.reading_import_opening)
            }
        ImportState.Building -> stringResource(R.string.reading_import_building)
        is ImportState.Done -> stringResource(R.string.reading_import_done, state.title)
        is ImportState.Failed -> stringResource(
            when (state.reason) {
                ImportFailure.UNSUPPORTED -> R.string.reading_unsupported
                ImportFailure.UNREADABLE -> R.string.reading_import_unreadable
                ImportFailure.NO_TEXT -> R.string.reading_import_no_text
            },
        )
        ImportState.Idle -> return
    }

    // Mientras trabaja no se puede descartar: quitar el aviso no pararia la lectura del
    // PDF y dejaria al usuario sin la unica senal de que algo esta pasando.
    val dismissable = !state.busy

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (dismissable) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.reading_import_dismiss),
                        onClick = onDismiss,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = ZenSpacing.Small),
    ) {
        Text(
            text = text,
            style = ZenTextStyles.Body,
            color = if (state is ImportState.Failed) ZenColors.Tertiary else ZenColors.Secondary,
        )
    }
}

/** El ancho de una portada en la lista. Un tercio de fila: es una senal, no una imagen. */
private val COVER_WIDTH = 52.dp
