package com.zenlauncher.zen.domain.apps

import com.zenlauncher.zen.domain.model.InstalledApp

/**
 * Las aplicaciones "que no quitan tiempo": las que se abren para resolver algo concreto
 * y se cierran solas —buscar, llamar, mirar la hora, pagar—, frente a las que estan
 * hechas para que te quedes.
 *
 * Son el contenido **por defecto** de la pantalla de inicio: un launcher recien puesto
 * no puede aparecer vacio, y pedirle al usuario que elija ocho aplicaciones antes de
 * poder llamar por telefono seria una barrera absurda. En cuanto elige favoritos en
 * Ajustes, los suyos mandan y esta lista deja de usarse.
 *
 * Cada hueco lleva **varios paquetes candidatos** porque el mismo papel lo cumple una
 * aplicacion distinta segun la ROM: el marcador de un Pixel no es el de un Nothing ni
 * el de un Samsung. Se coge el primero que este instalado y se descarta el hueco si no
 * hay ninguno; nunca se inventa una entrada que no se pueda abrir.
 */
object EssentialApps {

    /**
     * Tope de aplicaciones en la pantalla de inicio. Una pantalla de inicio que crece
     * deja de ser una pantalla de inicio; coincide con el numero de huecos esenciales.
     */
    const val MAX_HOME_APPS = 8

    /** Un papel de la pantalla de inicio y los paquetes que pueden cumplirlo, por orden. */
    data class Slot(val id: String, val candidates: List<String>)

    /**
     * El orden es el de la pantalla, no el alfabetico: primero lo que se usa de pie y
     * con prisa (buscar, hablar, llamar), al final lo que se consulta sentado.
     */
    val slots: List<Slot> = listOf(
        Slot(
            id = "buscar",
            candidates = listOf(
                "com.google.android.googlequicksearchbox",
                "com.google.android.apps.searchlite",
            ),
        ),
        Slot(
            id = "whatsapp",
            candidates = listOf("com.whatsapp", "com.whatsapp.w4b"),
        ),
        Slot(
            id = "telefono",
            candidates = listOf(
                "com.google.android.dialer",
                "com.android.dialer",
                "com.nothing.dialer",
                "com.samsung.android.dialer",
            ),
        ),
        Slot(
            id = "reloj",
            candidates = listOf(
                "com.google.android.deskclock",
                "com.android.deskclock",
                "com.nothing.clock",
                "com.sec.android.app.clockpackage",
            ),
        ),
        Slot(
            id = "ajustes",
            candidates = listOf("com.android.settings", "com.nothing.settings"),
        ),
        Slot(
            id = "mensajes",
            candidates = listOf(
                "com.google.android.apps.messaging",
                "com.android.messaging",
                "com.samsung.android.messaging",
            ),
        ),
        Slot(
            id = "musica",
            // La musica no roba tiempo, lo acompana: se pone y se deja de mirar. El
            // mando de la pantalla de inicio evita abrirla la mayoria de las veces.
            candidates = listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
        ),
        Slot(
            id = "banco",
            // imagin (CaixaBank) primero; si no esta, la aplicacion clasica del banco.
            // El primero es el verificado en dispositivo: la ficha de Play escribe
            // "imaginBank" pero el paquete instalado va todo en minusculas.
            candidates = listOf(
                "com.imaginbank.app",
                "com.imaginBank.app",
                "es.lacaixa.mobile.android.newwapicon",
            ),
        ),
    )

    /**
     * @param installed aplicaciones ya visibles: quien llama filtra antes las restringidas,
     *   porque una aplicacion restringida no puede colarse por la puerta de atras.
     * @return las esenciales instaladas, en el orden de [slots] y sin repetir paquete.
     */
    fun resolve(installed: List<InstalledApp>): List<InstalledApp> {
        val byPackage = installed.associateBy { it.packageName }
        return slots
            .mapNotNull { slot -> slot.candidates.firstNotNullOfOrNull(byPackage::get) }
            // Dos huecos podrian resolver al mismo paquete en una ROM que unifique
            // aplicaciones (mensajes y telefono, por ejemplo). Una entrada por aplicacion.
            .distinctBy { it.packageName }
    }
}
