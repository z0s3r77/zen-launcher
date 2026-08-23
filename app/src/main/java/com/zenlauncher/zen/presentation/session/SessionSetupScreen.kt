package com.zenlauncher.zen.presentation.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.StatusMark
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

@Composable
fun SessionSetupScreen(
    state: SessionUiState,
    onStart: (ZenDuration) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var customMinutes by remember { mutableStateOf("") }
    val customDuration = ZenDuration.ofMinutesOrNull(customMinutes.toIntOrNull())

    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.setup_title),
            right = stringResource(R.string.setup_header_minutes),
            onBack = onBack,
        )

        Spacer(Modifier.height(ZenSpacing.Large))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            ZenHairline()
            ZenDuration.Presets.forEach { preset ->
                val selected = preset.wholeMinutes == state.preferredDuration.wholeMinutes
                ZenListRow(
                    label = pluralStringResource(
                        R.plurals.setup_minutes,
                        preset.wholeMinutes,
                        preset.wholeMinutes,
                    ),
                    index = "%03d".format(preset.wholeMinutes),
                    labelColor = if (selected) ZenColors.Foreground else ZenColors.Secondary,
                    onClick = { onStart(preset) },
                    trailing = { if (selected) StatusMark(active = true) },
                )
                ZenHairline()
            }

            Spacer(Modifier.height(ZenSpacing.XLarge))
            MonoLabel(text = stringResource(R.string.setup_custom))
            ZenSearchField(
                value = customMinutes,
                onValueChange = { input ->
                    // Solo digitos: el teclado numerico no impide pegar texto.
                    customMinutes = input.filter(Char::isDigit).take(4)
                },
                placeholder = stringResource(
                    R.string.setup_custom_placeholder,
                    ZenDuration.MIN_MINUTES,
                    ZenDuration.MAX_MINUTES,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
            ZenHairline()

            ZenListRow(
                label = stringResource(R.string.setup_start_custom),
                labelColor = if (customDuration != null) ZenColors.Foreground
                else ZenColors.Disabled,
                onClick = customDuration?.let { duration -> { onStart(duration) } },
            )
            ZenHairline()

            Spacer(Modifier.height(ZenSpacing.Large))
            MonoLabel(text = stringResource(R.string.setup_notice), maxLines = 3)
            Spacer(Modifier.height(ZenSpacing.Medium).fillMaxWidth())
        }
    }
}
