package com.zenlauncher.zen.presentation.apps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.theme.ZenSpacing

@Composable
fun AppDrawerScreen(
    state: AppDrawerUiState,
    onQueryChange: (String) -> Unit,
    onLaunchApp: (InstalledApp) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.drawer_title),
            right = "%02d".format(state.apps.size),
            onBack = onBack,
        )

        ZenSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.drawer_search_placeholder),
        )
        ZenHairline()

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.apps, key = { it.packageName }) { app ->
                ZenListRow(
                    label = app.label,
                    onClick = { onLaunchApp(app) },
                )
                ZenHairline()
            }

            if (state.hiddenByRestriction > 0) {
                item {
                    Spacer(Modifier.height(ZenSpacing.Large))
                    MonoLabel(
                        text = stringResource(
                            R.string.drawer_hidden_notice,
                            state.hiddenByRestriction,
                        ),
                    )
                }
            }
        }
    }
}
