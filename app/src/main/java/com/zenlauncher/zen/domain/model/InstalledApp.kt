package com.zenlauncher.zen.domain.model

/** Aplicacion lanzable del dispositivo, ya resuelta a lo que la UI necesita. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val componentName: String,
) {
    /**
     * Clave de ordenacion alfabetica, **sin distinguir mayusculas**.
     *
     * Es una funcion y no un `get()` que reserve: era `label.lowercase()`, y usada desde
     * un `sortedBy` se evaluaba en cada comparacion —una cadena nueva por comparacion—.
     * Quien ordene listas largas debe usar `compareBy(String.CASE_INSENSITIVE_ORDER)`,
     * que no reserva nada; esto se queda para las listas cortas y para los tests.
     */
    val sortKey: String get() = label.lowercase()
}
