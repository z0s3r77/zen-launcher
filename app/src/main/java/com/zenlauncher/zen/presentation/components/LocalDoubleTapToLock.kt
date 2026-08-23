package com.zenlauncher.zen.presentation.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Accion de "doble toque en zona vacia para apagar la pantalla".
 *
 * Va por CompositionLocal y no por parametro porque la ofrece **toda** pantalla de Zen:
 * pasarla a mano por las ocho pantallas seria ruido en cada firma sin ganar nada.
 *
 * Por defecto no hace nada: si el administrador de dispositivos no esta concedido, un
 * doble toque perdido no debe producir ni un aviso ni una vibracion.
 */
val LocalDoubleTapToLock = staticCompositionLocalOf<() -> Unit> { {} }
