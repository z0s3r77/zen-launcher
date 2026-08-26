package com.zenlauncher.zen.presentation.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.scanner.ExportResult
import com.zenlauncher.zen.domain.scanner.ScanFilter
import com.zenlauncher.zen.domain.scanner.ScanPage
import com.zenlauncher.zen.domain.scanner.ScanPhase
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import kotlinx.coroutines.launch

/** Que ocupa el hueco grande. Se turnan; nunca se apilan. */
private enum class ReviewMode { IMAGE, CORNERS, TEXT }

/**
 * Revisar lo escaneado antes de darlo por bueno.
 *
 * El hueco grande tiene **un solo inquilino cada vez**: la hoja, las esquinas o el texto.
 * Es la misma decision que el menu de la pantalla de inicio, y por la misma razon: en un
 * movil, meter tres cosas a la vez obliga a que ninguna se vea entera, y aqui las tres
 * quieren la pantalla completa.
 *
 * Los modos de imagen van en una fila que se desplaza, y **el activo se distingue por el
 * tono del rotulo**, nunca por un color de acento: el ambar esta reservado a las marcas de
 * 6dp. Al lado sigue leyendose cual esta puesto.
 */
@Composable
fun ScanReviewScreen(
    state: ScannerUiState,
    page: ScanPage,
    onQuadChange: (Quad) -> Unit,
    onFilterChange: (ScanFilter) -> Unit,
    onRotate: () -> Unit,
    onRunOcr: () -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    onSaveImage: () -> Unit,
    onSavePdf: () -> Unit,
    onDelete: () -> Unit,
    onAcknowledge: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember(page.id) { mutableStateOf(ReviewMode.IMAGE) }
    val busy = state.phase == ScanPhase.PROCESSING || state.phase == ScanPhase.SAVING

    val index = state.document.pages.indexOfFirst { it.id == page.id } + 1

    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.scanner_review_title),
            right = stringResource(R.string.scanner_page_of, index, state.document.pageCount),
            onBack = onBack,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(ZenColors.Background),
        ) {
            when (mode) {
                ReviewMode.IMAGE -> ScanImage(
                    path = page.renderedPath,
                    revision = page.revision,
                    contentDescription = stringResource(R.string.scanner_page_description, index),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                ReviewMode.CORNERS -> CornerEditor(
                    imagePath = page.originalPath,
                    // El original no se reescribe nunca, asi que su revision es fija: es
                    // justo lo que garantiza que ajustar esquinas parta siempre de la
                    // foto entera y no de un recorte anterior.
                    revision = 0,
                    imageAspect = state.frameAspect,
                    quad = page.quad,
                    onCommit = onQuadChange,
                    modifier = Modifier.fillMaxSize(),
                )

                ReviewMode.TEXT -> RecognizedTextPanel(
                    page = page,
                    running = state.ocrRunning,
                    available = state.ocrAvailable,
                    onRunOcr = onRunOcr,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.height(ZenSpacing.Small))

        StatusLine(state = state, page = page, busy = busy, onAcknowledge = onAcknowledge)

        if (mode == ReviewMode.IMAGE) {
            FilterRow(current = page.filter, enabled = !busy, onFilterChange = onFilterChange)
        }

        ZenHairline()
        Spacer(Modifier.height(ZenSpacing.Small))

        ReviewActions(
            mode = mode,
            busy = busy,
            ocrAvailable = state.ocrAvailable,
            onMode = { mode = it },
            onRotate = onRotate,
            onRetake = onRetake,
            onConfirm = onConfirm,
            onSaveImage = onSaveImage,
            onSavePdf = onSavePdf,
            onDelete = onDelete,
        )
    }
}

/**
 * Una sola linea para lo que hay que decir: que esta pasando, que salio mal o donde quedo
 * guardado. Nunca tres avisos apilados.
 */
@Composable
private fun ColumnScope.StatusLine(
    state: ScannerUiState,
    page: ScanPage,
    busy: Boolean,
    onAcknowledge: () -> Unit,
) {
    val export = state.export
    val text = when {
        busy -> stringResource(R.string.scanner_hint_processing)
        export is ExportResult.Saved ->
            stringResource(R.string.scanner_saved_in, export.location, export.displayName)
        state.error != null -> stringResource(scanErrorLabel(state.error))
        // Solo se avisa de la foto movida cuando no hay nada mas que contar: es un
        // consejo, no un fallo, y el documento esta guardado igualmente.
        page.blurry -> stringResource(R.string.scanner_blurry)
        else -> null
    } ?: return

    Text(
        text = text,
        style = ZenTextStyles.Body,
        color = ZenColors.Secondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZenSpacing.Small),
    )

    if (export != null || state.error != null) {
        ZenTagButton(text = stringResource(R.string.scanner_understood), onClick = onAcknowledge)
    }
}

/**
 * Los cinco modos de imagen.
 *
 * En una fila que se desplaza y no en una retícula: son cinco rotulos cortos, se prueban
 * uno detras de otro y el orden importa —de menos a mas intervencion— porque asi el dedo
 * recorre la escala en lugar de saltar por ella.
 */
@Composable
private fun FilterRow(
    current: ScanFilter,
    enabled: Boolean,
    onFilterChange: (ScanFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = ZenSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (filter in ScanFilter.entries) {
            ZenTagButton(
                text = stringResource(filterLabel(filter)),
                onClick = { if (enabled) onFilterChange(filter) },
                onClickLabel = stringResource(
                    R.string.scanner_filter_label,
                    stringResource(filterLabel(filter)),
                ),
            )
        }
    }

    // El modo puesto se dice con palabras debajo de la fila. Es lo que evita depender del
    // contraste de un boton entre cinco iguales, y ademas lo lee un lector de pantalla.
    MonoLabel(
        text = stringResource(R.string.scanner_filter_current, stringResource(filterLabel(current))),
        color = ZenColors.Foreground,
    )
}

@Composable
private fun ReviewActions(
    mode: ReviewMode,
    busy: Boolean,
    ocrAvailable: Boolean,
    onMode: (ReviewMode) -> Unit,
    onRotate: () -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    onSaveImage: () -> Unit,
    onSavePdf: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
        ) {
            ZenTagButton(
                text = stringResource(
                    if (mode == ReviewMode.CORNERS) {
                        R.string.scanner_corners_done
                    } else {
                        R.string.scanner_corners
                    },
                ),
                onClick = {
                    onMode(if (mode == ReviewMode.CORNERS) ReviewMode.IMAGE else ReviewMode.CORNERS)
                },
                modifier = Modifier.weight(1f),
                stretch = true,
            )
            ZenTagButton(
                text = stringResource(R.string.scanner_rotate),
                onClick = { if (!busy) onRotate() },
                onClickLabel = stringResource(R.string.scanner_rotate_label),
                modifier = Modifier.weight(1f),
                stretch = true,
            )
            // El OCR solo se ofrece si el telefono trae el reconocedor. Lo que no tiene
            // nada detras no se pinta.
            if (ocrAvailable) {
                ZenTagButton(
                    text = stringResource(R.string.scanner_text),
                    onClick = {
                        onMode(if (mode == ReviewMode.TEXT) ReviewMode.IMAGE else ReviewMode.TEXT)
                    },
                    modifier = Modifier.weight(1f),
                    stretch = true,
                )
            }
        }

        Spacer(Modifier.height(ZenSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
        ) {
            ZenTagButton(
                text = stringResource(R.string.scanner_save_image),
                onClick = { if (!busy) onSaveImage() },
                modifier = Modifier.weight(1f),
                stretch = true,
            )
            ZenTagButton(
                text = stringResource(R.string.scanner_save_pdf),
                onClick = { if (!busy) onSavePdf() },
                modifier = Modifier.weight(1f),
                stretch = true,
            )
        }

        Spacer(Modifier.height(ZenSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
        ) {
            ZenTagButton(
                text = stringResource(R.string.scanner_retake),
                onClick = { if (!busy) onRetake() },
                onClickLabel = stringResource(R.string.scanner_retake_label),
                modifier = Modifier.weight(1f),
                stretch = true,
            )
            ZenTagButton(
                text = stringResource(R.string.scanner_next_page),
                onClick = { if (!busy) onConfirm() },
                onClickLabel = stringResource(R.string.scanner_next_page_label),
                modifier = Modifier.weight(1f),
                stretch = true,
            )
        }

        Spacer(Modifier.height(ZenSpacing.Small))
        ZenHairline()
        ZenTagButton(
            text = stringResource(R.string.scanner_delete_page),
            onClick = { if (!busy) onDelete() },
            onClickLabel = stringResource(R.string.scanner_delete_page_label),
            modifier = Modifier.fillMaxWidth(),
            stretch = true,
        )
        Spacer(Modifier.height(ZenSpacing.Medium))
    }
}

/**
 * El texto reconocido: se lee, se selecciona y se copia entero.
 *
 * `SelectionContainer` es lo que hace que se pueda seleccionar a mano con el gesto de
 * siempre, sin inventar ningun control. El boton de copiar existe aparte porque copiar la
 * pagina entera es lo que se quiere nueve de cada diez veces, y hacerlo a mano en un movil
 * son dos arrastres finos sobre un parrafo largo.
 */
@Composable
private fun RecognizedTextPanel(
    page: ScanPage,
    running: Boolean,
    available: Boolean,
    onRunOcr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `LocalClipboard` y no el `LocalClipboardManager` de siempre: el segundo esta
    // obsoleto y el nuevo copia en una funcion suspendida, asi que hace falta un ambito.
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val text = page.text

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        when {
            !available -> Text(
                text = stringResource(R.string.scanner_ocr_unavailable),
                style = ZenTextStyles.Body,
                color = ZenColors.Secondary,
            )

            running -> MonoLabel(text = stringResource(R.string.scanner_ocr_running))

            text == null -> {
                Text(
                    text = stringResource(R.string.scanner_ocr_explain),
                    style = ZenTextStyles.Body,
                    color = ZenColors.Secondary,
                )
                Spacer(Modifier.height(ZenSpacing.Medium))
                ZenTagButton(text = stringResource(R.string.scanner_ocr_run), onClick = onRunOcr)
            }

            text.empty -> Text(
                text = stringResource(R.string.scanner_ocr_empty),
                style = ZenTextStyles.Body,
                color = ZenColors.Secondary,
            )

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
                ) {
                    ZenTagButton(
                        text = stringResource(R.string.scanner_ocr_copy),
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        android.content.ClipData.newPlainText(
                                            CLIP_LABEL,
                                            text.text,
                                        ),
                                    ),
                                )
                            }
                        },
                        onClickLabel = stringResource(R.string.scanner_ocr_copy_label),
                    )
                    ZenTagButton(
                        text = stringResource(R.string.scanner_ocr_again),
                        onClick = onRunOcr,
                    )
                }
                Spacer(Modifier.height(ZenSpacing.Medium))
                SelectionContainer {
                    Text(
                        text = text.text,
                        style = ZenTextStyles.Body,
                        color = ZenColors.Foreground,
                    )
                }
                Spacer(Modifier.height(ZenSpacing.XXLarge))
            }
        }
    }
}

/** Lo que ve el usuario si su teclado le ensena el historial del portapapeles. */
private const val CLIP_LABEL = "Escaneo"

internal fun filterLabel(filter: ScanFilter): Int = when (filter) {
    ScanFilter.ORIGINAL -> R.string.scanner_filter_original
    ScanFilter.DOCUMENT -> R.string.scanner_filter_document
    ScanFilter.BLACK_AND_WHITE -> R.string.scanner_filter_bw
    ScanFilter.HIGH_CONTRAST -> R.string.scanner_filter_contrast
    ScanFilter.GRAYSCALE -> R.string.scanner_filter_gray
}
