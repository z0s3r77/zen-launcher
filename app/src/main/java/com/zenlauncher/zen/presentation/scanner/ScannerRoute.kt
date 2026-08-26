package com.zenlauncher.zen.presentation.scanner

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenlauncher.zen.data.scanner.CameraXScanner
import com.zenlauncher.zen.domain.scanner.ScanPhase

/**
 * Las dos caras del escaner y el puente con la camara.
 *
 * Vive aparte del `ZenNavHost` porque es la unica pantalla de Zen que cambia de cara sola
 * —al capturar se pasa a revisar, al confirmar se vuelve a la camara— y meter esa maquina
 * dentro del grafo de navegacion habria significado dos destinos con el mismo ViewModel y
 * una pila de navegacion que crece una entrada por pagina escaneada.
 *
 * @param onLeave salir del escaner del todo. Se llama tambien al soltar la pantalla, para
 *   que lo que no se guardo no se quede ocupando la cache del launcher.
 */
@Composable
fun ScannerRoute(
    viewModel: ScannerViewModel,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Salir de la pantalla se lleva lo que no se guardo, igual que abandonar una captura
    // de Notas se lleva las imagenes ya copiadas.
    DisposableEffect(Unit) { onDispose(viewModel::discardAll) }

    val editingPage = state.editingPage

    // Estando en revision, atras vuelve a la camara en lugar de salir del escaner: es la
    // misma idea que el menu de la pantalla de inicio, donde atras cierra el menu antes de
    // hacer cualquier otra cosa.
    BackHandler(enabled = editingPage != null) { viewModel.confirmPage() }

    if (editingPage != null && state.phase != ScanPhase.ERROR) {
        ScanReviewScreen(
            state = state,
            page = editingPage,
            onQuadChange = viewModel::setQuad,
            onFilterChange = viewModel::setFilter,
            onRotate = viewModel::rotate,
            onRunOcr = viewModel::runOcr,
            onRetake = viewModel::retakePage,
            onConfirm = viewModel::confirmPage,
            onSaveImage = viewModel::saveImage,
            onSavePdf = viewModel::savePdf,
            onDelete = { viewModel.deletePage(editingPage.id) },
            onAcknowledge = viewModel::acknowledge,
            onBack = viewModel::confirmPage,
            modifier = modifier,
        )
        return
    }

    ScannerScreen(
        state = state,
        onFrame = viewModel::onFrame,
        onCameraReady = viewModel::onCameraReady,
        onCameraFailed = viewModel::onCameraFailed,
        onCapture = viewModel::requestCapture,
        onToggleAutoCapture = viewModel::toggleAutoCapture,
        onSetTorch = viewModel::setTorch,
        onOpenPage = viewModel::openPage,
        onSavePdf = viewModel::savePdf,
        onAcknowledge = viewModel::acknowledge,
        onBack = onLeave,
        captureBridge = { camera -> CaptureBridge(viewModel = viewModel, camera = camera) },
        modifier = modifier,
    )
}

/**
 * El unico sitio donde el ViewModel y la camara se tocan.
 *
 * El ViewModel decide **cuando** hay que disparar y la camara sabe **como**; el puente
 * esta en medio porque la camara necesita el ciclo de vida de la pantalla y el ViewModel
 * no puede tenerlo. Partirlo asi es lo que permite probar la politica de captura
 * automatica sin una camara delante (ver `CaptureDecision`).
 */
@Composable
private fun CaptureBridge(viewModel: ScannerViewModel, camera: CameraXScanner) {
    LaunchedEffect(camera) {
        viewModel.captureRequests.collect {
            val photo = camera.capture()
            if (photo == null) {
                viewModel.onCaptureFailed()
            } else {
                viewModel.onPhotoTaken(photo.jpeg, photo.rotationDegrees)
            }
        }
    }
}
