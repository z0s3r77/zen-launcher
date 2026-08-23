package com.zenlauncher.zen.system

import android.view.WindowInsets
import com.zenlauncher.zen.domain.system.SystemBar

/** Traduce la politica de dominio a la mascara de tipos que espera WindowInsetsController. */
fun Set<SystemBar>.toInsetsTypeMask(): Int = fold(0) { mask, bar ->
    mask or when (bar) {
        SystemBar.STATUS -> WindowInsets.Type.statusBars()
        SystemBar.NAVIGATION -> WindowInsets.Type.navigationBars()
    }
}
