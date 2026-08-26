package com.zenlauncher.zen.data.scanner

import android.util.Log
import com.zenlauncher.zen.domain.scanner.DocumentAspect
import com.zenlauncher.zen.domain.scanner.DocumentProcessor
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Enderezar la hoja y dejarla con cara de documento escaneado.
 *
 * Todo el trabajo pesado del escaner esta aqui, y **solo corre al capturar o al cambiar
 * de filtro**, nunca por frame. Trabaja con JPEG a la entrada y a la salida para que el
 * dominio no vea nunca un `Mat` ni un `Bitmap`.
 *
 * Ninguna funcion lanza: devuelven null. Es la regla de siempre en Zen, y aqui pesa mas
 * de lo normal porque una excepcion desde el proceso del launcher deja el telefono sin
 * pantalla de inicio.
 */
class OpenCvDocumentProcessor(
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : DocumentProcessor {

    override val available: Boolean get() = OpenCvVision.available

    override suspend fun upright(jpeg: ByteArray, rotationDegrees: Int): ByteArray? =
        withContext(io) {
            guard {
                withMats { scope ->
                    val source = decodeColor(jpeg)?.let(scope::keep) ?: return@withMats null
                    val rotation = when (((rotationDegrees % 360) + 360) % 360) {
                        90 -> Core.ROTATE_90_CLOCKWISE
                        180 -> Core.ROTATE_180
                        270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                        // Ya esta derecha: se devuelven los bytes originales sin volver a
                        // codificar. Recomprimir un JPEG para no cambiar nada es perder
                        // calidad a cambio de nada.
                        else -> return@withMats jpeg
                    }
                    val rotated = scope.mat()
                    Core.rotate(source, rotated, rotation)
                    encode(scope, rotated, CAPTURE_QUALITY)
                }
            }
        }

    override suspend fun rectify(jpeg: ByteArray, quad: Quad, quarterTurns: Int): ByteArray? =
        withContext(io) {
            guard {
                withMats { scope ->
                    val source = decodeColor(jpeg)?.let(scope::keep) ?: return@withMats null
                    val width = source.cols()
                    val height = source.rows()
                    if (width <= 0 || height <= 0) return@withMats null

                    val clamped = quad.clampedToImage()
                    val (targetWidth, targetHeight) = DocumentAspect.targetSize(
                        quad = clamped,
                        sourceWidth = width,
                        sourceHeight = height,
                        maxEdge = MAX_OUTPUT_EDGE,
                    )

                    val sourceCorners = scope.keep(
                        MatOfPoint2f(
                            *clamped.points
                                .map { Point(it.x.toDouble() * width, it.y.toDouble() * height) }
                                .toTypedArray(),
                        ),
                    )
                    // El destino en el MISMO orden que declara `Quad`: arriba izquierda,
                    // arriba derecha, abajo derecha, abajo izquierda. Emparejar mal dos
                    // esquinas aqui da un documento reflejado sin ningun error visible.
                    val destinationCorners = scope.keep(
                        MatOfPoint2f(
                            Point(0.0, 0.0),
                            Point(targetWidth - 1.0, 0.0),
                            Point(targetWidth - 1.0, targetHeight - 1.0),
                            Point(0.0, targetHeight - 1.0),
                        ),
                    )

                    val transform = scope.keep(
                        Imgproc.getPerspectiveTransform(sourceCorners, destinationCorners),
                    )
                    val warped = scope.mat()
                    Imgproc.warpPerspective(
                        source,
                        warped,
                        transform,
                        Size(targetWidth.toDouble(), targetHeight.toDouble()),
                        // CUBIC y no LINEAR: el texto pequeno es justo lo que se pierde
                        // al interpolar, y esto solo corre una vez por captura.
                        Imgproc.INTER_CUBIC,
                        Core.BORDER_REPLICATE,
                        Scalar(0.0),
                    )

                    val turned = rotateQuarters(scope, warped, quarterTurns)
                    encode(scope, turned, RECTIFIED_QUALITY)
                }
            }
        }

    override suspend fun applyFilter(rectified: ByteArray, filter: ScanFilter): ByteArray? =
        withContext(io) {
            // El modo original no toca nada: se devuelven los mismos bytes en lugar de
            // decodificar y recomprimir para dejarlo igual pero peor.
            if (filter == ScanFilter.ORIGINAL) return@withContext rectified

            guard {
                withMats { scope ->
                    val source = decodeColor(rectified)?.let(scope::keep) ?: return@withMats null
                    val result = when (filter) {
                        ScanFilter.ORIGINAL -> source
                        ScanFilter.GRAYSCALE -> grayscale(scope, source)
                        ScanFilter.DOCUMENT -> document(scope, source, DOCUMENT_GAMMA, DOCUMENT_WHITE)
                        ScanFilter.HIGH_CONTRAST ->
                            document(scope, source, CONTRAST_GAMMA, CONTRAST_WHITE)
                        ScanFilter.BLACK_AND_WHITE -> blackAndWhite(scope, source)
                    }
                    encode(scope, result, RECTIFIED_QUALITY)
                }
            }
        }

    override suspend fun sharpness(jpeg: ByteArray): Float = withContext(io) {
        guard {
            withMats { scope ->
                val encoded = scope.keep(MatOfByte(*jpeg))
                val gray = scope.keep(
                    Imgcodecs.imdecode(
                        encoded,
                        Imgcodecs.IMREAD_GRAYSCALE or Imgcodecs.IMREAD_IGNORE_ORIENTATION,
                    ),
                )
                if (gray.empty()) return@withMats 0f

                // Se mide sobre una reduccion fija: la varianza del laplaciano depende del
                // tamano, asi que sin normalizar la resolucion una foto grande siempre
                // pareceria mas nitida que la misma foto pequena.
                val small = scope.mat()
                val scale = SHARPNESS_EDGE.toDouble() / max(gray.cols(), gray.rows())
                if (scale < 1.0) {
                    Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
                } else {
                    gray.copyTo(small)
                }

                val laplacian = scope.mat()
                Imgproc.Laplacian(small, laplacian, CvType.CV_32F)

                val mean = scope.keep(MatOfDouble())
                val deviation = scope.keep(MatOfDouble())
                Core.meanStdDev(laplacian, mean, deviation)
                val sigma = deviation.toArray().firstOrNull() ?: 0.0

                // La varianza cruda no tiene escala util: se normaliza contra un valor de
                // referencia medido en el dispositivo con una hoja de texto bien enfocada.
                ((sigma * sigma) / SHARPNESS_REFERENCE).coerceIn(0.0, 1.0).toFloat()
            }
        } ?: 0f
    }

    /** Gris de verdad, sin tocar niveles: para fotos y diagramas. */
    private fun grayscale(scope: MatScope, source: Mat): Mat {
        val gray = scope.mat()
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY)
        return gray
    }

    /**
     * El modo escaner: quitar la sombra y dejar el fondo blanco.
     *
     * El truco es estimar **la luz que habia**, no la imagen. Un cierre morfologico con un
     * elemento mas grande que cualquier letra se come el texto y deja solo el degradado de
     * la iluminacion; dividir la imagen por ese degradado deja el papel plano a 255 y el
     * texto donde estaba.
     *
     * Division y no resta porque la sombra **multiplica** la luz que llega al papel: una
     * zona a media luz tiene el papel a 128 y la tinta a 20, y restando la estimacion el
     * texto de la zona oscura se aclararia tanto como el fondo.
     */
    private fun document(scope: MatScope, source: Mat, gamma: Double, whitePoint: Double): Mat {
        val gray = grayscale(scope, source)

        // El elemento tiene que ser mas grande que la letra mas grande de la pagina, o la
        // "iluminacion" estimada incluira el propio texto y este se borrara al dividir.
        val kernelSize = odd((max(gray.cols(), gray.rows()) / ILLUMINATION_DIVISOR).coerceAtLeast(MIN_KERNEL))
        val kernel = scope.keep(
            Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(kernelSize.toDouble(), kernelSize.toDouble()),
            ),
        )
        val illumination = scope.mat()
        Imgproc.morphologyEx(gray, illumination, Imgproc.MORPH_CLOSE, kernel)
        // Un desenfoque despues del cierre: sin el, el borde del elemento estructurante
        // deja escalones que salen como bandas en el fondo ya blanco.
        Imgproc.GaussianBlur(illumination, illumination, Size(kernelSize.toDouble(), kernelSize.toDouble()), 0.0)

        val flat = scope.mat()
        Core.divide(gray, illumination, flat, 255.0)

        return stretch(scope, flat, gamma, whitePoint)
    }

    /** Dos tonos con umbral adaptativo, sobre la imagen ya sin sombras. */
    private fun blackAndWhite(scope: MatScope, source: Mat): Mat {
        val flat = document(scope, source, DOCUMENT_GAMMA, DOCUMENT_WHITE)
        val binary = scope.mat()
        // Adaptativo y no un corte fijo aunque el fondo ya este plano: en una fotocopia
        // con la tinta muy floja por una esquina, un corte global se lleva ese trozo
        // entero. El bloque se escala con la imagen para que valga a cualquier resolucion.
        val block = odd((max(flat.cols(), flat.rows()) / THRESHOLD_BLOCK_DIVISOR).coerceAtLeast(MIN_KERNEL))
        Imgproc.adaptiveThreshold(
            flat,
            binary,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            block,
            THRESHOLD_CONSTANT,
        )
        return binary
    }

    /**
     * Estira los niveles hasta que el papel es blanco, con una gamma que respeta el gris
     * intermedio.
     *
     * Los dos extremos salen de **percentiles de la propia imagen**, no del minimo y el
     * maximo: un unico pixel quemado por un reflejo, o una mota negra, fijarian el rango
     * entero y el estirado no haria nada. El percentil alto se toma bajo (99 %) para que
     * el papel se vaya a blanco de verdad y no a un gris de 240.
     */
    private fun stretch(scope: MatScope, gray: Mat, gamma: Double, whitePercentile: Double): Mat {
        val (black, white) = percentiles(scope, gray, BLACK_PERCENTILE, whitePercentile)
        val span = (white - black).coerceAtLeast(1)

        val table = ByteArray(LUT_SIZE)
        for (value in 0 until LUT_SIZE) {
            val normalized = ((value - black).toDouble() / span).coerceIn(0.0, 1.0)
            val corrected = normalized.pow(gamma)
            table[value] = (corrected * 255.0).roundToInt().coerceIn(0, 255).toByte()
        }

        val lut = scope.keep(Mat(1, LUT_SIZE, CvType.CV_8UC1))
        lut.put(0, 0, table)

        val result = scope.mat()
        Core.LUT(gray, lut, result)
        return result
    }

    /**
     * Los valores de gris que dejan por debajo las fracciones pedidas.
     *
     * El histograma se cuenta sobre una **reduccion**: para saber donde esta el 99 % de la
     * imagen no hacen falta dos megapixeles, y leer el Mat entero a un `ByteArray` seria
     * reservar de golpe tantos megabytes como pixeles tenga la foto dentro del proceso del
     * launcher.
     */
    private fun percentiles(
        scope: MatScope,
        gray: Mat,
        low: Double,
        high: Double,
    ): Pair<Int, Int> {
        val small = scope.mat()
        val scale = HISTOGRAM_EDGE.toDouble() / max(gray.cols(), gray.rows())
        if (scale < 1.0) {
            Imgproc.resize(gray, small, Size(), scale, scale, Imgproc.INTER_AREA)
        } else {
            gray.copyTo(small)
        }

        val count = (small.total() * small.channels()).toInt()
        if (count <= 0) return 0 to 255
        val pixels = ByteArray(count)
        small.get(0, 0, pixels)

        val histogram = IntArray(LUT_SIZE)
        for (pixel in pixels) histogram[pixel.toInt() and 0xFF]++

        val lowTarget = (count * low).toInt()
        val highTarget = (count * high).toInt()
        var running = 0
        var black = 0
        var white = 255
        var blackFound = false
        for (value in 0 until LUT_SIZE) {
            running += histogram[value]
            if (!blackFound && running >= lowTarget) {
                black = value
                blackFound = true
            }
            if (running >= highTarget) {
                white = value
                break
            }
        }
        return min(black, white) to max(black, white)
    }

    private fun rotateQuarters(scope: MatScope, source: Mat, quarterTurns: Int): Mat {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return source
        val rotation = when (turns) {
            1 -> Core.ROTATE_90_CLOCKWISE
            2 -> Core.ROTATE_180
            else -> Core.ROTATE_90_COUNTERCLOCKWISE
        }
        val rotated = scope.mat()
        Core.rotate(source, rotated, rotation)
        return rotated
    }

    private fun decodeColor(jpeg: ByteArray): Mat? {
        val encoded = MatOfByte(*jpeg)
        return try {
            // Se ignora la etiqueta EXIF: la orientacion ya se resolvio girando los
            // pixeles en `upright`, y honrarla otra vez la giraria dos veces.
            val decoded = Imgcodecs.imdecode(
                encoded,
                Imgcodecs.IMREAD_COLOR or Imgcodecs.IMREAD_IGNORE_ORIENTATION,
            )
            if (decoded.empty()) {
                decoded.release()
                null
            } else {
                decoded
            }
        } finally {
            encoded.release()
        }
    }

    private fun encode(scope: MatScope, mat: Mat, quality: Int): ByteArray? {
        val buffer = scope.keep(MatOfByte())
        val parameters = scope.keep(MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality))
        val written = Imgcodecs.imencode(".jpg", mat, buffer, parameters)
        return if (written) buffer.toArray() else null
    }

    /** Los nucleos y los bloques de umbral tienen que ser impares. */
    private fun odd(value: Int): Int = if (value % 2 == 0) value + 1 else value

    private inline fun <T> guard(block: () -> T?): T? = try {
        block()
    } catch (error: Throwable) {
        Log.w(TAG, "El procesado fallo; se conserva lo anterior", error)
        null
    }

    private companion object {
        const val TAG = "ZenScanner"

        /**
         * Lado maximo de la hoja enderezada.
         *
         * 2400 px de lado largo es un A4 a unos 200 puntos por pulgada: de sobra para leer
         * y para el OCR, y **la mitad de memoria** que la foto de 12 megapixeles de la que
         * sale. En el proceso del launcher eso importa mas que en cualquier otra
         * aplicacion. Ver `LauncherMemory`.
         */
        const val MAX_OUTPUT_EDGE = 2400

        const val CAPTURE_QUALITY = 95
        const val RECTIFIED_QUALITY = 90

        /** El elemento del cierre, como fraccion del lado largo. */
        const val ILLUMINATION_DIVISOR = 16
        const val THRESHOLD_BLOCK_DIVISOR = 40
        const val MIN_KERNEL = 15

        const val THRESHOLD_CONSTANT = 12.0

        const val BLACK_PERCENTILE = 0.02
        const val DOCUMENT_WHITE = 0.99
        const val CONTRAST_WHITE = 0.94

        /**
         * Menos de 1 aclara los medios tonos.
         *
         * En modo documento se deja casi neutro para no borrar el lapiz flojo; en alto
         * contraste se aprieta, que es justo lo que se pide cuando la tinta es muy clara.
         */
        const val DOCUMENT_GAMMA = 0.90
        const val CONTRAST_GAMMA = 0.65

        const val LUT_SIZE = 256
        const val HISTOGRAM_EDGE = 512
        const val SHARPNESS_EDGE = 800

        /**
         * Varianza del laplaciano de una hoja de texto bien enfocada, medida en el
         * dispositivo. Solo sirve para dar una escala de 0 a 1 al aviso de foto movida.
         */
        const val SHARPNESS_REFERENCE = 900.0
    }
}
