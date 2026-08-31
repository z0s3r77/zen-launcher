package com.zenlauncher.zen.domain.model

/**
 * El aspecto elegido. Son **dos** y no una escala de temas.
 *
 * [NEGRO] es el de siempre: negro puro y una escala de grises apretada contra el fondo,
 * pensada para que en AMOLED el pixel se apague del todo. [SISTEMA] mantiene el mismo
 * fondo negro —eso no se negocia en un launcher que ocupa la pantalla entera— pero sube
 * las superficies y el texto a los grises que usa Android en sus propios paneles, que es
 * lo que pidio quien no queria un "negro total".
 *
 * Sigue siendo una eleccion de contraste, no de color: las dos paletas son monocromas y
 * las dos comparten el ambar y el rojo, que son marcas de significado y no de aspecto.
 *
 * Vive en el dominio y no en `presentation/theme` porque lo que se guarda es la
 * **eleccion**, no los tonos: los tonos son un detalle de como se dibuja.
 */
enum class ZenThemeChoice(val id: String) {
    NEGRO("negro"),
    SISTEMA("sistema"),
    ;

    /**
     * El siguiente, en circulo.
     *
     * La fila de ajustes es un solo toque y no una lista: con dos temas, una lista de
     * dos filas ocupa el doble para decir lo mismo. Si algun dia hay un tercero esto
     * sigue valiendo, y a partir del cuarto habra que cambiar la fila por una lista.
     */
    fun next(): ZenThemeChoice = entries[(ordinal + 1) % entries.size]

    companion object {
        /** El de fabrica es el que Zen tenia antes de que hubiera donde elegir. */
        val Default: ZenThemeChoice = NEGRO

        /**
         * Un id desconocido cae en el de fabrica en lugar de reventar.
         *
         * Puede llegar de un fichero de preferencias tocado a mano o de una version
         * futura que anadio un tema y se desinstalo: una excepcion aqui dejaria el
         * telefono sin pantalla de inicio.
         */
        fun ofIdOrDefault(id: String?): ZenThemeChoice =
            entries.firstOrNull { it.id == id } ?: Default
    }
}
