package com.zenlauncher.zen.domain.scanner

/**
 * Por donde va el escaner. Es la unica fuente del estado de la pantalla.
 *
 * Los nueve escalones son los del enunciado y no se han inventado mas: cada uno cambia
 * lo que se ve, y ninguno es un matiz de otro.
 */
enum class ScanPhase {
    /** Pidiendo permiso, abriendo la camara o cargando OpenCV. Todavia no hay imagen. */
    INITIALIZING,

    /** Hay imagen y se busca hoja. No hay nada dibujado encima. */
    DETECTING,

    /** Hay una hoja detectada, pero todavia no vale para disparar. Se dibuja el marco. */
    DOCUMENT_DETECTED,

    /** Cuatro esquinas fiables, tamano suficiente y movil quieto: se dispara solo. */
    READY_TO_CAPTURE,

    /** La foto esta tomandose. La camara ya no acepta otra. */
    CAPTURING,

    /** Enderezando, recortando y mejorando la foto ya tomada. */
    PROCESSING,

    /** El usuario revisa el resultado y puede tocar las esquinas, girar o filtrar. */
    EDITING,

    /** Escribiendo el JPEG o el PDF en el disco. */
    SAVING,

    /** Algo fallo y hay que decirlo. Ver [ScanError]. */
    ERROR,
}

/**
 * Lo que puede salir mal, y **todo lo que puede salir mal esta aqui**.
 *
 * Ninguno cierra la aplicacion: Zen es la pantalla de inicio del telefono, asi que una
 * camara ocupada por otra aplicacion o un PDF que no cabe en el disco tienen que
 * terminar en un rotulo, nunca en un proceso muerto. Cada uno se traduce a texto en la
 * pantalla, no a un codigo.
 */
enum class ScanError {
    /**
     * No hay camara, esta ocupada o el proveedor no arranco.
     *
     * El permiso denegado **no** esta aqui: no es un fallo del escaner sino un estado de la
     * pantalla, que ensena su propio aviso con el boton de volver a pedirlo. Meterlo en
     * esta lista habria significado dos sitios distintos capaces de contar lo mismo.
     */
    CAMERA_UNAVAILABLE,

    /** OpenCV no cargo en este telefono: sin el no hay deteccion posible. */
    VISION_UNAVAILABLE,

    /** La foto se tomo pero no se pudo decodificar. */
    CAPTURE_FAILED,

    /** No se encontro ninguna hoja en la foto tomada a mano. Se pasa a edicion manual. */
    NO_DOCUMENT,

    /** La hoja detectada ocupa tan poco que enderezarla no daria nada legible. */
    DOCUMENT_TOO_SMALL,

    /*
     * La foto movida **no** esta en esta lista, y es deliberado: no es un fallo del que
     * haya que recuperarse sino una propiedad de la pagina, que se guarda igual y se
     * revisa igual. Vive en `ScanPage.sharpness` y se dice al lado de la pagina a la que
     * le pasa, no como un error suelto que taparia al siguiente.
     */

    /** No se pudo escribir el JPEG o el PDF. */
    SAVE_FAILED,

    /** No queda sitio en el disco. Se distingue de [SAVE_FAILED] porque tiene arreglo. */
    OUT_OF_SPACE,

    /** El reconocimiento de texto fallo. El documento sigue guardado igual. */
    OCR_FAILED,
}

/**
 * Por que **todavia** no se dispara solo.
 *
 * Existe para que la pantalla pueda decirlo con palabras en lugar de dejar al usuario
 * moviendo el movil sin saber que le falta. Es la misma regla que el resto de Zen: todo
 * estado se lee como texto, no solo como una forma o un color.
 */
enum class CaptureHint {
    /** No se ve ninguna hoja. */
    SEARCHING,

    /** Se ve, pero ocupa demasiado poco: hay que acercarse. */
    TOO_FAR,

    /** Se ve entera y bien, pero el movil se esta moviendo. */
    HOLD_STILL,

    /** Todo en orden: se dispara. */
    READY,
}
