package com.zenlauncher.zen.presentation.reading

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.reading.Bookmarks
import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.HighlightSpans
import com.zenlauncher.zen.domain.reading.Paginator
import com.zenlauncher.zen.domain.reading.ReaderPage
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.domain.reading.Sentences
import com.zenlauncher.zen.domain.reading.TextSpan
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * El lector.
 *
 * **No se renderiza el PDF.** Lo que se pinta es el texto ya reconstruido en parrafos
 * (ver `BookBuilder`), repartido en hojas medidas contra la pantalla de verdad (ver
 * `Paginator`). Un visor de PDF en un movil obliga a hacer zoom y a arrastrar en dos ejes
 * para leer una columna pensada para un A4.
 *
 * **Mientras se lee no hay nada mas que el libro**: ni franja de cabecera, ni barra de
 * pagina, ni mandos. La pagina ocupa la pantalla entera. Al tocar el centro aparece todo
 * —volver, marcar, ajustes, indice, buscar, marcas y los botones de pasar hoja— y al
 * volver a tocarlo se va. Es la unica pantalla de Zen que esconde su propia salida, y lo
 * hace porque aqui el contenido **es** la pantalla; se sale igualmente arrastrando desde
 * el borde, que es el gesto de volver de toda la aplicacion.
 *
 * **Se pasa pagina, y se pasa tocando, no deslizando.** No es una preferencia: `ZenScreen`
 * ya usa el arrastre horizontal para volver, y el hijo consumiria el gesto dejando el
 * lector sin salida. Los tercios laterales pasan hoja y el de en medio despierta la
 * pantalla; con la pantalla despierta, los botones de pasar hoja tambien estan a la vista.
 *
 * Los mandos **se dibujan encima** del texto en lugar de empujarlo. Asi la hoja se mide
 * siempre contra la pantalla entera y la linea que estabas leyendo no se mueve de sitio
 * al abrirlos y cerrarlos.
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    /** Lo escrito en el buscador, sin pasar por el filtro. Ver `ReaderViewModel.query`. */
    query: String,
    onQueryChange: (String) -> Unit,
    onPositionVisible: (ReadingPosition) -> Unit,
    onTextStep: (Int) -> Unit,
    onLeadingStep: (Int) -> Unit,
    onMarginStep: (Int) -> Unit,
    onToggleSerif: () -> Unit,
    onAddBookmark: (position: ReadingPosition, snippet: String, page: Int) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onHighlight: (
        blockIndex: Int,
        start: Int,
        end: Int,
        text: String,
        page: Int,
        note: String?,
    ) -> Unit,
    onSetNote: (Highlight, String?) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La pantalla nace dormida: se entra a un libro para leer, no para mirar botones.
    var awake by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf(ReaderPanel.NONE) }

    // La posicion va en dos enteros y no en un objeto porque `rememberSaveable` guarda
    // tipos del bundle. -1 significa "todavia sin restaurar del libro".
    var blockIndex by rememberSaveable { mutableIntStateOf(-1) }
    var charOffset by rememberSaveable { mutableIntStateOf(0) }

    var selection by remember { mutableStateOf<ReaderSelection?>(null) }
    var noteDraft by remember { mutableStateOf<String?>(null) }

    var areaWidthPx by remember { mutableIntStateOf(0) }
    var areaHeightPx by remember { mutableFloatStateOf(0f) }

    val book = state.book
    val position = ReadingPosition(blockIndex.coerceAtLeast(0), charOffset)

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val margin = state.settings.marginDp.dp
    val marginPx = with(density) { margin.toPx() }
    val textWidthPx = (areaWidthPx - marginPx * 2).toInt()

    val pageMeasurer = remember(state.blocks, state.settings, textWidthPx) {
        ComposePageMeasurer(
            blocks = state.blocks,
            measurer = measurer,
            settings = state.settings,
            widthPx = textWidthPx,
            density = density,
        )
    }

    val page = remember(position, state.blocks, state.settings, textWidthPx, areaHeightPx) {
        Paginator.page(position, state.blocks, areaHeightPx, pageMeasurer)
    }

    val currentPageNumber =
        state.blocks.getOrNull(page.fragments.firstOrNull()?.blockIndex ?: 0)?.page ?: 0
    val bookmark = remember(page, state.bookmarks) { Bookmarks.on(page, state.bookmarks) }

    // Continuar donde se dejo, una sola vez por libro. Si esto corriera en cada
    // recomposicion, cada ajuste de tamano de letra devolveria al sitio guardado en lugar
    // de dejar al lector donde esta leyendo ahora.
    LaunchedEffect(book?.id) {
        val saved = book?.lastPosition ?: return@LaunchedEffect
        if (blockIndex < 0) {
            blockIndex = saved.blockIndex
            charOffset = saved.charOffset
        }
    }

    LaunchedEffect(blockIndex, charOffset) {
        if (blockIndex >= 0) onPositionVisible(position)
    }

    // Lo ultimo que se abrio es lo primero que se cierra: la nota, luego lo senalado,
    // luego el panel, luego la pantalla se vuelve a dormir. Solo despues se sale del
    // libro. Salir sin querer pierde el sitio de lectura.
    BackHandler(enabled = noteDraft != null || selection != null || awake) {
        when {
            noteDraft != null -> noteDraft = null
            selection != null -> selection = null
            panel != ReaderPanel.NONE -> panel = ReaderPanel.NONE
            else -> awake = false
        }
    }

    fun goTo(target: ReadingPosition) {
        selection = null
        noteDraft = null
        panel = ReaderPanel.NONE
        awake = false
        blockIndex = target.blockIndex
        charOffset = target.charOffset
    }

    fun turnNext() {
        if (page.end.blockIndex < state.blocks.size) goTo(page.end)
    }

    fun turnPrevious() {
        if (position <= ReadingPosition.Start) return
        goTo(Paginator.previous(position, state.blocks, areaHeightPx, pageMeasurer).start)
    }

    ZenScreen(
        modifier = modifier,
        // El margen lateral lo pone el usuario, asi que la pantalla no impone el suyo, y
        // el aire de arriba y abajo tampoco: la hoja llega hasta los bordes seguros.
        horizontalPadding = 0.dp,
        onSwipeBack = onBack,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged {
                    areaWidthPx = it.width
                    areaHeightPx = it.height.toFloat() -
                        with(density) { (ZenSpacing.Small * 2).toPx() }
                },
        ) {
            when {
                state.loading -> MonoLabel(
                    text = stringResource(R.string.reader_loading),
                    modifier = Modifier.padding(horizontal = ZenSpacing.ScreenHorizontal),
                )

                state.missing -> MonoLabel(
                    text = stringResource(R.string.reader_missing),
                    modifier = Modifier.padding(horizontal = ZenSpacing.ScreenHorizontal),
                )

                else -> ReaderPageView(
                    page = page,
                    blocks = state.blocks,
                    settings = state.settings,
                    highlights = state.highlights,
                    selection = selection,
                    onTapAt = { fraction ->
                        when {
                            // Lo primero que hace un toque es soltar lo senalado. Si no,
                            // el toque para cancelar pasaria pagina y se llevaria la
                            // frase de vista sin avisar.
                            selection != null -> {
                                selection = null
                                noteDraft = null
                            }

                            fraction < SIDE_ZONE -> turnPrevious()
                            fraction > 1f - SIDE_ZONE -> turnNext()

                            else -> {
                                awake = !awake
                                if (!awake) panel = ReaderPanel.NONE
                            }
                        }
                    },
                    onLongPressAt = { block, offset ->
                        val text = state.blocks.getOrNull(block)?.text ?: return@ReaderPageView
                        panel = ReaderPanel.NONE
                        noteDraft = null
                        val existing = HighlightSpans.at(block, offset, state.highlights)
                        selection = if (existing != null) {
                            ReaderSelection(block, TextSpan(existing.start, existing.end), existing)
                        } else {
                            ReaderSelection(block, Sentences.at(text, offset))
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = margin, vertical = ZenSpacing.Small),
                )
            }

            // La salida solo existe con la pantalla despierta, que es lo que pide leer a
            // pagina completa. Sigue habiendo dos: tocar el centro y volver a tener el
            // boton, o arrastrar desde el borde como en cualquier otra pantalla de Zen.
            if (awake && book != null) {
                ZenHeaderStrip(
                    left = stringResource(R.string.reading_title),
                    right = stringResource(
                        if (bookmark != null) R.string.reader_bookmarked else R.string.reader_bookmark,
                    ),
                    rightDescription = stringResource(
                        if (bookmark != null) {
                            R.string.reader_bookmark_remove_label
                        } else {
                            R.string.reader_bookmark_add_label
                        },
                    ),
                    // El hueco derecho no es un rotulo muerto: es el boton de marcar, y
                    // dice cosas distintas segun la hoja. Marcar tiene que costar un
                    // toque o nadie marca nada.
                    onRightClick = {
                        val existing = bookmark
                        if (existing != null) {
                            onDeleteBookmark(existing.id)
                        } else {
                            onAddBookmark(page.start, snippetOf(page, state), currentPageNumber)
                        }
                    },
                    onBack = onBack,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // Fondo opaco: sin el, el texto del libro se leeria por debajo.
                        .background(ZenColors.Background)
                        // El aire de debajo va DENTRO del fondo, asi que lo que hace es
                        // alargar el negro por debajo del filete. Sin el, la linea de
                        // texto que la franja tapa asoma justo ahi: se veian trocitos de
                        // glifo colandose por el filete. Comprobado en el dispositivo.
                        .padding(
                            start = ZenSpacing.ScreenHorizontal,
                            end = ZenSpacing.ScreenHorizontal,
                            bottom = ZenSpacing.Small,
                        )
                        .swallowTaps(),
                )
            }

            // Abajo hay siempre **una sola** cosa, y la que hay depende de lo ultimo que
            // hiciste: escribir una nota manda sobre haber senalado una frase, y esa
            // manda sobre estar leyendo. Apilarlas dejaria dos filas compitiendo.
            val active = selection
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(ZenColors.Background)
                    // Mismo motivo que arriba: el negro se alarga por encima del filete
                    // para que la linea de texto que queda debajo no asome por el.
                    .padding(top = ZenSpacing.Small)
                    .swallowTaps(),
            ) {
                if (book != null) {
                    ReaderControlPanel(
                        visible = awake && active == null,
                        panel = panel,
                        settings = state.settings,
                        chapters = state.chapters,
                        pageStops = state.pageStops,
                        hits = state.hits,
                        bookmarks = state.bookmarks,
                        highlights = state.highlights,
                        query = query,
                        onOpenPanel = { panel = if (panel == it) ReaderPanel.NONE else it },
                        onTextStep = onTextStep,
                        onLeadingStep = onLeadingStep,
                        onMarginStep = onMarginStep,
                        onToggleSerif = onToggleSerif,
                        onQueryChange = onQueryChange,
                        onJumpTo = ::goTo,
                        onDeleteBookmark = onDeleteBookmark,
                        onDeleteHighlight = onDeleteHighlight,
                        onDelete = onDelete,
                    )
                }

                when {
                    active != null && noteDraft != null -> ReaderNoteBar(
                        draft = noteDraft.orEmpty(),
                        onDraftChange = { noteDraft = it },
                        onSave = {
                            val existing = active.existing
                            if (existing != null) {
                                onSetNote(existing, noteDraft)
                            } else {
                                onHighlight(
                                    active.blockIndex,
                                    active.span.start,
                                    active.span.end,
                                    textOf(active, state),
                                    currentPageNumber,
                                    noteDraft,
                                )
                            }
                            noteDraft = null
                            selection = null
                        },
                        onCancel = { noteDraft = null },
                    )

                    active != null -> ReaderSelectionBar(
                        selection = active,
                        onHighlight = {
                            val existing = active.existing
                            if (existing != null) {
                                onDeleteHighlight(existing.id)
                            } else {
                                onHighlight(
                                    active.blockIndex,
                                    active.span.start,
                                    active.span.end,
                                    textOf(active, state),
                                    currentPageNumber,
                                    null,
                                )
                            }
                            selection = null
                        },
                        onNote = { noteDraft = active.existing?.note.orEmpty() },
                        onExtend = {
                            val text = state.blocks[active.blockIndex].text
                            selection = active.copy(span = Sentences.extend(text, active.span))
                        },
                        onCancel = { selection = null },
                    )

                    awake && book != null -> ReaderPageBar(
                        page = page,
                        blocks = state.blocks,
                        blockCount = book.blockCount,
                        pageCount = book.pageCount,
                        chapters = state.chapters,
                        onPrevious = ::turnPrevious,
                        onNext = ::turnNext,
                    )
                }
            }
        }
    }
}

/** El texto que hay debajo de lo senalado. */
private fun textOf(selection: ReaderSelection, state: ReaderUiState): String {
    val text = state.blocks.getOrNull(selection.blockIndex)?.text ?: return ""
    return text.substring(
        selection.span.start.coerceIn(0, text.length),
        selection.span.end.coerceIn(0, text.length),
    )
}

/** El principio de la hoja, que es lo que se guarda con la marca para reconocerla. */
private fun snippetOf(page: ReaderPage, state: ReaderUiState): String {
    val fragment = page.fragments.firstOrNull() ?: return ""
    val text = state.blocks.getOrNull(fragment.blockIndex)?.text ?: return ""
    return text.substring(
        fragment.start.coerceIn(0, text.length),
        fragment.end.coerceIn(0, text.length),
    )
}

/** Un tercio de pantalla a cada lado pasa hoja; el de en medio despierta los mandos. */
private const val SIDE_ZONE = 1f / 3f

/**
 * Se traga los toques que caen en un hueco del panel.
 *
 * Un fondo opaco tapa el texto pero **no para el dedo**: sin esto, tocar el hueco de al
 * lado de un boton de los mandos atravesaba hasta la hoja de debajo y pasaba pagina o
 * cancelaba lo que acababas de senalar. Se noto tocando el campo de escribir una nota:
 * el campo cogia el foco y a la vez la hoja de detras se llevaba la seleccion por
 * delante, asi que la nota no se podia escribir. Encontrado en el dispositivo.
 */
private fun Modifier.swallowTaps(): Modifier =
    pointerInput(Unit) { detectTapGestures { /* deliberadamente vacio */ } }
