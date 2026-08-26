package com.zenlauncher.zen.presentation.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenMotion
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/**
 * "Desarrollar una idea": conexiones, pregunta central, enfoques y preguntas, todo
 * anclado a datos reales de la propia idea. Cada seccion **solo se pinta si tiene algo
 * real detras**: nada de un hueco vacio esperando a que la IA responda.
 */
@Composable
fun DevelopIdeaScreen(
    state: DevelopIdeaUiState,
    ideaText: String,
    nowMillis: Long,
    onIdeaChange: (String) -> Unit,
    onDictate: () -> Unit,
    onOpenNote: (String) -> Unit,
    onSave: () -> Unit,
    onConvertToProject: (String) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    var showProjectField by remember { mutableStateOf(false) }
    var projectTitle by remember { mutableStateOf("") }

    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.develop_title),
            right = "",
            onBack = onBack,
        )

        Spacer(Modifier.height(ZenSpacing.Medium))

        val selectionColors = TextSelectionColors(
            handleColor = ZenColors.Secondary,
            backgroundColor = ZenColors.Border,
        )

        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
            BasicTextField(
                value = ideaText,
                onValueChange = onIdeaChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = ZenTextStyles.Body.copy(color = ZenColors.Foreground),
                cursorBrush = SolidColor(ZenColors.Secondary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.None,
                ),
                decorationBox = { innerTextField ->
                    if (ideaText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.develop_placeholder),
                            style = ZenTextStyles.Body,
                            color = ZenColors.Dim,
                        )
                    }
                    innerTextField()
                },
            )
        }

        ZenHairline()

        // Dictar solo aparece si este telefono transcribe sin red. Mismo componente que
        // `QuickNoteScreen`, no se duplica logica de voz: la diferencia esta solo en que
        // el texto dictado se une aqui a la idea en vez de a una nota nueva.
        if (state.canDictate) {
            ZenListRow(
                label = stringResource(
                    if (state.listening) R.string.quick_note_dictate_stop
                    else R.string.quick_note_dictate,
                ),
                index = "··",
                labelColor = if (state.listening) ZenColors.Foreground else ZenColors.Secondary,
                onClick = onDictate,
                trailing = {
                    when {
                        state.listening -> MonoLabel(
                            text = stringResource(R.string.quick_note_listening),
                            color = ZenColors.Foreground,
                        )

                        state.micDenied -> MonoLabel(
                            text = stringResource(R.string.quick_note_mic_denied),
                        )

                        else -> Unit
                    }
                },
            )
            ZenHairline()
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Notas parecidas a esta idea, todavia sin ser una nota. Es el mismo indice
            // que el buscador por significado: ver `NoteIndexer.similarTo`.
            if (state.related.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.Large))
                MonoLabel(
                    text = pluralStringResource(
                        R.plurals.develop_connections,
                        state.related.size,
                        state.related.size,
                    ),
                )
                state.related.forEach { related ->
                    ZenHairline()
                    RelatedRow(related, nowMillis, locale, onOpenNote)
                }
                ZenHairline()
            }

            if (state.prompts.centralQuestion != null) {
                Spacer(Modifier.height(ZenSpacing.XLarge))
                MonoLabel(text = stringResource(R.string.develop_central_question))
                Spacer(Modifier.height(ZenSpacing.Small))
                Text(
                    text = state.prompts.centralQuestion,
                    style = ZenTextStyles.Body,
                    color = ZenColors.Foreground,
                )
            }

            if (state.prompts.approaches.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.XLarge))
                MonoLabel(text = stringResource(R.string.develop_approaches))
                Spacer(Modifier.height(ZenSpacing.Small))
                Text(
                    text = state.prompts.approaches.joinToString("  ·  "),
                    style = ZenTextStyles.Body,
                    color = ZenColors.Secondary,
                )
            }

            if (state.prompts.questions.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.XLarge))
                MonoLabel(text = stringResource(R.string.develop_questions))
                state.prompts.questions.forEach { question ->
                    Spacer(Modifier.height(ZenSpacing.Small))
                    Text(text = question, style = ZenTextStyles.Body, color = ZenColors.Foreground)
                }
            }

            Spacer(Modifier.height(ZenSpacing.XLarge))
        }

        // Convertir en proyecto solo aparece con conexiones de verdad detras: menos de
        // tres notas relacionadas no es un patron, es una coincidencia.
        if (state.canConvertToProject) {
            ZenHairline()
            if (showProjectField) {
                Spacer(Modifier.height(ZenSpacing.Small))
                ZenSearchField(
                    value = projectTitle,
                    onValueChange = { projectTitle = it },
                    placeholder = stringResource(R.string.develop_project_placeholder),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ZenTagButton(
                        text = stringResource(R.string.develop_project_create),
                        onClick = { onConvertToProject(projectTitle) },
                    )
                }
                ZenHairline()
            } else {
                ZenListRow(
                    label = stringResource(R.string.develop_convert_project),
                    index = "··",
                    labelColor = ZenColors.Secondary,
                    onClick = { showProjectField = true },
                )
            }
        }

        AnimatedVisibility(
            visible = ideaText.isNotBlank(),
            enter = ZenMotion.RevealEnter,
            exit = ZenMotion.RevealExit,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ZenTagButton(
                    text = stringResource(R.string.develop_save),
                    onClick = onSave,
                )
            }
        }

        ZenHairline()
    }
}

@Composable
private fun RelatedRow(
    note: Note,
    nowMillis: Long,
    locale: Locale,
    onOpenNote: (String) -> Unit,
) {
    ZenListRow(
        label = note.displayTitle,
        labelColor = ZenColors.Secondary,
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
