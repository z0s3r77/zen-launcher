package com.zenlauncher.zen.presentation.notes

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zenlauncher.zen.R
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * Proyectos: notas que el usuario ha decidido que son lo mismo.
 *
 * Se llega solo si hay al menos uno (ver la fila en `NotesScreen`): esta pantalla nunca
 * es la primera en ensenar "todavia no hay nada".
 */
@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.projects_title),
            right = "%02d".format(state.projects.size),
            onBack = onBack,
        )

        if (state.empty) {
            Spacer(Modifier.height(ZenSpacing.XXLarge))
            MonoLabel(text = stringResource(R.string.projects_empty))
            return@ZenScreen
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.projects, key = { it.project.id }) { row ->
                ZenHairline()
                ZenListRow(
                    label = row.project.title,
                    onClick = { onOpenProject(row.project.id) },
                    trailing = {
                        MonoLabel(
                            text = if (row.project.done) {
                                stringResource(R.string.project_finished)
                            } else {
                                pluralStringResource(R.plurals.project_note_count, row.noteCount, row.noteCount)
                            },
                            color = if (row.project.done) ZenColors.Dim else ZenColors.Secondary,
                        )
                    },
                )
            }
            item { ZenHairline() }
        }
    }
}
