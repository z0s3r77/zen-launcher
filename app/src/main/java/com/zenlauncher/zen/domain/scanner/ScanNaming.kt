package com.zenlauncher.zen.domain.scanner

/**
 * Como se llama lo que se guarda.
 *
 * Puro y con el instante por la puerta —nunca `System.currentTimeMillis()` dentro—, para
 * poder probarlo: el nombre lo ve el usuario en su galeria y en su carpeta de documentos,
 * asi que un fallo aqui es un fichero que no encuentra.
 *
 * El formato es de ordenacion natural (ano-mes-dia y hora con ceros): en una carpeta,
 * ordenar por nombre y ordenar por fecha dan lo mismo, que es lo que se espera de algo
 * llamado "Escaneo".
 */
object ScanNaming {

    const val PREFIX = "Escaneo"

    /**
     * @param stamp los campos ya extraidos del reloj del sistema, en hora local. Llegan
     *   desmenuzados y no como milisegundos porque formatear una fecha necesita zona
     *   horaria, y la zona horaria es un dato del dispositivo: si entrara aqui, esto
     *   dejaria de ser puro.
     */
    data class Stamp(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val second: Int,
    )

    fun imageName(stamp: Stamp, pageNumber: Int, totalPages: Int): String {
        val base = "${PREFIX}_${timestamp(stamp)}"
        // Con una sola pagina el sufijo sobra y ensucia el nombre; con varias es lo unico
        // que las distingue dentro de la galeria.
        return if (totalPages <= 1) "$base.jpg" else "${base}_%02d.jpg".format(pageNumber)
    }

    fun pdfName(stamp: Stamp): String = "${PREFIX}_${timestamp(stamp)}.pdf"

    fun textName(stamp: Stamp): String = "${PREFIX}_${timestamp(stamp)}.txt"

    private fun timestamp(stamp: Stamp): String = "%04d-%02d-%02d_%02d%02d%02d".format(
        stamp.year,
        stamp.month,
        stamp.day,
        stamp.hour,
        stamp.minute,
        stamp.second,
    )
}
