package com.zenlauncher.zen.presentation.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.StatusMark
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * No se parece a los ajustes de Android a proposito: sin interruptores Material, sin
 * tarjetas y sin iconos de aplicacion. El estado se lee como texto — BLOQUEADA o
 * LIBRE — y el cuadrado ambar solo lo acompana, nunca lo sustituye.
 */
@Composable
fun RestrictedAppsScreen(
    state: RestrictedAppsUiState,
    onQueryChange: (String) -> Unit,
    onToggle: (RestrictedAppRow) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.restricted_title),
            right = "%02d / %02d".format(state.restrictedCount, state.totalCount),
            onBack = onBack,
        )

        ZenSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.restricted_search_placeholder),
        )
        ZenHairline()

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.rows, key = { it.app.packageName }) { row ->
                ZenListRow(
                    label = row.app.label,
                    labelColor = if (row.restricted) ZenColors.Foreground else ZenColors.Muted,
                    onClick = { onToggle(row) },
                    onClickLabel = stringResource(
                        if (row.restricted) R.string.restricted_action_allow
                        else R.string.restricted_action_block,
                    ),
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
                        ) {
                            StatusMark(active = row.restricted)
                            MonoLabel(
                                text = stringResource(
                                    if (row.restricted) R.string.restricted_state_blocked
                                    else R.string.restricted_state_free,
                                ),
                                color = if (row.restricted) ZenColors.Tertiary
                                else ZenColors.Disabled,
                                modifier = Modifier.width(84.dp),
                            )
                        }
                    },
                )
                // El paquete se muestra en pequeno: dos apps pueden llamarse igual.
                Text(
                    text = row.app.packageName,
                    style = ZenTextStyles.MonoIndex,
                    color = ZenColors.Faint,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(ZenSpacing.Small))
                ZenHairline()
            }

            item {
                Spacer(Modifier.height(ZenSpacing.Large))
                MonoLabel(text = stringResource(R.string.restricted_v01_notice), maxLines = 3)
                Spacer(Modifier.height(ZenSpacing.Medium))
            }
        }
    }
}
