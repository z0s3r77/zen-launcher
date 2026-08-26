package com.zenlauncher.zen.system

import android.content.ComponentCallbacks2
import android.database.sqlite.SQLiteDatabase
import com.zenlauncher.zen.domain.system.MemoryRelease
import com.zenlauncher.zen.domain.system.MemoryTrim
import com.zenlauncher.zen.domain.system.MemoryTrimPolicy

/**
 * La memoria del propio launcher.
 *
 * **"Vaciar la RAM" del telefono no existe para Zen, y no se finge que si.** Desde
 * Android 14 `ActivityManager.killBackgroundProcesses` solo mata procesos *de la propia
 * aplicacion*: cualquier boton de "liberar memoria" en un launcher de Android moderno
 * cierra Zen y nada mas. Las demas aplicaciones las gestiona `lmkd`, el sistema, y lo
 * hace mejor que cualquier heuristica de una app normal —matar un proceso en cache no
 * libera nada util y obliga a arrancarlo entero la proxima vez, que cuesta bateria en
 * lugar de ahorrarla.
 *
 * Lo que si esta en manos de Zen, y es lo que hace esta clase, es **ocupar poco para no
 * ser el proceso que el sistema elija matar**. Un launcher al que matan deja el telefono
 * sin pantalla de inicio y tarda un segundo largo en volver; ese es el problema real de
 * memoria de un launcher, no los megabytes libres del sistema.
 *
 * Dos momentos:
 * - Al terminar de pintar la primera pantalla, se sueltan las paginas de SQLite que se
 *   leyeron para arrancar y ya no hacen falta.
 * - Cuando el proceso pasa a la cola de candidatos a morir, se suelta todo lo cacheable.
 *   Cuando el sistema solo avisa de que Zen dejo de verse, **no** (ver [MemoryTrimPolicy]).
 */
class LauncherMemory(private val releasable: List<Releasable>) {

    /** Algo que guarda una cache reconstruible. */
    fun interface Releasable {
        fun release()
    }

    /**
     * Una sola vez, con la pantalla de inicio ya dibujada.
     *
     * Aqui es donde tiene sentido y no en `Application.onCreate`: al arrancar todavia no
     * hay nada que soltar, y hacerlo antes del primer fotograma solo retrasaria la
     * pantalla que el usuario esta esperando.
     */
    fun releaseAfterFirstFrame() {
        // Las paginas leidas de zen.db para resolver la sesion activa y los favoritos no
        // se vuelven a tocar hasta que se abre Notas o el Registro.
        SQLiteDatabase.releaseMemory()
    }

    /** Traduce el aviso del sistema y actua. Se llama desde `ZenApplication`. */
    fun onTrimMemory(level: Int) {
        if (MemoryTrimPolicy.decide(trimOf(level)) == MemoryRelease.NADA) return
        releasable.forEach { it.release() }
        SQLiteDatabase.releaseMemory()
    }

    /**
     * Solo se miran los dos niveles vigentes. Los cinco intermedios estan `@Deprecated`
     * en el SDK y Android ya no los entrega (comprobado en `android-36/android.jar`),
     * asi que mapearlos habria dejado la rama de soltar memoria muerta sin aviso: en un
     * telefono real nunca habria llegado ninguno. Ver [MemoryTrim].
     */
    private fun trimOf(level: Int): MemoryTrim = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> MemoryTrim.UI_OCULTA
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryTrim.EN_SEGUNDO_PLANO
        else -> MemoryTrim.OTRA
    }
}
