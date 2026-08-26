package com.zenlauncher.zen.data.scanner

import org.opencv.core.Mat

/**
 * Un sitio donde apuntar los `Mat` para soltarlos todos al salir.
 *
 * No es azucar: un `Mat` es memoria **nativa**, fuera del monton de Java, y el recolector
 * de basura no la ve. La deteccion crea media docena por frame y corre quince veces por
 * segundo; sin soltarlos a mano, el escaner reserva cientos de megabytes en un minuto y
 * el sistema mata al proceso. Que ese proceso sea la pantalla de inicio es la razon de
 * que esto exista en lugar de confiar en el finalizador.
 */
internal class MatScope : AutoCloseable {

    private val mats = ArrayList<Mat>(8)

    /** Apunta el `Mat` para soltarlo al cerrar el ambito, y lo devuelve. */
    fun <T : Mat> keep(mat: T): T {
        mats.add(mat)
        return mat
    }

    /** Un `Mat` vacio ya apuntado. */
    fun mat(): Mat = keep(Mat())

    override fun close() {
        // Al reves de como se crearon: las submatrices comparten los datos de su madre,
        // asi que se sueltan antes que ella.
        for (index in mats.indices.reversed()) {
            runCatching { mats[index].release() }
        }
        mats.clear()
    }
}

/** Igual que `use`, con el nombre del ambito delante para que se lea que es nativo. */
internal inline fun <T> withMats(block: (MatScope) -> T): T =
    MatScope().use(block)
