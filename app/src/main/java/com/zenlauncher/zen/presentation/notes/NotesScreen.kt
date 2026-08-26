package com.zenlauncher.zen.presentation.notes

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.RecurringCluster
import com.zenlauncher.zen.domain.notes.RecurringWord
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
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
 *
 * La pantalla se reparte en dos mitades que no se mezclan:
 *
 * - **Lo que se hace** —capturar, desarrollar, buscar, abrir proyectos— vive arriba,
 *   fijo, y tiene forma de control: rotulo tecnico dentro de un marco. Fijo y no dentro
 *   del desplazamiento porque una idea no puede esperar a que se vuelva arriba, y
 *   porque el buscador dentro de una lista perezosa pierde el foco al salirse de la
 *   pantalla mientras se escribe.
 * - **Lo que se lee** —las notas— vive debajo, se desplaza y tiene forma de recuadro.
 *
 * Antes todo era la misma fila de una linea: capturar, una nota, un patron y un
 * proyecto se leian igual, y de la nota solo se veia el titulo. Dos formas distintas
 * para dos cosas distintas es lo que quita el ruido, no quitar funciones.
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
    onDevelopIdea: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onAcceptClusterSuggestion: (RecurringCluster, String) -> Unit,
    onIgnoreClusterSuggestion: (RecurringCluster) -> Unit,
    onOpenProjects: () -> Unit,
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

        // Captura arriba, pensar debajo: apuntar una idea tiene prisa, desarrollarla no.
        // Los dos marcos miden lo mismo porque ninguna de las dos manda sobre la otra.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
        ) {
            ZenTagButton(
                text = stringResource(R.string.notes_quick),
                onClick = onQuickNote,
                modifier = Modifier.weight(1f),
                onClickLabel = stringResource(R.string.notes_quick_label),
                stretch = true,
            )
            ZenTagButton(
                text = stringResource(R.string.notes_develop),
                onClick = onDevelopIdea,
                modifier = Modifier.weight(1f),
                onClickLabel = stringResource(R.string.notes_develop_label),
                stretch = true,
            )
        }

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

        Spacer(Modifier.height(ZenSpacing.Medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Se rotula distinto segun de donde venga la lista: "lo ultimo que apunte" y
            // "lo que encontre buscando" son dos cosas, y con el mismo rotulo el usuario
            // no sabe si esta viendo todo o un filtro.
            MonoLabel(
                text = stringResource(
                    if (state.searching) R.string.notes_results else R.string.notes_recent,
                ),
            )
            // Los proyectos son otra forma de mirar estas mismas notas, asi que el
            // acceso vive en la linea que rotula la lista y no ocupa alto propio. Solo
            // existe con al menos un proyecto: con cero, no hay boton.
            if (state.hasProjects) {
                ZenTagButton(
                    text = stringResource(R.string.notes_projects),
                    onClick = onOpenProjects,
                )
            }
        }

        if (state.noResults) {
            MonoLabel(text = stringResource(R.string.notes_no_results))
            return@ZenScreen
        }

        Spacer(Modifier.height(ZenSpacing.Small))

        // Retícula desigual, no rejilla de celdas iguales: cada recuadro mide lo que
        // mide su nota, asi que dos columnas caben sin recortar unas ni dejar hueco en
        // otras. Es lo que hace que la lista se lea de un vistazo.
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(COLUMNS),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalItemSpacing = ZenSpacing.Small,
            horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
            // El aire final va como margen y **no** como un elemento mas. Siendolo, en
            // el primer fotograma —todavia cargando, sin notas— era el unico elemento
            // de la retícula, asi que quedaba de ancla; al llegar las notas y pasar al
            // final, la retícula se desplazaba para mantenerlo a la vista y Notas se
            // abria por la mitad de la lista. Un margen no es un elemento y no ancla.
            contentPadding = PaddingValues(bottom = ZenSpacing.Large),
        ) {
            noteCards(state.notes, nowMillis, locale, onOpenNote)

            // Encontradas por significado, no por las palabras escritas. Van aparte y
            // con su propio rotulo: lo que contiene lo que buscaste y lo que se parece a
            // lo que buscaste son dos cosas, y mezclarlas haria dudar de si el buscador
            // entiende lo que se le pide.
            if (state.related.isNotEmpty()) {
                sectionLabel(R.string.notes_related)
                // Prefijo en la clave: la misma nota puede salir en la lista de arriba y
                // aqui, y la retícula revienta con dos claves iguales. Reventar aqui es
                // dejar el telefono sin pantalla de inicio.
                noteCards(state.related, nowMillis, locale, onOpenNote, keyPrefix = "rel-")
            }

            // Notas con algo esperando respuesta. Es un aviso, no una bandeja de
            // entrada: van tres como mucho y desaparece en cuanto se responden. Una
            // lista larga de pendientes es una lista de tareas, y Zen no reparte tareas.
            //
            // Van como fila y no como recuadro aunque sean notas: casi siempre son
            // notas que ya estan mas arriba en la lista, y repetir el mismo recuadro
            // entero dos veces en una pantalla es lo que la hacia parecer un caos. La
            // fila dice lo mismo —cual es y que espera respuesta— sin duplicar nada.
            if (state.withSuggestions.isNotEmpty()) {
                sectionLabel(R.string.notes_suggestions)
                items(
                    items = state.withSuggestions,
                    key = { note -> "sug-" + note.id },
                    span = { StaggeredGridItemSpan.FullLine },
                ) { note ->
                    SuggestionRow(note, nowMillis, locale, onOpenNote)
                }
            }

            // Patrones: raices a las que se vuelve y grupos de notas ya conectadas
            // entre si. Solo con datos reales detras, nunca una afirmacion inventada.
            if (state.patterns.isNotEmpty() || state.projectSuggestions.isNotEmpty()) {
                sectionLabel(R.string.notes_patterns)
                items(
                    items = state.patterns,
                    key = { word -> "word-" + word.stem },
                    span = { StaggeredGridItemSpan.FullLine },
                ) { word ->
                    PatternWordRow(word, onSearch = onQueryChange)
                }
                items(
                    items = state.projectSuggestions,
                    key = { cluster -> "cluster-" + cluster.noteIds.sorted() },
                    span = { StaggeredGridItemSpan.FullLine },
                ) { cluster ->
                    ClusterSuggestionCard(
                        cluster = cluster,
                        allNotes = state.notes,
                        onAccept = onAcceptClusterSuggestion,
                        onIgnore = onIgnoreClusterSuggestion,
                    )
                }
            }

        }
    }
}

/** Un tramo de notas de la retícula. El prefijo evita que la misma nota choque consigo misma. */
private fun LazyStaggeredGridScope.noteCards(
    notes: List<Note>,
    nowMillis: Long,
    locale: Locale,
    onOpenNote: (Note) -> Unit,
    keyPrefix: String = "",
) {
    items(items = notes, key = { note -> keyPrefix + note.id }) { note ->
        NoteCard(
            note = note,
            nowMillis = nowMillis,
            locale = locale,
            onClick = { onOpenNote(note) },
        )
    }
}

/** Rotulo que parte la retícula de lado a lado: sin el, una seccion nueva pareceria mas notas. */
private fun LazyStaggeredGridScope.sectionLabel(resId: Int) {
    item(key = "label-$resId", span = StaggeredGridItemSpan.FullLine) {
        Column {
            Spacer(Modifier.height(ZenSpacing.Large))
            MonoLabel(text = stringResource(resId))
            Spacer(Modifier.height(ZenSpacing.Small))
        }
    }
}

/**
 * Una nota que tiene una propuesta esperando respuesta.
 *
 * Solo el nombre y la fecha: quien entra aqui no viene a releerla, viene a decidir si
 * conecta con otra, y eso se hace dentro de la nota.
 */
@Composable
private fun SuggestionRow(
    note: Note,
    nowMillis: Long,
    locale: Locale,
    onOpenNote: (Note) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpenNote(note) }
            .heightIn(min = TOUCH_TARGET),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Medium),
    ) {
        Text(
            text = note.displayTitle,
            style = ZenTextStyles.Body,
            color = ZenColors.Tertiary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MonoLabel(
            text = ZenDateFormats.shortDate(note.createdAtMillis, nowMillis, locale),
        )
    }
}

/**
 * Una raiz que se repite. No es un boton enmarcado ni un recuadro: es un dato que se
 * puede tocar para buscarlo, y compitiendo con las notas seria una afirmacion sobre el
 * usuario en lugar de una pista.
 */
@Composable
private fun PatternWordRow(word: RecurringWord, onSearch: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onSearch(word.stem) }
            .heightIn(min = TOUCH_TARGET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.notes_pattern_word,
                word.noteCount,
                word.noteCount,
                word.stem,
            ),
            style = ZenTextStyles.Body,
            color = ZenColors.Tertiary,
        )
    }
}

/**
 * Grupo de notas que podria ser un proyecto.
 *
 * Va enmarcado como una nota porque habla de notas, pero con las dos respuestas juntas
 * y del mismo tamano: ni aceptar es la opcion buena ni ignorar es un descarte que haya
 * que justificar.
 */
@Composable
private fun ClusterSuggestionCard(
    cluster: RecurringCluster,
    allNotes: List<Note>,
    onAccept: (RecurringCluster, String) -> Unit,
    onIgnore: (RecurringCluster) -> Unit,
) {
    var accepting by remember(cluster) { mutableStateOf(false) }
    var title by remember(cluster) {
        mutableStateOf(
            allNotes.filter { it.id in cluster.noteIds }
                .maxByOrNull { it.createdAtMillis }
                ?.displayTitle
                .orEmpty(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(ZenSpacing.Hairline, ZenColors.Border)
            .padding(horizontal = ZenSpacing.Medium, vertical = ZenSpacing.Small),
    ) {
        MonoLabel(
            text = pluralStringResource(
                R.plurals.notes_project_suggestion,
                cluster.noteIds.size,
                cluster.noteIds.size,
            ),
            color = ZenColors.Secondary,
        )

        if (accepting) {
            ZenSearchField(
                value = title,
                onValueChange = { title = it },
                placeholder = stringResource(R.string.develop_project_placeholder),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ZenTagButton(
                    text = stringResource(R.string.develop_project_create),
                    onClick = { onAccept(cluster, title) },
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small)) {
                ZenTagButton(
                    text = stringResource(R.string.notes_project_suggestion_accept),
                    onClick = { accepting = true },
                )
                ZenTagButton(
                    text = stringResource(R.string.notes_project_suggestion_ignore),
                    onClick = { onIgnore(cluster) },
                )
            }
        }
    }
}

private const val COLUMNS = 2
private val TOUCH_TARGET = 48.dp
