package com.zenlauncher.zen.presentation.notes

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/**
 * Una nota como recuadro: titulo, lo que dice y cuando se apunto.
 *
 * Antes cada nota era una fila de una linea, igual que "Nota rapida" o "Proyectos": la
 * lista y los controles se leian iguales y habia que abrir una nota para recordar de
 * que iba. Aqui se ve el mensaje sin entrar, que es todo el sentido de una libreta.
 *
 * Marco de 1px y fondo negro, no relleno gris: el fondo ocupa casi toda la pantalla en
 * un AMOLED y un gris apagaria la unica ventaja de tenerlo apagado. El recuadro lo
 * dibuja el borde, no una superficie.
 */
@Composable
fun NoteCard(
    note: Note,
    nowMillis: Long,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = note.preview

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .border(ZenSpacing.Hairline, ZenColors.Border)
            // Una nota de dos palabras no puede quedar como una rendija: el recuadro
            // tiene que leerse como recuadro aunque casi no lleve texto.
            .heightIn(min = CARD_MIN_HEIGHT)
            .padding(ZenSpacing.Medium),
    ) {
        Text(
            text = note.displayTitle,
            style = ZenTextStyles.Tile,
            color = ZenColors.Foreground,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )

        if (preview.isNotBlank()) {
            Spacer(Modifier.height(ZenSpacing.Small))
            Text(
                text = preview,
                style = ZenTextStyles.Body,
                color = ZenColors.Secondary,
                maxLines = PREVIEW_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(ZenSpacing.Medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            MonoLabel(
                text = ZenDateFormats.shortDate(
                    epochMillis = note.createdAtMillis,
                    nowMillis = nowMillis,
                    locale = locale,
                ),
            )
            // Solo con algo detras: una nota sin adjuntos no lleva marca. Y va escrito,
            // no como icono, para que se lea igual sin distinguir formas pequenas.
            attachmentsLabel(note)?.let { MonoLabel(text = it, color = ZenColors.Muted) }
        }
    }
}

/** Null cuando la nota es solo texto: entonces no hay nada que anunciar. */
@Composable
private fun attachmentsLabel(note: Note): String? {
    val images = note.images.size
    val links = note.links.size
    if (images == 0 && links == 0) return null

    return listOfNotNull(
        images.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.notes_card_images, it, it) },
        links.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.notes_card_links, it, it) },
    ).joinToString("  ")
}

private val CARD_MIN_HEIGHT = 96.dp
private const val TITLE_MAX_LINES = 3
private const val PREVIEW_MAX_LINES = 6
