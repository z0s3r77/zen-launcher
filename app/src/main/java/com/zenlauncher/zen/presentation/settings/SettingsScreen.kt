package com.zenlauncher.zen.presentation.settings

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
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.StatusMark
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    isDefaultLauncher: Boolean,
    doubleTapLockEnabled: Boolean,
    nowPlayingEnabled: Boolean,
    onOpenHomeApps: () -> Unit,
    onSetDuration: (ZenDuration) -> Unit,
    onRequestHomeRole: () -> Unit,
    onToggleDoubleTapLock: () -> Unit,
    onToggleNowPlaying: () -> Unit,
    onOpenBatterySaver: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenWeather: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.settings_title),
            // El dato de la franja es la duracion que se usara al pulsar ZEN. Antes era
            // el numero de aplicaciones del inicio, cuando elegirlas se hacia aqui.
            right = "%d MIN".format(state.preferredDuration.wholeMinutes),
            onBack = onBack,
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Spacer(Modifier.height(ZenSpacing.Large))
                MonoLabel(text = stringResource(R.string.settings_system))
                Spacer(Modifier.height(ZenSpacing.Small))
                ZenHairline()

                // Cuando Zen YA es el launcher, esta fila debe seguir siendo pulsable:
                // es la unica salida desde dentro de la aplicacion para devolver la
                // pantalla de inicio al launcher anterior.
                ZenListRow(
                    label = stringResource(
                        if (isDefaultLauncher) R.string.settings_default_launcher_change
                        else R.string.settings_default_launcher,
                    ),
                    labelColor = ZenColors.Secondary,
                    onClick = onRequestHomeRole,
                    trailing = {
                        MonoLabel(
                            text = stringResource(
                                if (isDefaultLauncher) R.string.settings_state_active
                                else R.string.settings_state_inactive,
                            ),
                        )
                    },
                )
                ZenHairline()

                // Ver BatterySaverController: Android no permite activarlo desde una
                // app normal, asi que lo unico honesto es abrir los ajustes.
                ZenListRow(
                    label = stringResource(R.string.settings_battery_saver),
                    labelColor = ZenColors.Secondary,
                    onClick = onOpenBatterySaver,
                    trailing = {
                        MonoLabel(
                            text = stringResource(
                                if (state.batterySaverEnabled) R.string.settings_state_active
                                else R.string.settings_state_inactive,
                            ),
                        )
                    },
                )
                ZenHairline()

                // El tiempo es lo unico de Zen que usa internet, asi que su fila dice
                // en el propio rotulo si esta encendido: sin ciudad no se conecta a nada.
                ZenListRow(
                    label = stringResource(R.string.settings_weather),
                    labelColor = ZenColors.Secondary,
                    onClick = onOpenWeather,
                    trailing = {
                        MonoLabel(
                            text = state.weatherPlaceName
                                ?: stringResource(R.string.settings_weather_none),
                        )
                    },
                )
                ZenHairline()

                ZenListRow(
                    label = stringResource(R.string.settings_double_tap_lock),
                    labelColor = ZenColors.Secondary,
                    onClick = onToggleDoubleTapLock,
                    trailing = {
                        MonoLabel(
                            text = stringResource(
                                if (doubleTapLockEnabled) R.string.settings_state_active
                                else R.string.settings_state_inactive,
                            ),
                        )
                    },
                )
                ZenHairline()
                Spacer(Modifier.height(ZenSpacing.Small))
                MonoLabel(
                    text = stringResource(R.string.settings_double_tap_lock_notice),
                    color = ZenColors.Dim,
                    maxLines = 3,
                )
                Spacer(Modifier.height(ZenSpacing.Medium))
                ZenHairline()

                // El acceso a notificaciones es la unica via de Android para leer que
                // suena. Se ofrece aqui, apagado por defecto y con su letra pequena.
                ZenListRow(
                    label = stringResource(R.string.settings_now_playing),
                    labelColor = ZenColors.Secondary,
                    onClick = onToggleNowPlaying,
                    trailing = {
                        MonoLabel(
                            text = stringResource(
                                if (nowPlayingEnabled) R.string.settings_state_active
                                else R.string.settings_state_inactive,
                            ),
                        )
                    },
                )
                ZenHairline()
                Spacer(Modifier.height(ZenSpacing.Small))
                MonoLabel(
                    text = stringResource(R.string.settings_now_playing_notice),
                    color = ZenColors.Dim,
                    maxLines = 4,
                )
                Spacer(Modifier.height(ZenSpacing.Medium))
                ZenHairline()

                ZenListRow(
                    label = stringResource(R.string.settings_greyscale),
                    labelColor = ZenColors.Secondary,
                    onClick = onOpenAccessibility,
                )
                ZenHairline()
                Spacer(Modifier.height(ZenSpacing.Small))
                MonoLabel(
                    text = stringResource(R.string.settings_system_notice),
                    color = ZenColors.Dim,
                    maxLines = 4,
                )

                Spacer(Modifier.height(ZenSpacing.XXLarge))
                ZenHairline()
                // Solo la puerta y el dato. La lista de todas las aplicaciones colgaba
                // de aqui: para poner el telefono en el inicio habia que reconocerlo
                // entre doscientas filas, y esta pantalla no tenia final.
                ZenListRow(
                    label = stringResource(R.string.settings_home_apps),
                    labelColor = ZenColors.Secondary,
                    onClick = onOpenHomeApps,
                    trailing = {
                        MonoLabel(text = "%02d".format(state.homeAppsCount))
                    },
                )
                ZenHairline()

                Spacer(Modifier.height(ZenSpacing.XXLarge))
                MonoLabel(text = stringResource(R.string.settings_duration))
                Spacer(Modifier.height(ZenSpacing.Small))
                ZenHairline()
            }

            items(ZenDuration.Presets, key = { it.wholeMinutes }) { preset ->
                val selected = preset.wholeMinutes == state.preferredDuration.wholeMinutes
                ZenListRow(
                    label = pluralStringResource(
                        R.plurals.setup_minutes,
                        preset.wholeMinutes,
                        preset.wholeMinutes,
                    ),
                    labelColor = if (selected) ZenColors.Foreground else ZenColors.Secondary,
                    onClick = { onSetDuration(preset) },
                    trailing = { if (selected) StatusMark(active = true) },
                )
                ZenHairline()
            }

            item {
                Spacer(Modifier.height(ZenSpacing.Medium))
            }
        }
    }
}
