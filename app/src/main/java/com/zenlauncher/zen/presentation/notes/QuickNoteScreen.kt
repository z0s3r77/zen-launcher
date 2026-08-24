package com.zenlauncher.zen.presentation.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.zenlauncher.zen.R
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenMotion
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * Captura.
 *
 * La pantalla entera es el campo de texto, con el cursor puesto y el teclado abierto al
 * llegar: al entrar aqui ya se sabe a que se viene, y pedir un toque mas para empezar a
 * escribir es un toque de mas cuando lo que hay es una idea a punto de olvidarse.
 *
 * Un solo boton, y **solo cuando hay algo que guardar**. Un "Guardar" apagado sobre una
 * nota en blanco es un control que se ve y no funciona; la salida sin guardar ya existe
 * y es la flecha de volver, la misma de todas las pantallas.
 */
@Composable
fun QuickNoteScreen(
    state: QuickNoteUiState,
    onTextChange: (String) -> Unit,
    onDictate: () -> Unit,
    onPickImage: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Guardar devuelve a la pantalla de inicio, no aqui: capturar, guardar y fuera. La
    // salida la dispara el estado y no el propio boton para que sea el guardado quien
    // manda, no el toque.
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.quick_note_title),
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
                value = state.text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    // Se queda con todo el alto sobrante para que el boton no suba y
                    // baje segun crece el texto: un control que se mueve mientras se
                    // escribe se toca sin querer.
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .focusRequester(focusRequester),
                textStyle = ZenTextStyles.Body.copy(color = ZenColors.Foreground),
                cursorBrush = SolidColor(ZenColors.Secondary),
                keyboardOptions = KeyboardOptions(
                    // Una nota son frases, no un termino de busqueda: mayuscula al
                    // empezar cada una y salto de linea en vez de "buscar".
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.None,
                ),
                decorationBox = { innerTextField ->
                    if (state.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.quick_note_placeholder),
                            style = ZenTextStyles.Body,
                            color = ZenColors.Dim,
                        )
                    }
                    innerTextField()
                },
            )
        }

        ZenHairline()
        // Dictar solo aparece si este telefono transcribe sin red: lo que no tiene nada
        // detras no se pinta, y un "Dictar" que al tocarlo dijera que no se puede seria
        // un control que existe para negarse. Ver [Dictation.available].
        if (state.canDictate) {
            ZenListRow(
                label = stringResource(
                    if (state.listening) R.string.quick_note_dictate_stop
                    else R.string.quick_note_dictate,
                ),
                index = "··",
                // Encendido mientras escucha: es la misma fila en los dos estados, y
                // sin esto nada distinguiria "puedes dictar" de "te esta oyendo".
                labelColor = if (state.listening) ZenColors.Foreground else ZenColors.Secondary,
                onClick = onDictate,
                trailing = {
                    // El estado se lee como texto, nunca solo por el color. Denegar el
                    // microfono se dice aqui y no con un aviso que interrumpa.
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

        // Adjuntar una imagen es la unica fila de esta pantalla. No hay "adjuntar
        // enlace": los enlaces se reconocen solos en el texto al guardar, porque un
        // enlace siempre llega pegado y pedir un toque para clasificarlo seria la
        // friccion que la captura rapida existe para quitar. Ver [LinkExtractor].
        ZenListRow(
            label = stringResource(R.string.quick_note_image),
            index = "··",
            labelColor = ZenColors.Secondary,
            onClick = onPickImage,
            trailing = {
                // El recuento solo existe si hay algo detras: un "00" permanente es
                // ruido con forma de dato.
                if (state.images.isNotEmpty()) {
                    MonoLabel(text = stringResource(R.string.quick_note_images, state.images.size))
                }
            },
        )

        // Aparece al escribir la primera letra en lugar de estar siempre ahi apagado:
        // mismo criterio que el mando del reproductor, que solo existe si hay algo que
        // mandar. Ver `HomeUiState.mediaVisible`.
        AnimatedVisibility(
            visible = state.canSave,
            enter = ZenMotion.RevealEnter,
            exit = ZenMotion.RevealExit,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ZenTagButton(
                    text = stringResource(R.string.quick_note_save),
                    onClick = onSave,
                )
            }
        }

        ZenHairline()
    }
}
