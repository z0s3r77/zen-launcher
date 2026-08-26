package com.zenlauncher.zen.presentation.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.presentation.components.MonoData
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.home.weatherConditionLabel
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/**
 * El tiempo: lo que hay, de donde sale y como se cambia.
 *
 * Es la salida del glifo de la franja de la pantalla de inicio —ninguna cifra sin
 * salida— y es donde se elige la ciudad. Las dos cosas en la misma pantalla porque son
 * la misma pregunta: "¿de donde es este numero?".
 *
 * Aqui si se escribe la hora de la lectura, y en la franja no: en la home solo caben los
 * grados, pero quien entra a mirar el detalle tiene derecho a saber si el dato es de
 * hace diez minutos o de esta manana.
 */
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    search: PlaceSearchState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onChoose: (WeatherPlace) -> Unit,
    onClearPlace: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        val reading = state.reading
        ZenHeaderStrip(
            left = stringResource(R.string.weather_title),
            right = reading?.let { stringResource(R.string.weather_degrees, it.degrees) }
                ?: stringResource(R.string.weather_no_data_short),
            onBack = onBack,
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "actual") {
                Spacer(Modifier.height(ZenSpacing.Large))
                if (state.place == null) {
                    // Sin ciudad no hay dato ni hay red: se explica en una linea en vez
                    // de dejar la pantalla en blanco.
                    MonoLabel(
                        text = stringResource(R.string.weather_no_place),
                        color = ZenColors.Dim,
                        // Cuatro: con tres, la frase se cortaba en "no se conecta…" y se
                        // perdia justo la promesa que hace. Comprobado en el dispositivo.
                        maxLines = 4,
                    )
                } else {
                    CurrentWeather(state = state, locale = locale, onRefresh = onRefresh)
                }
                Spacer(Modifier.height(ZenSpacing.Large))
                ZenHairline()
            }

            item(key = "buscar") {
                Spacer(Modifier.height(ZenSpacing.Medium))
                MonoLabel(
                    text = stringResource(
                        if (state.place == null) R.string.weather_choose_place
                        else R.string.weather_change_place,
                    ),
                )
                ZenSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.weather_search_placeholder),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // Buscar es una accion y no algo que pase mientras se teclea: cada
                    // letra seria una peticion a la red. Ver [WeatherViewModel].
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
                ZenHairline()
                Spacer(Modifier.height(ZenSpacing.Small))
                Row(horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small)) {
                    ZenTagButton(
                        text = stringResource(R.string.weather_search_action),
                        onClick = onSearch,
                    )
                    if (state.place != null) {
                        ZenTagButton(
                            text = stringResource(R.string.weather_clear_place),
                            onClick = onClearPlace,
                        )
                    }
                }
            }

            if (search.searching) {
                item(key = "buscando") {
                    MonoLabel(
                        text = stringResource(R.string.weather_searching),
                        color = ZenColors.Dim,
                    )
                }
            }

            if (search.empty) {
                item(key = "vacio") {
                    // "No hay resultados" y "no hay red" se dicen juntos a proposito:
                    // desde aqui no se pueden distinguir, y afirmar solo lo primero
                    // seria decirle al usuario que su ciudad no existe.
                    MonoLabel(
                        text = stringResource(R.string.weather_search_empty),
                        color = ZenColors.Dim,
                        maxLines = 3,
                    )
                }
            }

            // Prefijo en la clave: esta lista convive con los elementos fijos de arriba
            // y dos claves iguales en una LazyColumn lanzan excepcion, que aqui es
            // dejar el telefono sin pantalla de inicio.
            items(search.results, key = { "sitio-${it.name}-${it.latitude}-${it.longitude}" }) { place ->
                ZenHairline()
                ZenListRow(
                    label = place.name,
                    onClick = { onChoose(place) },
                    onClickLabel = stringResource(R.string.weather_choose_action),
                    // Lo que se corta en una linea es el pais, que es justo lo que
                    // distingue un Oviedo de otro. Comprobado en el dispositivo: la
                    // busqueda de "Oviedo" devuelve siete de cinco paises distintos.
                    labelMaxLines = 2,
                )
            }

            item(key = "aire") { Spacer(Modifier.height(ZenSpacing.XLarge)) }
        }
    }
}

@Composable
private fun CurrentWeather(
    state: WeatherUiState,
    locale: Locale,
    onRefresh: () -> Unit,
) {
    val place = state.place ?: return
    val reading = state.reading
    Column(Modifier.fillMaxWidth()) {
        MonoLabel(text = place.name)
        Spacer(Modifier.height(ZenSpacing.Small))
        if (reading == null) {
            MonoLabel(
                text = stringResource(
                    // Un dato demasiado viejo y no haber traido ninguno son cosas
                    // distintas y se dicen distinto: en el primer caso hubo red alguna
                    // vez y el numero existio.
                    if (state.stale) R.string.weather_stale else R.string.weather_no_reading,
                ),
                color = ZenColors.Dim,
                maxLines = 2,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.weather_degrees, reading.degrees),
                    style = ZenTextStyles.Figure,
                    color = ZenColors.Foreground,
                )
                reading.condition?.let { condition ->
                    Spacer(Modifier.width(ZenSpacing.Medium))
                    MonoLabel(
                        text = "${condition.glyph}  " +
                            stringResource(weatherConditionLabel(condition)).uppercase(locale),
                        color = ZenColors.Tertiary,
                    )
                }
            }
            Spacer(Modifier.height(ZenSpacing.Small))
            MonoData(
                text = stringResource(
                    R.string.weather_observed_at,
                    ZenDateFormats.time(reading.observedAtMillis),
                ),
                color = ZenColors.Dim,
            )
        }
        Spacer(Modifier.height(ZenSpacing.Medium))
        ZenTagButton(text = stringResource(R.string.weather_refresh), onClick = onRefresh)
    }
}
