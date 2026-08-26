package com.zenlauncher.zen.presentation.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zenlauncher.zen.R
import com.zenlauncher.zen.data.scanner.CameraXScanner
import com.zenlauncher.zen.data.scanner.SensorStillness
import com.zenlauncher.zen.domain.scanner.CaptureHint
import com.zenlauncher.zen.domain.scanner.ScanError
import com.zenlauncher.zen.domain.scanner.ScanPhase
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * El escaner: camara arriba, un boton de disparar abajo y nada mas.
 *
 * La pantalla tiene **dos caras** y se turnan segun el estado: apuntar y revisar. No hay
 * pestanas ni menus; se pasa de una a otra capturando o confirmando, que es el unico
 * camino que existe. Es la misma idea que el menu de la pantalla de inicio, que sustituye
 * a la pantalla entera en lugar de anadirse a ella.
 *
 * Todo lo que se ve se lee ademas como texto —BUSCANDO, ACERCATE, SUJETA, LISTO—, no solo
 * como un marco que cambia de tono: es la regla de Zen de que ningun estado dependa del
 * color ni de la agudeza visual.
 */
@Composable
fun ScannerScreen(
    state: ScannerUiState,
    onFrame: (com.zenlauncher.zen.domain.scanner.GrayFrame, Boolean) -> Unit,
    onCameraReady: (Boolean) -> Unit,
    onCameraFailed: (ScanError) -> Unit,
    onCapture: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onSetTorch: (Boolean) -> Unit,
    onOpenPage: (String) -> Unit,
    onSavePdf: () -> Unit,
    onAcknowledge: () -> Unit,
    onBack: () -> Unit,
    /** Lo llama la pantalla cuando el ViewModel pide una foto. Ver `captureRequests`. */
    captureBridge: @Composable (CameraXScanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var denied by remember { mutableStateOf(false) }

    // El permiso se pide **al abrir el escaner**, que es el unico sitio de Zen donde hace
    // falta la camara. Quien no entra aqui no ve nunca el dialogo, igual que quien
    // escribe sus notas con el teclado no ve el del microfono.
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        granted = allowed
        denied = !allowed
    }

    LaunchedEffect(Unit) {
        if (!granted) request.launch(Manifest.permission.CAMERA)
    }

    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.scanner_title),
            right = "%02d".format(state.document.pageCount),
            rightDescription = stringResource(
                R.string.scanner_pages_description,
                state.document.pageCount,
            ),
            onBack = onBack,
        )

        when {
            state.phase == ScanPhase.ERROR ->
                ScannerFailure(error = state.error, onRetry = onAcknowledge, onBack = onBack)

            !granted ->
                CameraPermissionNotice(
                    denied = denied,
                    onRequest = { request.launch(Manifest.permission.CAMERA) },
                )

            else -> CameraFace(
                state = state,
                lifecycleOwner = lifecycleOwner,
                onFrame = onFrame,
                onCameraReady = onCameraReady,
                onCameraFailed = onCameraFailed,
                onCapture = onCapture,
                onToggleAutoCapture = onToggleAutoCapture,
                onSetTorch = onSetTorch,
                onOpenPage = onOpenPage,
                onSavePdf = onSavePdf,
                captureBridge = captureBridge,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CameraFace(
    state: ScannerUiState,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onFrame: (com.zenlauncher.zen.domain.scanner.GrayFrame, Boolean) -> Unit,
    onCameraReady: (Boolean) -> Unit,
    onCameraFailed: (ScanError) -> Unit,
    onCapture: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onSetTorch: (Boolean) -> Unit,
    onOpenPage: (String) -> Unit,
    onSavePdf: () -> Unit,
    captureBridge: @Composable (CameraXScanner) -> Unit,
) {
    val context = LocalContext.current
    val camera = remember { CameraXScanner(context) }
    val stillness = remember { SensorStillness(context) }

    // El acelerometro se registra al entrar y **se suelta al salir**. Un oyente de sensor
    // que sobrevive a su pantalla es bateria gastandose para nadie, y aqui el proceso es
    // el de la pantalla de inicio: durara hasta que se apague el telefono.
    DisposableEffect(Unit) {
        stillness.start()
        onDispose {
            stillness.stop()
            camera.unbind()
        }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        val bound = camera.bind(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = view.surfaceProvider,
        ) { frame -> onFrame(frame, stillness.still) }

        if (bound) onCameraReady(camera.hasFlash) else onCameraFailed(ScanError.CAMERA_UNAVAILABLE)
    }

    LaunchedEffect(state.torchOn) { camera.setTorch(state.torchOn) }

    captureBridge(camera)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .background(ZenColors.Background),
    ) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    // FIT_CENTER y no FILL: el marco de la hoja se calcula sobre el frame
                    // entero, asi que si la vista previa recortara por los lados, las
                    // esquinas se dibujarian donde no estan. Ademas, con la hoja llenando
                    // el encuadre, recortar esconderia justo los bordes que hay que ver.
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }.also { previewView = it }
            },
            modifier = Modifier
                .fillMaxSize()
                // Tocar para enfocar y medir la luz ahi. Es un toque, no un gesto oculto.
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        camera.focusAt(
                            x = position.x,
                            y = position.y,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                        )
                    }
                },
        )

        DocumentOverlay(
            quad = state.live.quad,
            imageAspect = state.frameAspect,
            ready = state.phase == ScanPhase.READY_TO_CAPTURE,
            modifier = Modifier.fillMaxSize(),
        )
    }

    Spacer(Modifier.height(ZenSpacing.Small))

    Text(
        text = stringResource(hintLabel(state)),
        style = ZenTextStyles.MonoLabel,
        color = if (state.phase == ScanPhase.READY_TO_CAPTURE) {
            ZenColors.Foreground
        } else {
            ZenColors.Dim
        },
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    Spacer(Modifier.height(ZenSpacing.Medium))

    CameraControls(
        state = state,
        onCapture = onCapture,
        onToggleAutoCapture = onToggleAutoCapture,
        onSetTorch = onSetTorch,
    )

    if (!state.document.empty) {
        Spacer(Modifier.height(ZenSpacing.Medium))
        ZenHairline()
        PagesStrip(state = state, onOpenPage = onOpenPage, onSavePdf = onSavePdf)
    }
}

/**
 * Los mandos: disparar en el centro, y a los lados lo que se toca una vez y se olvida.
 *
 * El obturador es un circulo y no un rotulo con marco, que es lo unico que rompe aqui el
 * sistema visual. Se hace a proposito: es el unico control de toda la aplicacion que se
 * busca a ciegas, con el movil ya levantado y la vista puesta en la hoja.
 */
@Composable
private fun CameraControls(
    state: ScannerUiState,
    onCapture: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onSetTorch: (Boolean) -> Unit,
) {
    val captureLabel = stringResource(R.string.scanner_capture_label)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZenTagButton(
            text = stringResource(
                if (state.autoCapture) R.string.scanner_auto_on else R.string.scanner_auto_off,
            ),
            onClick = onToggleAutoCapture,
            onClickLabel = stringResource(
                if (state.autoCapture) {
                    R.string.scanner_auto_off_label
                } else {
                    R.string.scanner_auto_on_label
                },
            ),
        )

        Box(
            modifier = Modifier
                .size(SHUTTER_TOUCH)
                .clickable(
                    role = Role.Button,
                    onClickLabel = captureLabel,
                    enabled = state.phase != ScanPhase.CAPTURING &&
                        state.phase != ScanPhase.PROCESSING,
                    onClick = onCapture,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(SHUTTER_RING)
                    .border(ZenSpacing.Hairline, ZenColors.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(SHUTTER_CORE)
                        .background(
                            if (state.phase == ScanPhase.CAPTURING ||
                                state.phase == ScanPhase.PROCESSING
                            ) {
                                ZenColors.Disabled
                            } else {
                                ZenColors.Foreground
                            },
                            CircleShape,
                        ),
                )
            }
        }

        if (state.hasFlash) {
            ZenTagButton(
                text = stringResource(
                    if (state.torchOn) R.string.scanner_light_on else R.string.scanner_light_off,
                ),
                onClick = { onSetTorch(!state.torchOn) },
            )
        } else {
            // Sin linterna no se pinta un boton apagado: lo que no tiene nada detras no
            // se dibuja. Se deja el hueco para que el obturador no se descentre.
            Spacer(Modifier.size(SHUTTER_TOUCH))
        }
    }
}

/**
 * Las paginas que ya se llevan, y la salida hacia el PDF.
 *
 * Aparece **solo cuando hay alguna**: con cero paginas es una fila vacia diciendo cero, y
 * eso es ruido con forma de dato. Misma regla que el mando del reproductor de la home.
 */
@Composable
private fun PagesStrip(
    state: ScannerUiState,
    onOpenPage: (String) -> Unit,
    onSavePdf: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ZenSpacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(
            text = stringResource(R.string.scanner_pages, state.document.pageCount),
            color = ZenColors.Secondary,
            modifier = Modifier
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.scanner_open_last_label),
                ) {
                    state.document.pages.lastOrNull()?.let { onOpenPage(it.id) }
                }
                .padding(vertical = ZenSpacing.Medium),
        )

        ZenTagButton(
            text = stringResource(R.string.scanner_save_pdf),
            onClick = onSavePdf,
            onClickLabel = stringResource(R.string.scanner_save_pdf_label),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CameraPermissionNotice(
    denied: Boolean,
    onRequest: () -> Unit,
) {
    Spacer(Modifier.height(ZenSpacing.XXLarge))
    Text(
        text = stringResource(
            if (denied) R.string.scanner_camera_denied else R.string.scanner_camera_needed,
        ),
        style = ZenTextStyles.Body,
        color = ZenColors.Secondary,
    )
    Spacer(Modifier.height(ZenSpacing.Medium))
    ZenTagButton(text = stringResource(R.string.scanner_camera_grant), onClick = onRequest)
    Spacer(Modifier.weight(1f))
}

/**
 * Un fallo del que no se puede seguir.
 *
 * Nunca cierra nada: dice que pasa en palabras y deja las dos unicas salidas que tienen
 * sentido, reintentar o volver. Zen es la pantalla de inicio del telefono, asi que un
 * error aqui tiene que terminar siempre en un rotulo.
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ScannerFailure(
    error: ScanError?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Spacer(Modifier.height(ZenSpacing.XXLarge))
    Text(
        text = stringResource(scanErrorLabel(error)),
        style = ZenTextStyles.Body,
        color = ZenColors.Secondary,
    )
    Spacer(Modifier.height(ZenSpacing.Medium))
    Row(horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Medium)) {
        ZenTagButton(text = stringResource(R.string.scanner_retry), onClick = onRetry)
        ZenTagButton(text = stringResource(R.string.scanner_leave), onClick = onBack)
    }
    Spacer(Modifier.weight(1f))
}

private fun hintLabel(state: ScannerUiState): Int = when {
    state.phase == ScanPhase.CAPTURING -> R.string.scanner_hint_capturing
    state.phase == ScanPhase.PROCESSING -> R.string.scanner_hint_processing
    state.phase == ScanPhase.SAVING -> R.string.scanner_hint_saving
    state.phase == ScanPhase.INITIALIZING -> R.string.scanner_hint_starting
    else -> when (state.hint) {
        CaptureHint.SEARCHING -> R.string.scanner_hint_searching
        CaptureHint.TOO_FAR -> R.string.scanner_hint_too_far
        CaptureHint.HOLD_STILL -> R.string.scanner_hint_hold
        CaptureHint.READY -> R.string.scanner_hint_ready
    }
}

/** Cada fallo, en palabras. Ninguno se ensena como un codigo. */
internal fun scanErrorLabel(error: ScanError?): Int = when (error) {
    ScanError.CAMERA_UNAVAILABLE -> R.string.scanner_error_camera
    ScanError.VISION_UNAVAILABLE -> R.string.scanner_error_vision
    ScanError.CAPTURE_FAILED -> R.string.scanner_error_capture
    ScanError.NO_DOCUMENT -> R.string.scanner_error_no_document
    ScanError.DOCUMENT_TOO_SMALL -> R.string.scanner_error_too_small
    ScanError.SAVE_FAILED -> R.string.scanner_error_save
    ScanError.OUT_OF_SPACE -> R.string.scanner_error_space
    ScanError.OCR_FAILED -> R.string.scanner_error_ocr
    null -> R.string.scanner_error_unknown
}

private val SHUTTER_TOUCH = androidx.compose.ui.unit.Dp(72f)
private val SHUTTER_RING = androidx.compose.ui.unit.Dp(64f)
private val SHUTTER_CORE = androidx.compose.ui.unit.Dp(52f)
