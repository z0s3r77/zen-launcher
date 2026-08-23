package com.zenlauncher.zen.domain.system

enum class SystemBar { STATUS, NAVIGATION }

/**
 * Que barras del sistema oculta Zen.
 *
 * Solo la de navegacion —la barra de gestos—, porque una pantalla de inicio no tiene
 * "atras" ni "recientes" a los que ir: la linea blanca del borde inferior solo invita a
 * salir de aqui.
 *
 * **La de estado se dejo de ocultar.** Ocultarla tenia un motivo bueno —los iconos de
 * notificacion son justo el estimulo que la aplicacion existe para quitar— pero
 * provocaba algo peor: al deslizar desde cualquier borde, Android la sacaba de golpe
 * encima del contenido y volvia a esconderla sola. Una barra que aparece y desaparece
 * llama mas la atencion que una que simplemente esta. Entre las dos molestias se eligio
 * la quieta.
 *
 * Consecuencia asumida: la hora, la bateria y la cobertura salen dos veces en la
 * pantalla de inicio, una arriba en pequeno y otra en la tipografia de Zen.
 *
 * **Limite real:** sin Device Owner, Android no permite desactivar los gestos. Ocultar
 * la barra la quita de la vista y deja el gesto de "atras" sin efecto en la home (ver
 * el BackHandler de la navegacion), pero deslizar desde el borde inferior sigue llevando
 * al sistema. Durante una sesion, el anclado de pantalla si lo bloquea de verdad.
 *
 * Consecuencia directa: ese borde **no es de Zen**, y ningun gesto de la aplicacion
 * puede empezar ahi. La pantalla de inicio llego a abrir la lista de aplicaciones con
 * un arrastre hacia arriba y hubo que reservarle al sistema la franja de abajo; el
 * gesto acabo retirado entero —la lista es ahora una fila visible— y con el, esa
 * excepcion. El unico gesto que le queda a Zen es el de volver desde un lateral, ver
 * [EdgeBackPolicy].
 */
object SystemBarsPolicy {
    val hidden: Set<SystemBar> = setOf(SystemBar.NAVIGATION)
}
