package com.zenlauncher.zen.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * Campo de busqueda sin caja, sin icono de lupa y sin boton de borrar: solo una linea
 * de texto sobre el filete que ya separa la cabecera. Cualquier adorno aqui competiria
 * con la lista, que es lo unico que importa.
 */
@Composable
fun ZenSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
) {
    val selectionColors = TextSelectionColors(
        handleColor = ZenColors.Secondary,
        backgroundColor = ZenColors.Border,
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 12.dp),
            textStyle = ZenTextStyles.Body.copy(color = ZenColors.Foreground),
            cursorBrush = SolidColor(ZenColors.Secondary),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = ZenTextStyles.Body,
                        color = ZenColors.Dim,
                    )
                }
                innerTextField()
            },
        )
    }
}
