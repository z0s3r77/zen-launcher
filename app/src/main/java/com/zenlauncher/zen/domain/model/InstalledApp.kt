package com.zenlauncher.zen.domain.model

/** Aplicacion lanzable del dispositivo, ya resuelta a lo que la UI necesita. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val componentName: String,
) {
    val sortKey: String get() = label.lowercase()
}
