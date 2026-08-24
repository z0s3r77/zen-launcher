package com.zenlauncher.zen.presentation.notes

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/**
 * Notas: capturar, buscar y recuperar, en ese orden de arriba abajo.
 *
 * La captura va **primera y arriba del todo** porque es lo unico que tiene prisa: una
 * idea se apunta en el momento o se pierde. Buscar y releer se hacen con calma, asi que
 * viven debajo.
 *
 * No hay carpetas, ni etiquetas para filtrar, ni orden por relevancia. La lista es
 * cronologica: en un cuaderno, lo de ayer esta donde estaba ayer, y una lista que se
 * recoloca sola obliga a buscar cada vez lo que ya se sabia donde estaba.
 */
@Composable
fun NotesScreen(
    state: NotesUiState,
    /**
     * Lo escrito en el buscador, tal cual y sin pasar por el filtro.
     *
     * Va aparte de [state] a proposito: ver [NotesViewModel.query]. Si el campo leyera
     * el texto que vuelve del filtro, perderia letras al escribir.
     */
    query: String,
    nowMillis: Long,
    onQueryChange: (String) -> Unit,
    onQuickNote: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.notes_title),
            right = "%02d".format(state.total),
            onBack = onBack,
        )

        // La unica accion con nombre propio de esta pantalla. "Desarrollar una idea"
        // todavia no existe y por eso **no esta**: una fila que no hace nada ensena a
        // desconfiar de las que si funcionan, que es justo por lo que se quito el
        // PRONTO de la pantalla de inicio.
        ZenListRow(
            label = stringResource(R.string.notes_quick),
            index = "··",
            onClick = onQuickNote,
            onClickLabel = stringResource(R.string.notes_quick_label),
        )
        ZenHairline(color = ZenColors.Border)

        ZenSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.notes_search_placeholder),
        )
        ZenHairline()

        if (state.empty) {
            Spacer(Modifier.height(ZenSpacing.XXLarge))
            MonoLabel(text = stringResource(R.string.notes_empty))
            return@ZenScreen
        }

        Spacer(Modifier.height(ZenSpacing.Large))
        // Se rotula distinto segun de donde venga la lista: "lo ultimo que apunte" y
        // "lo que encontre buscando" son dos cosas, y con el mismo rotulo el usuario no
        // sabe si esta viendo todo o un filtro.
        MonoLabel(
            text = stringResource(
                if (state.searching) R.string.notes_results else R.string.notes_recent,
            ),
        )
        Spacer(Modifier.height(ZenSpacing.Small))

        if (state.noResults) {
            MonoLabel(text = stringResource(R.string.notes_no_results))
            return@ZenScreen
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.notes, key = { it.id }) { note ->
                ZenHairline()
                NoteRow(note, nowMillis, locale, onOpenNote)
            }

            // Encontradas por significado, no por las palabras escritas. Van aparte y
            // con su propio rotulo: lo que contiene lo que buscaste y lo que se parece a
            // lo que buscaste son dos cosas, y mezclarlas haria dudar de si el buscador
            // entiende lo que se le pide.
            if (state.related.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(ZenSpacing.Large))
                    MonoLabel(text = stringResource(R.string.notes_related))
                    Spacer(Modifier.height(ZenSpacing.Small))
                }
                // Prefijo en la clave: la misma nota puede salir en la lista de arriba y
                // aqui, y `LazyColumn` revienta con dos claves iguales. Reventar aqui es
                // dejar el telefono sin pantalla de inicio.
                items(state.related, key = { note -> "rel-" + note.id }) { note ->
                    ZenHairline()
                    NoteRow(note, nowMillis, locale, onOpenNote)
                }
            }

            // Notas con algo esperando respuesta. Es un aviso, no una bandeja de
            // entrada: van tres como mucho y desaparece en cuanto se responden. Una
            // lista larga de pendientes es una lista de tareas, y Zen no reparte tareas.
            if (state.withSuggestions.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(ZenSpacing.XLarge))
                    MonoLabel(text = stringResource(R.string.notes_suggestions))
                    Spacer(Modifier.height(ZenSpacing.Small))
                }
                items(state.withSuggestions, key = { note -> "sug-" + note.id }) { note ->
                    ZenHairline()
                    NoteRow(note, nowMillis, locale, onOpenNote)
                }
                item { ZenHairline() }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    nowMillis: Long,
    locale: Locale,
    onOpenNote: (Note) -> Unit,
) {
    ZenListRow(
        label = note.displayTitle,
        onClick = { onOpenNote(note) },
        trailing = {
            MonoLabel(
                text = ZenDateFormats.shortDate(
                    epochMillis = note.createdAtMillis,
                    nowMillis = nowMillis,
                    locale = locale,
                ),
            )
        },
    )
}
