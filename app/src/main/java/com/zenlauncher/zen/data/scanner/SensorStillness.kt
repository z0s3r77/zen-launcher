package com.zenlauncher.zen.data.scanner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.zenlauncher.zen.domain.scanner.Stillness

/**
 * El acelerometro del telefono, traducido a "esta quieto" o "no".
 *
 * Todo lo que decide vive en [Stillness], que es puro; esto solo es el enchufe con el
 * sistema. Se registra al abrir el escaner y **se suelta al salir**: un oyente de sensor
 * que sobrevive a su pantalla es bateria gastandose para nadie, y en el proceso de la
 * pantalla de inicio eso dura hasta que se apaga el telefono.
 *
 * `SENSOR_DELAY_UI` y no `FASTEST`: hace falta saber si la mano tiembla, no medir el
 * temblor. A la velocidad maxima el sensor despierta el procesador decenas de veces mas
 * por segundo para dar la misma respuesta.
 */
class SensorStillness(context: Context) : SensorEventListener {

    private val sensors = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    private var reading = Stillness.Reading()

    /**
     * Sin acelerometro se da por quieto **siempre**.
     *
     * Es la unica respuesta razonable: la alternativa es que la captura automatica no
     * dispare nunca en un telefono sin ese sensor, y el usuario no tendria forma de saber
     * por que. Las esquinas siguen teniendo que estar quietas, asi que la condicion no
     * desaparece: se queda con una pata en lugar de dos.
     */
    val still: Boolean get() = accelerometer == null || reading.still

    fun start() {
        val manager = sensors ?: return
        val sensor = accelerometer ?: return
        reading = Stillness.Reading()
        runCatching { manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        runCatching { sensors?.unregisterListener(this) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val values = event.values ?: return
        if (values.size < 3) return
        reading = Stillness.next(reading, values[0], values[1], values[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
