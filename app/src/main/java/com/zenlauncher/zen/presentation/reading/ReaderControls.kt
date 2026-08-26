package com.zenlauncher.zen.presentation.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.BookChapter
import com.zenlauncher.zen.domain.reading.Bookmark
import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.domain.reading.ReadingHit
import com.zenlauncher.zen.domain.reading.ReaderPage
import com.zenlauncher.zen.domain.reading.ReadingProgress
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenSearchField
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/** Que hay abierto encima del texto. Solo una cosa a la vez. */
enum class ReaderPanel { NONE, CONTROLS, INDEX, SEARCH, MARKS }

/**
 * Los mandos del lector, sobre el texto y solo mientras se piden.
 *
 * Aparecen al tocar la pantalla y se van al volver a tocarla, que es como funciona
 * cualquier lector: mientras se lee no hay nada, y cuando hace falta algo esta todo a un
 * toque. Van pegados abajo, en la zona del pulgar, y **no empujan el texto**: se dibujan
 * encima, asi que la linea que se estaba leyendo sigue donde estaba al cerrarlos.
 *
 * Los tres ajustes de forma —letra, interlinea, margenes— son escalones con dos botones
 * y una fila de puntos, no barras deslizantes. Una barra en un movil se arrastra con el
 * dedo tapando justo lo que se esta ajustando, y ademas obliga a inventar un valor
 * continuo donde solo hay siete pasos utiles.
 */
@Composable
fun ReaderControlPanel(
    visible: Boolean,
    panel: ReaderPanel,
    settings: ReadingSettings,
    chapters: List<BookChapter>,
    pageStops: List<BookBlock>,
    hits: List<ReadingHit>,
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    query: String,
    onOpenPanel: (ReaderPanel) -> Unit,
    onTextStep: (Int) -> Unit,
    onLeadingStep: (Int) -> Unit,
    onMarginStep: (Int) -> Unit,
    onToggleSerif: () -> Unit,
    onQueryChange: (String) -> Unit,
    onJumpTo: (ReadingPosition) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Fondo opaco: sin el, el texto del libro se leeria a traves de los mandos.
            .background(ZenColors.Background)
            .padding(horizontal = ZenSpacing.ScreenHorizontal),
    ) {
        if (panel != ReaderPanel.NONE) ZenHairline(color = ZenColors.Border)

        when (panel) {
            ReaderPanel.CONTROLS -> FormatControls(
                settings = settings,
                onTextStep = onTextStep,
                onLeadingStep = onLeadingStep,
                onMarginStep = onMarginStep,
                onToggleSerif = onToggleSerif,
                onDelete = onDelete,
            )
            ReaderPanel.INDEX -> IndexPanel(
                chapters = chapters,
                pageStops = pageStops,
                onJumpTo = onJumpTo,
            )
            ReaderPanel.SEARCH -> SearchPanel(
                query = query,
                hits = hits,
                onQueryChange = onQueryChange,
                onJumpTo = onJumpTo,
            )
            ReaderPanel.MARKS -> MarksPanel(
                bookmarks = bookmarks,
                highlights = highlights,
                onJumpTo = onJumpTo,
                onDeleteBookmark = onDeleteBookmark,
                onDeleteHighlight = onDeleteHighlight,
            )
            ReaderPanel.NONE -> Unit
        }

        ZenHairline()
        PanelTabs(panel = panel, onOpenPanel = onOpenPanel)
    }
}

/** Las tres puertas: ajustar, indice y buscar. La abierta se lee encendida. */
@Composable
private fun PanelTabs(panel: ReaderPanel, onOpenPanel: (ReaderPanel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Tab(R.string.reader_format, panel == ReaderPanel.CONTROLS) {
            onOpenPanel(ReaderPanel.CONTROLS)
        }
        Tab(R.string.reader_index, panel == ReaderPanel.INDEX) {
            onOpenPanel(ReaderPanel.INDEX)
        }
        Tab(R.string.reader_search, panel == ReaderPanel.SEARCH) {
            onOpenPanel(ReaderPanel.SEARCH)
        }
        Tab(R.string.reader_marks, panel == ReaderPanel.MARKS) {
            onOpenPanel(ReaderPanel.MARKS)
        }
    }
}

@Composable
private fun Tab(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = TOUCH_TARGET)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = ZenSpacing.Small),
        contentAlignment = Alignment.Center,
    ) {
        // Encendido con el tono del texto principal, no con color: es la misma regla que
        // usa la fila "Menú" de la home para decir que esta abierta.
        MonoLabel(
            text = stringResource(labelRes),
            color = if (selected) ZenColors.Foreground else ZenColors.Muted,
        )
    }
}

/** Tamano de letra, interlineado, margenes y tipografia. */
@Composable
private fun FormatControls(
    settings: ReadingSettings,
    onTextStep: (Int) -> Unit,
    onLeadingStep: (Int) -> Unit,
    onMarginStep: (Int) -> Unit,
    onToggleSerif: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepRow(
            label = stringResource(R.string.reader_text_size),
            step = settings.textStep,
            steps = ReadingSettings.TEXT_STEPS,
            onStep = onTextStep,
        )
        StepRow(
            label = stringResource(R.string.reader_leading),
            step = settings.leadingStep,
            steps = ReadingSettings.LEADING_STEPS,
            onStep = onLeadingStep,
        )
        StepRow(
            label = stringResource(R.string.reader_margins),
            step = settings.marginStep,
            steps = ReadingSettings.MARGIN_STEPS,
            onStep = onMarginStep,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TOUCH_TARGET)
                .clickable(role = Role.Button, onClick = onToggleSerif),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLabel(text = stringResource(R.string.reader_font), color = ZenColors.Muted)
            MonoLabel(
                text = stringResource(
                    if (settings.serif) R.string.reader_font_serif else R.string.reader_font_sans,
                ),
                color = ZenColors.Foreground,
            )
        }

        ZenHairline()
        // Quitar el libro vive aqui y no en la biblioteca: es la pantalla del libro, que
        // es donde Zen pone siempre el borrado (ver la pantalla de una nota). En la lista
        // seria un control de borrado al lado de cada fila, a un dedo de abrirlo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TOUCH_TARGET)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.reader_delete_label),
                    onClick = onDelete,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sin el rojo de "Salir de Zen": ese tono tiene una sola fila en toda la
            // aplicacion a proposito, y quitar un libro que se puede volver a importar
            // no es lo mismo que quedarse sin pantalla de inicio.
            MonoLabel(text = stringResource(R.string.reader_delete), color = ZenColors.Tertiary)
        }
    }
}

/**
 * Un ajuste de escalones: menos, los puntos y mas.
 *
 * Los puntos son texto monoespaciado, igual que la barra de progreso de la biblioteca:
 * asi el estado se lee tal cual, sin depender de ver cuantos circulos hay encendidos.
 */
@Composable
private fun StepRow(
    label: String,
    step: Int,
    steps: Int,
    onStep: (Int) -> Unit,
) {
    val current = step.coerceIn(0, steps)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = TOUCH_TARGET),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonoLabel(text = label, color = ZenColors.Muted, modifier = Modifier.weight(1f))

        StepButton(
            glyph = "−",
            description = stringResource(R.string.reader_decrease, label),
            enabled = current > 0,
            onClick = { onStep(current - 1) },
        )
        MonoLabel(
            text = FILLED.repeat(current + 1) + EMPTY.repeat(steps - current),
            color = ZenColors.Secondary,
            modifier = Modifier.semantics {
                contentDescription = "${current + 1} / ${steps + 1}"
            },
        )
        StepButton(
            glyph = "+",
            description = stringResource(R.string.reader_increase, label),
            enabled = current < steps,
            onClick = { onStep(current + 1) },
        )
    }
}

@Composable
private fun StepButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(TOUCH_TARGET)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = ZenTextStyles.MonoData,
            // En el extremo se apaga en lugar de desaparecer: un boton que se va deja un
            // hueco y mueve de sitio los de al lado justo cuando el dedo va a repetir.
            color = if (enabled) ZenColors.Foreground else ZenColors.Disabled,
        )
    }
}

/**
 * El indice del libro.
 *
 * Si no hay —ni indice impreso ni titulos detectables en el cuerpo— se ofrecen saltos
 * por pagina. Un libro siempre se puede recorrer; lo que cambia es con cuanta precision.
 */
@Composable
private fun IndexPanel(
    chapters: List<BookChapter>,
    pageStops: List<BookBlock>,
    onJumpTo: (ReadingPosition) -> Unit,
) {
    if (chapters.isEmpty() && pageStops.isEmpty()) {
        Column(Modifier.padding(vertical = ZenSpacing.Medium)) {
            MonoLabel(text = stringResource(R.string.reader_no_index))
        }
        return
    }

    val jumpLabel = stringResource(R.string.reader_jump_label)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = PANEL_MAX_HEIGHT),
        contentPadding = PaddingValues(vertical = ZenSpacing.Small),
    ) {
        if (chapters.isEmpty()) {
            item {
                Column(Modifier.padding(vertical = ZenSpacing.Small)) {
                    MonoLabel(text = stringResource(R.string.reader_no_index_pages))
                }
            }
        }
        // Claves con prefijo: las dos secciones de esta lista se numeran por su cuenta y
        // con claves repetidas Compose lanza excepcion (ver `NotesScreen`).
        items(items = chapters, key = { "cap-${it.blockIndex}" }) { chapter ->
            Text(
                text = chapter.title,
                style = ZenTextStyles.Body,
                color = if (chapter.level <= 1) ZenColors.Foreground else ZenColors.Secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = jumpLabel,
                        onClick = { onJumpTo(ReadingPosition(chapter.blockIndex)) },
                    )
                    .heightIn(min = TOUCH_TARGET)
                    // La jerarquia se ve con la sangria, no con el tamano: un titulo de
                    // nivel 3 en cuerpo pequeno seria ilegible en un panel de mandos.
                    .padding(
                        start = INDENT * (chapter.level - 1).coerceAtLeast(0),
                        top = ZenSpacing.Small,
                        bottom = ZenSpacing.Small,
                    ),
            )
        }
        items(items = pageStops, key = { "pag-${it.index}" }) { block ->
            Text(
                text = stringResource(R.string.reader_page, block.page + 1),
                style = ZenTextStyles.Body,
                color = ZenColors.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = jumpLabel,
                        onClick = { onJumpTo(ReadingPosition(block.index)) },
                    )
                    .heightIn(min = TOUCH_TARGET)
                    .padding(vertical = ZenSpacing.Small),
            )
        }
    }
}

/** Buscar dentro del libro: literal, sin conexion y sobre el texto ya cargado. */
@Composable
private fun SearchPanel(
    query: String,
    hits: List<ReadingHit>,
    onQueryChange: (String) -> Unit,
    onJumpTo: (ReadingPosition) -> Unit,
) {
    val jumpLabel = stringResource(R.string.reader_jump_label)

    Column(Modifier.fillMaxWidth()) {
        ZenSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.reader_search_placeholder),
        )

        if (query.isNotBlank() && hits.isEmpty()) {
            MonoLabel(text = stringResource(R.string.reader_no_hits))
            Spacer(Modifier.height(ZenSpacing.Small))
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = PANEL_MAX_HEIGHT),
            contentPadding = PaddingValues(bottom = ZenSpacing.Small),
        ) {
            items(items = hits, key = { it.blockIndex }) { hit ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = jumpLabel,
                            onClick = { onJumpTo(ReadingPosition(hit.blockIndex)) },
                        )
                        .padding(vertical = ZenSpacing.Small),
                ) {
                    MonoLabel(text = stringResource(R.string.reader_page, hit.page + 1))
                    Spacer(Modifier.height(ZenSpacing.Base))
                    Text(
                        text = hit.snippet,
                        style = ZenTextStyles.Body,
                        color = ZenColors.Secondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


/**
 * Mis marcas en este libro: las de pagina y lo subrayado.
 *
 * Las dos cosas en un solo panel porque las dos responden a la misma pregunta —"¿donde
 * dejé algo?"— y separarlas obligaria a mirar en dos sitios para encontrar una cosa que
 * no recuerdas si marcaste o subrayaste.
 *
 * Claves con prefijo: son dos secciones en la misma lista perezosa y con claves repetidas
 * Compose lanza excepcion (ver `NotesScreen`). Aqui eso deja el telefono sin pantalla de
 * inicio si el libro se abre desde la home.
 */
@Composable
private fun MarksPanel(
    bookmarks: List<Bookmark>,
    highlights: List<Highlight>,
    onJumpTo: (ReadingPosition) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
) {
    if (bookmarks.isEmpty() && highlights.isEmpty()) {
        Column(Modifier.padding(vertical = ZenSpacing.Medium)) {
            MonoLabel(text = stringResource(R.string.reader_no_marks))
        }
        return
    }

    val jumpLabel = stringResource(R.string.reader_jump_label)
    val removeLabel = stringResource(R.string.reader_mark_remove)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = PANEL_MAX_HEIGHT),
        contentPadding = PaddingValues(vertical = ZenSpacing.Small),
    ) {
        if (bookmarks.isNotEmpty()) {
            item(key = "cab-marcas") {
                MonoLabel(
                    text = stringResource(R.string.reader_marks_pages),
                    modifier = Modifier.padding(vertical = ZenSpacing.Small),
                )
            }
        }
        items(items = bookmarks, key = { "marca-${it.id}" }) { bookmark ->
            MarkRow(
                label = stringResource(R.string.reader_page, bookmark.page + 1),
                text = bookmark.snippet,
                jumpLabel = jumpLabel,
                removeLabel = removeLabel,
                onJump = { onJumpTo(bookmark.position) },
                onRemove = { onDeleteBookmark(bookmark.id) },
            )
        }

        if (highlights.isNotEmpty()) {
            item(key = "cab-subrayados") {
                MonoLabel(
                    text = stringResource(R.string.reader_marks_highlights),
                    modifier = Modifier.padding(vertical = ZenSpacing.Small),
                )
            }
        }
        items(items = highlights, key = { "sub-${it.id}" }) { highlight ->
            MarkRow(
                label = stringResource(R.string.reader_page, highlight.page + 1),
                text = highlight.text,
                // La nota se lee **debajo** del fragmento, no en lugar de el: la gracia
                // de repasar es ver que dijo el libro y que dijiste tu, en ese orden.
                note = highlight.note,
                jumpLabel = jumpLabel,
                removeLabel = removeLabel,
                onJump = { onJumpTo(highlight.position) },
                onRemove = { onDeleteHighlight(highlight.id) },
            )
        }
    }
}

@Composable
private fun MarkRow(
    label: String,
    text: String,
    jumpLabel: String,
    removeLabel: String,
    onJump: () -> Unit,
    onRemove: () -> Unit,
    note: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ZenSpacing.Small),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = jumpLabel, onClick = onJump),
        ) {
            MonoLabel(text = label)
            Spacer(Modifier.height(ZenSpacing.Base))
            Text(
                text = text,
                style = ZenTextStyles.Body,
                color = ZenColors.Secondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!note.isNullOrBlank()) {
                Spacer(Modifier.height(ZenSpacing.XSmall))
                Text(
                    text = note,
                    style = ZenTextStyles.Body,
                    color = ZenColors.Foreground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(TOUCH_TARGET)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = removeLabel },
            contentAlignment = Alignment.Center,
        ) {
            MonoLabel(text = "\u00d7", color = ZenColors.Muted)
        }
    }
}

/**
 * La barra de abajo mientras se lee: pasar hoja y donde estas.
 *
 * Es el unico adorno permanente del lector, y es un **instrumento**, no un indicador: sin
 * el, pasar pagina seria un gesto invisible, que es lo que Zen no admite. De paso dice el
 * capitulo, la pagina y el porcentaje, asi que no hace falta abrir nada para saber por
 * donde vas.
 */
@Composable
fun ReaderPageBar(
    page: ReaderPage,
    blocks: List<BookBlock>,
    blockCount: Int,
    pageCount: Int,
    chapters: List<BookChapter>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blockIndex = page.fragments.firstOrNull()?.blockIndex ?: 0
    val pageNumber = blocks.getOrNull(blockIndex)?.page ?: 0
    val chapter = ReadingProgress.chapterAt(blockIndex, chapters)
    val percent = ReadingProgress.percent(blockIndex, blockCount)
    val atStart = page.start <= ReadingPosition.Start
    val atEnd = page.end.blockIndex >= blocks.size

    Column(modifier = modifier.fillMaxWidth()) {
        ZenHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZenSpacing.ScreenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TurnButton(
                glyph = "\u2039",
                description = stringResource(R.string.reader_previous_page),
                enabled = !atStart,
                onClick = onPrevious,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                chapter?.let {
                    Text(
                        text = it.title,
                        style = ZenTextStyles.Body,
                        color = ZenColors.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MonoLabel(
                    text = stringResource(
                        R.string.reader_position,
                        pageNumber + 1,
                        pageCount,
                        percent,
                    ),
                    color = ZenColors.Secondary,
                )
            }
            TurnButton(
                glyph = "\u203a",
                description = stringResource(R.string.reader_next_page),
                enabled = !atEnd,
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun TurnButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(TURN_TARGET)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = ZenTextStyles.Title,
            // En el extremo se apaga en vez de desaparecer: un boton que se va deja un
            // hueco y mueve de sitio al de al lado justo cuando el dedo va a repetir.
            color = if (enabled) ZenColors.Foreground else ZenColors.Disabled,
        )
    }
}

/**
 * Lo que se puede hacer con la frase que acabas de senalar.
 *
 * Sustituye a la barra de pagina en lugar de ponerse encima: mientras decides que hacer
 * con un fragmento no estas pasando hojas, y dos filas de botones a la vez son dos sitios
 * donde mirar.
 */
@Composable
fun ReaderSelectionBar(
    selection: ReaderSelection,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ZenHairline(color = ZenColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZenSpacing.ScreenHorizontal, vertical = ZenSpacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sobre algo ya subrayado, el mismo sitio quita en vez de volver a subrayar:
            // subrayar dos veces lo mismo no significa nada.
            BarAction(
                labelRes = if (selection.editing) {
                    R.string.reader_highlight_remove
                } else {
                    R.string.reader_highlight
                },
                onClick = onHighlight,
                strong = true,
            )
            BarAction(labelRes = R.string.reader_note, onClick = onNote)
            BarAction(labelRes = R.string.reader_extend, onClick = onExtend)
            BarAction(labelRes = R.string.reader_cancel, onClick = onCancel)
        }
    }
}

/** Escribir la nota de un fragmento. */
@Composable
fun ReaderNoteBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZenSpacing.ScreenHorizontal),
    ) {
        ZenHairline(color = ZenColors.Border)
        ZenSearchField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = stringResource(R.string.reader_note_placeholder),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = ZenSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Medium),
        ) {
            BarAction(labelRes = R.string.reader_save, onClick = onSave, strong = true)
            BarAction(labelRes = R.string.reader_cancel, onClick = onCancel)
        }
    }
}

@Composable
private fun BarAction(labelRes: Int, onClick: () -> Unit, strong: Boolean = false) {
    Box(
        modifier = Modifier
            .heightIn(min = TOUCH_TARGET)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(
            text = stringResource(labelRes),
            color = if (strong) ZenColors.Foreground else ZenColors.Muted,
        )
    }
}

/** El glifo de pasar hoja necesita mas caja que un rotulo de 10sp. */
private val TURN_TARGET = 56.dp

private val TOUCH_TARGET = 48.dp
private val INDENT = 18.dp

/**
 * Lo que puede ocupar un panel como mucho.
 *
 * Acotado para que el indice de un libro con doscientas entradas no se coma la pantalla
 * entera: los mandos van encima del texto, y si lo tapan del todo se pierde la
 * referencia de donde estabas al abrirlos.
 */
private val PANEL_MAX_HEIGHT = 280.dp

private const val FILLED = "▪"
private const val EMPTY = "·"
