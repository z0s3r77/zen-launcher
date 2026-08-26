package com.zenlauncher.zen.fakes

import android.content.Intent
import com.zenlauncher.zen.domain.usage.AppOpening
import com.zenlauncher.zen.domain.usage.UsageRepository
import com.zenlauncher.zen.domain.usage.UsageSnapshot
import com.zenlauncher.zen.domain.usage.WeeklyUsage

/**
 * El acceso de uso se concede a mano en Ajustes de Android, asi que en un test hay que
 * poder ponerlo y quitarlo: la mitad de lo que hay que probar es justo el camino sin
 * concesion, donde Zen no puede ensenar un cero.
 */
class FakeUsageRepository(
    var granted: Boolean = true,
    var snapshot: UsageSnapshot = UsageSnapshot(
        dayStartMillis = 0L,
        nowMillis = 0L,
        screenMillis = 0L,
        unlocks = 0,
        apps = emptyList(),
    ),
    var openings: List<AppOpening> = emptyList(),
    var week: WeeklyUsage = WeeklyUsage(emptyList()),
) : UsageRepository {

    /** Cuantas veces se ha releido el dia entero: el sondeo es lo que gasta bateria. */
    var fullReads = 0
        private set

    override fun hasAccess(): Boolean = granted

    override fun accessIntent(): Intent = Intent("zen.test.USAGE_ACCESS")

    override suspend fun today(nowMillis: Long): UsageSnapshot {
        fullReads++
        return if (granted) snapshot.copy(nowMillis = nowMillis) else {
            UsageSnapshot.unmeasured(nowMillis, nowMillis)
        }
    }

    /** Cuantas veces se ha pedido la semana: son siete consultas y no pueden ser gratis. */
    var weekReads = 0
        private set

    override suspend fun week(nowMillis: Long, days: Int): WeeklyUsage {
        weekReads++
        return if (granted) week else WeeklyUsage(emptyList())
    }

    override suspend fun recentOpenings(
        windowMillis: Long,
        nowMillis: Long,
    ): List<AppOpening> =
        if (granted) openings.filter { it.atMillis >= nowMillis - windowMillis } else emptyList()
}
