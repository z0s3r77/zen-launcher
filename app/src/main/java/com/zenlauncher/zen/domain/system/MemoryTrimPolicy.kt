package com.zenlauncher.zen.domain.system

/**
 * Lo que el sistema pide, sin las constantes de Android.
 *
 * **Solo hay dos, y no es una simplificacion.** Verificado sobre `android-36/android.jar`:
 * de los siete niveles de `ComponentCallbacks2`, cinco estan marcados `@Deprecated`
 * —`TRIM_MEMORY_RUNNING_MODERATE`, `RUNNING_LOW`, `RUNNING_CRITICAL`, `MODERATE` y
 * `COMPLETE`— y Android ya no los entrega. Los dos que siguen vigentes son
 * `TRIM_MEMORY_UI_HIDDEN` y `TRIM_MEMORY_BACKGROUND`, y son justo los dos que aqui hacen
 * falta. Un mapeo que se apoyase en los obsoletos tendria la rama de soltar memoria
 * muerta sin que nada lo dijese.
 */
enum class MemoryTrim {
    /**
     * El usuario acaba de abrir otra aplicacion y Zen ya no se ve. **No** significa que
     * falte memoria: llega en cada salida del launcher, decenas de veces al dia.
     */
    UI_OCULTA,

    /**
     * El proceso ha pasado a la lista de segundo plano: a partir de aqui es candidato a
     * que lo maten cuando haga falta sitio. Es la unica senal vigente de que la memoria
     * de Zen importa.
     */
    EN_SEGUNDO_PLANO,

    OTRA,
}

enum class MemoryRelease { NADA, SOLTAR }

/**
 * Cuando soltar las caches del launcher.
 *
 * La decision no es obvia y va justo al reves de lo que parece. `UI_OCULTA` es el
 * momento en que mas apetece vaciarlo todo —Zen no se ve, ¿para que ocupar?— y es
 * precisamente donde **no** hay que hacerlo: llega cada vez que se abre una aplicacion,
 * y soltar ahi significa releer por IPC la lista entera de aplicaciones en **cada**
 * vuelta a la pantalla de inicio. Un launcher que tarda medio segundo en pintar su
 * reticula cincuenta veces al dia es peor que un launcher que ocupa unos kilobytes de
 * mas, y esos kilobytes no son los que deciden si el sistema mata a alguien.
 *
 * Cuando el proceso ya esta en la cola de candidatos es al reves y no se discute: mas
 * vale una pantalla de inicio lenta que un telefono sin pantalla de inicio.
 */
object MemoryTrimPolicy {
    fun decide(trim: MemoryTrim): MemoryRelease = when (trim) {
        MemoryTrim.EN_SEGUNDO_PLANO -> MemoryRelease.SOLTAR
        MemoryTrim.UI_OCULTA, MemoryTrim.OTRA -> MemoryRelease.NADA
    }
}
