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
import com.zenlauncher.zen.presentation.components.StatusMark
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * Elegir las aplicaciones de la pantalla de inicio.
 *
 * Dos bloques y nada mas: **lo que ya esta puesto**, numerado igual que la reticula
 * —01 arriba a la izquierda— y donde tocar quita; y **anadir**, que es un buscador y
 * los resultados de lo que se escriba. Sin escribir no hay lista: ver
 * [HomeAppsViewModel] para por que.
 *
 * El estado se lee como texto en las dos partes —QUITAR en lo elegido, AÑADIR en lo
 * que no— y el cuadrado ambar solo lo acompana.
 */
@Composable
fun HomeAppsScreen(
    state: HomeAppsUiState,
    onQueryChange: (String) -> Unit,
    onAdd: (InstalledApp) -> Unit,
    onRemove: (InstalledApp) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.home_apps_title),
            // Solo la cuenta: no hay contra que contarla desde que la home se desplaza.
            right = "%02d".format(state.chosenCount),
            onBack = onBack,
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Spacer(Modifier.height(ZenSpacing.Large))
                MonoLabel(text = stringResource(R.string.home_apps_chosen))
                Spacer(Modifier.height(ZenSpacing.Small))
                ZenHairline()
            }

            // La clave lleva de que bloque es: una aplicacion ya puesta que ademas
            // coincide con la busqueda sale en los dos, y con el paquete a secas
            // LazyColumn se encontraba la misma clave dos veces y reventaba la pantalla.
            items(state.chosen, key = { "elegida:${it.app.packageName}" }) { row ->
                ZenListRow(
                    label = row.app.label,
                    // El mismo numero que lleva la celda en la reticula: asi se sabe
                    // que se esta moviendo sin tener que volver a la home a mirarlo.
                    index = "%02d".format(row.position + 1),
                    onClick = { onRemove(row.app) },
                    onClickLabel = stringResource(R.string.home_apps_action_remove),
                    trailing = {
                        MonoLabel(text = stringResource(R.string.home_apps_state_chosen))
                    },
                )
                ZenHairline()
            }

            if (state.chosen.isEmpty()) {
                item {
                    // Sin ninguna elegida la home no aparece vacia: usa las esenciales.
                    // Decirlo aqui evita que el usuario crea que ha roto algo.
                    Spacer(Modifier.height(ZenSpacing.Small))
                    MonoLabel(
                        text = stringResource(R.string.home_apps_empty),
                        color = ZenColors.Dim,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(ZenSpacing.Small))
                    ZenHairline()
                }
            }

            item {
                Spacer(Modifier.height(ZenSpacing.XXLarge))
                MonoLabel(text = stringResource(R.string.home_apps_add))
                ZenSearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.home_apps_search_placeholder),
                )
                ZenHairline()
            }

            items(state.candidates, key = { "candidata:${it.app.packageName}" }) { row ->
                // Si ya esta puesta se ve, pero no se puede volver a anadir: repetirla
                // en la reticula seria un hueco perdido.
                val addable = !row.chosen
                ZenListRow(
                    label = row.app.label,
                    labelColor = when {
                        row.chosen -> ZenColors.Foreground
                        addable -> ZenColors.Muted
                        else -> ZenColors.Disabled
                    },
                    onClick = if (addable) ({ onAdd(row.app) }) else null,
                    onClickLabel = stringResource(R.string.home_apps_action_add),
                    trailing = { StatusMark(active = row.chosen) },
                )
                ZenHairline()
            }

            item {
                Spacer(Modifier.height(ZenSpacing.Large))
                val notice = when {
                    // El orden importa: buscando y sin resultados, lo util es decir que
                    // no hay nada con ese nombre, no recordar el tope.
                    state.searching && state.candidates.isEmpty() ->
                        stringResource(R.string.home_apps_no_results)

                    !state.searching ->
                        stringResource(R.string.home_apps_search_hint)

                    else -> stringResource(R.string.home_apps_notice)
                }
                MonoLabel(text = notice, color = ZenColors.Dim, maxLines = 3)
                Spacer(Modifier.height(ZenSpacing.Medium))
            }
        }
    }
}
