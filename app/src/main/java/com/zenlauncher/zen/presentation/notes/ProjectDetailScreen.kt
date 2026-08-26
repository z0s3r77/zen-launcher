package com.zenlauncher.zen.presentation.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/** Un proyecto: sus notas y el botón para darlo por terminado. */
@Composable
fun ProjectDetailScreen(
    state: ProjectDetailUiState,
    nowMillis: Long,
    onOpenNote: (String) -> Unit,
    onMarkDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    val project = state.project

    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.project_title),
            right = if (project?.done == true) stringResource(R.string.project_finished) else "",
            onBack = onBack,
        )

        // El proyecto puede desaparecer bajo los pies si se borra desde otro sitio: el
        // flujo reemite antes de que la navegacion cierre la pantalla.
        if (project == null) return@ZenScreen

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(ZenSpacing.Large))
            Text(text = project.title, style = ZenTextStyles.Title, color = ZenColors.Foreground)
            Spacer(Modifier.height(ZenSpacing.Large))

            if (state.notes.isEmpty()) {
                MonoLabel(text = stringResource(R.string.notes_empty))
            } else {
                ZenHairline()
                state.notes.forEach { note ->
                    ProjectNoteRow(note, nowMillis, locale, onOpenNote)
                    ZenHairline()
                }
            }
        }

        if (!project.done) {
            ZenListRow(
                label = stringResource(R.string.project_done),
                labelColor = ZenColors.Danger,
                onClick = onMarkDone,
            )
            ZenHairline()
        }
    }
}

@Composable
private fun ProjectNoteRow(
    note: Note,
    nowMillis: Long,
    locale: Locale,
    onOpenNote: (String) -> Unit,
) {
    ZenListRow(
        label = note.displayTitle,
        onClick = { onOpenNote(note.id) },
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
