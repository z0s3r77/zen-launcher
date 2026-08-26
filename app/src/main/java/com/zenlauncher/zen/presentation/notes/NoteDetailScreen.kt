package com.zenlauncher.zen.presentation.notes

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.notes.NoteStage
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/**
 * Una nota, para releerla.
 *
 * Se lee, no se edita: quien entra aqui viene a recordar que penso, no a corregir la
 * redaccion. Lo unico que se puede hacer, aparte de leer, es borrarla.
 */
@Composable
fun NoteDetailScreen(
    state: NoteDetailUiState,
    nowMillis: Long,
    onOpenLink: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onAccept: (NoteLink) -> Unit,
    onIgnore: (NoteLink) -> Unit,
    onDevelop: () -> Unit,
    onAssignProject: (String) -> Unit,
    onCreateProject: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    val note = state.note

    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.note_title),
            right = note?.let {
                ZenDateFormats.shortDate(it.createdAtMillis, nowMillis, locale)
            }.orEmpty(),
            onBack = onBack,
        )

        // La nota que se estaba mirando puede desaparecer bajo los pies: se borra desde
        // aqui mismo, y el flujo reemite antes de que la navegacion cierre la pantalla.
        // Sin esto, ese fotograma intermedio reventaba.
        if (note == null) return@ZenScreen

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(ZenSpacing.Large))

            // Recien capturada (SEED) es el estado por defecto de cualquier nota:
            // ensenarlo en todas seria ruido. Solo se dice cuando hay algo que contar.
            if (note.stage != NoteStage.SEED) {
                MonoLabel(text = stageLabel(note.stage))
                Spacer(Modifier.height(ZenSpacing.Small))
            }

            // El titulo generado solo se pinta si existe. Sin el, el cuerpo empieza
            // arriba del todo en lugar de dejar un hueco esperando a que la IA llegue.
            note.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(text = title, style = ZenTextStyles.Title, color = ZenColors.Foreground)
                Spacer(Modifier.height(ZenSpacing.Medium))
            }

            Text(
                text = note.body,
                style = ZenTextStyles.Body,
                color = ZenColors.Tertiary,
                modifier = Modifier.fillMaxWidth(),
            )

            // Las imagenes van despues del texto: se apuntan como apoyo de una idea,
            // no como el contenido. Puestas arriba empujarian las palabras fuera de la
            // pantalla y habria que desplazarse para leer lo que se escribio.
            if (state.imagePaths.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.Large))
                state.imagePaths.forEach { path ->
                    NoteImage(absolutePath = path)
                    Spacer(Modifier.height(ZenSpacing.Small))
                }
            }

            if (note.links.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.Large))
                MonoLabel(text = stringResource(R.string.note_links))
                note.links.forEach { link ->
                    ZenHairline()
                    ZenListRow(
                        label = link.value,
                        labelColor = ZenColors.Secondary,
                        onClick = { onOpenLink(link.value) },
                        onClickLabel = stringResource(R.string.note_open_link),
                    )
                }
                ZenHairline()
            }

            // Lo que el usuario ya dijo que va junto: son parte de la nota, y por eso
            // van antes que lo que el indice solo propone.
            if (state.connections.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.XLarge))
                MonoLabel(text = stringResource(R.string.note_connections))
                state.connections.forEach { connected ->
                    ZenHairline()
                    ZenListRow(
                        label = connected.note.displayTitle,
                        labelColor = ZenColors.Secondary,
                        onClick = { onOpenNote(connected.note.id) },
                        trailing = {
                            MonoLabel(
                                text = ZenDateFormats.shortDate(
                                    connected.note.createdAtMillis,
                                    nowMillis,
                                    locale,
                                ),
                            )
                        },
                    )
                }
                ZenHairline()
            }

            // Propuestas del indice. Se preguntan, no se afirman: el rotulo lleva
            // interrogacion porque el indice **no sabe** si estas dos ideas son la
            // misma, solo que se parecen. Quien lo sabe es quien las escribio.
            if (state.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.XLarge))
                MonoLabel(text = stringResource(R.string.note_suggestions))
                state.suggestions.forEach { suggested ->
                    ZenHairline()
                    ZenListRow(
                        label = suggested.note.displayTitle,
                        labelColor = ZenColors.Secondary,
                        onClick = { onOpenNote(suggested.note.id) },
                        trailing = {
                            MonoLabel(
                                text = ZenDateFormats.shortDate(
                                    suggested.note.createdAtMillis,
                                    nowMillis,
                                    locale,
                                ),
                            )
                        },
                    )
                    // Las dos respuestas juntas y del mismo tamano: ni "Conectar" es la
                    // opcion buena ni "Ignorar" es un descarte que haya que justificar.
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small)) {
                        ZenTagButton(
                            text = stringResource(R.string.note_connect),
                            onClick = { onAccept(suggested.link) },
                        )
                        ZenTagButton(
                            text = stringResource(R.string.note_ignore),
                            onClick = { onIgnore(suggested.link) },
                        )
                    }
                }
                ZenHairline()
            }

            if (note.tags.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.XLarge))
                MonoLabel(text = stringResource(R.string.note_tags))
                Spacer(Modifier.height(ZenSpacing.Small))
                MonoLabel(
                    text = note.tags.joinToString("  ·  ").uppercase(locale),
                    color = ZenColors.Secondary,
                    maxLines = 3,
                )
            }

            Spacer(Modifier.height(ZenSpacing.XLarge))
        }

        ZenHairline()
        ZenListRow(
            label = stringResource(R.string.note_develop),
            onClick = onDevelop,
        )
        ZenHairline()
        ProjectRow(
            currentProject = state.currentProject,
            projects = state.projects,
            onAssign = onAssignProject,
            onCreate = onCreateProject,
        )
        ZenHairline()
        ZenListRow(
            label = stringResource(R.string.note_delete),
            // El mismo rojo que "Salir de Zen", y por el mismo motivo: en una lista
            // monocroma es lo unico que evita pulsarla por inercia. Ver [ZenColors.Danger].
            labelColor = ZenColors.Danger,
            onClick = onDelete,
        )
        ZenHairline()
    }
}

/** Nunca se llama con [NoteStage.SEED]: es el estado por defecto y no se pinta. */
@Composable
private fun stageLabel(stage: NoteStage): String {
    val resId = when (stage) {
        NoteStage.DEVELOPED -> R.string.note_stage_developed
        NoteStage.PROJECT -> R.string.note_stage_project
        NoteStage.DONE -> R.string.note_stage_done
        NoteStage.SEED -> return ""
    }
    return stringResource(resId)
}

/**
 * Fila "Proyecto": muestra el actual o "Sin proyecto", y al tocarla abre un selector
 * simple —lista de proyectos existentes mas "Nuevo proyecto"— sin salir de la pantalla.
 */
@Composable
private fun ProjectRow(
    currentProject: Project?,
    projects: List<Project>,
    onAssign: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var creatingNew by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    ZenListRow(
        label = stringResource(R.string.note_project_row),
        onClick = { open = !open },
        trailing = {
            MonoLabel(text = currentProject?.title ?: stringResource(R.string.note_project_none))
        },
    )

    if (!open) return

    if (creatingNew) {
        ZenHairline()
        ZenSearchField(
            value = newTitle,
            onValueChange = { newTitle = it },
            placeholder = stringResource(R.string.develop_project_placeholder),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ZenTagButton(
                text = stringResource(R.string.develop_project_create),
                onClick = {
                    onCreate(newTitle)
                    open = false
                    creatingNew = false
                    newTitle = ""
                },
            )
        }
        return
    }

    projects.forEach { project ->
        ZenHairline()
        ZenListRow(
            label = project.title,
            labelColor = ZenColors.Secondary,
            onClick = {
                onAssign(project.id)
                open = false
            },
        )
    }
    ZenHairline()
    ZenListRow(
        label = stringResource(R.string.note_project_new),
        labelColor = ZenColors.Secondary,
        onClick = { creatingNew = true },
    )
}
