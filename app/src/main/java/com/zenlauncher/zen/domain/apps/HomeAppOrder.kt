package com.zenlauncher.zen.domain.apps

import kotlin.math.hypot

/**
 * El orden de la reticula de la pantalla de inicio: que hueco ocupa cada aplicacion y
 * a cual va a parar la que se esta arrastrando.
 *
 * Vive aqui y no en la reticula de Compose por la razon de siempre: lo que decide se
 * prueba sin Android. La reticula solo aporta la geometria —donde esta el centro de
 * cada hueco— y el dedo; el resto son cuentas.
 *
 * El orden que se guarda es [com.zenlauncher.zen.domain.repository.PreferencesRepository.favouritePackages],
 * el mismo que numera la pantalla de "Elegir aplicaciones": mover una celda en la home
 * y mirar el numero en Ajustes tienen que contar lo mismo.
 */
object HomeAppOrder {

    /**
     * El centro de un hueco de la reticula, en pixeles de la pantalla.
     *
     * Se miden en lugar de calcularse a partir del alto de celda: entre fila y fila hay
     * un filete, la ultima puede quedar a medias y el `fontScale` estira las celdas, asi
     * que una formula con constantes acabaria mintiendo justo en el ultimo hueco.
     */
    data class Slot(val x: Float, val y: Float)

    /**
     * Saca el elemento [from] y lo mete en el hueco [to], desplazando el resto.
     *
     * Devuelve la lista tal cual si el movimiento no lleva a ninguna parte: un indice
     * fuera de rango o el mismo hueco. Quien llama compara para saber si hay algo que
     * escribir.
     */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        val moved = items.toMutableList()
        moved.add(to, moved.removeAt(from))
        return moved
    }

    /**
     * El hueco al que ha llegado la celda [from] tras arrastrarla [dragX],[dragY].
     *
     * Es el hueco cuyo centro queda **mas cerca** del centro de la celda en la mano, no
     * el que hay bajo el dedo: se agarra por cualquier punto de la celda, asi que el
     * dedo suele estar en una esquina y el hueco de debajo no es el que se ve ocupado.
     *
     * Con el empate gana quedarse: sin arrastre, o a media celda exacta, el destino es
     * el hueco de partida. Un roce no puede reordenar la pantalla de inicio.
     */
    fun slotAt(from: Int, dragX: Float, dragY: Float, slots: List<Slot>): Int {
        val origin = slots.getOrNull(from) ?: return from
        val x = origin.x + dragX
        val y = origin.y + dragY
        var best = from
        var bestDistance = hypot(dragX, dragY)
        slots.forEachIndexed { index, slot ->
            val distance = hypot(slot.x - x, slot.y - y)
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * El nuevo orden guardado tras mover la celda [from] al hueco [to] de la reticula.
     *
     * [stored] son los favoritos escritos en preferencias y [visible] lo que la home
     * esta pintando de verdad, que puede ser menos: una favorita restringida o
     * desinstalada desaparece de la reticula pero **sigue guardada**. Sin esta
     * distincion, reordenar reescribiria los favoritos con lo que se ve y una
     * aplicacion restringida se perderia para siempre al quitarle la restriccion.
     *
     * Las escondidas se quedan en su sitio de la lista y lo que se mueve son solo los
     * huecos que ocupan las visibles.
     *
     * Cuando la home va con las esenciales —nada elegido todavia, ver
     * [SeedEssentialFavourites]— lo que se ve no esta en [stored]: entonces manda lo que
     * se ve, que es exactamente lo que el usuario acaba de ordenar con el dedo.
     */
    fun reorder(
        stored: List<String>,
        visible: List<String>,
        from: Int,
        to: Int,
    ): List<String> {
        val moved = move(visible, from, to)
        if (moved == visible) return stored
        if (visible.any { it !in stored }) return moved

        val result = stored.toMutableList()
        var next = 0
        stored.forEachIndexed { index, packageName ->
            if (packageName in visible) {
                result[index] = moved[next]
                next++
            }
        }
        return result
    }
}
