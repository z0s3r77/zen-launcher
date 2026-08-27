# Zen

Un launcher de Android que convierte temporalmente el teléfono en una herramienta de
concentración. Durante una sesión Zen la pantalla de inicio deja de ser una lista de
aplicaciones y pasa a ser un cronómetro deliberadamente aburrido.

> El teléfono deja de intentar llamar tu atención.

**Versión 0.1** — aplicación normal, sin capacidades privilegiadas. Todo lo que Android
no permite hacer a una app sin privilegios está detrás de una interfaz, documentado y
sin ningún atajo. Ver [Límites reales de Android 16](#límites-reales-de-android-16).

Dispositivo objetivo: **Nothing Phone (2a)** con Nothing OS 4.1 / Android 16.

---

## Qué hace

- **Escáner de documentos** — apunta a una hoja, Zen le encuentra las cuatro esquinas en
  tiempo real, espera a que el móvil esté quieto, dispara solo, corrige la perspectiva,
  recorta el fondo y la deja con cara de escaneo. Se revisa, se ajustan las esquinas a
  mano si hace falta, se elige el modo y se guarda como imagen o como PDF de varias
  páginas. Opcionalmente lee el texto, sin conexión. Ver
  [El escáner de documentos](#el-escáner-de-documentos).
- **Lectura** — importa un PDF y lo convierte en un libro reflowable: extrae el texto,
  quita cabeceras y folios, rehace los párrafos, detecta el índice y lo deja para leer a
  página completa, con marcas, subrayado y notas. Todo local, sin conexión y sin una sola
  librería de PDF. Ver [Lectura](#lectura).
- **La home se desplaza por el medio** — pon en el inicio las aplicaciones que quieras,
  sin tope: si no caben, la pantalla baja a buscarlas. Lo que **no** se mueve nunca es la
  franja de arriba (fecha, tiempo y la cara del día) ni la fila **Menú** de abajo: el área
  desplazable vive entre las dos, así que no hay ningún sitio al que llegar arrastrando
  donde falte la salida. El menú abierto sigue ocupando el sitio de la retícula en lugar
  de alargar la página, y la lista completa de aplicaciones se abre desde la fila **Todas
  las aplicaciones**, justo debajo de la retícula.

  Zen no se desplazó durante mucho tiempo, a propósito: una pantalla de inicio que se
  arrastra deja de ser un sitio fijo, y el reloj estaba siempre en el mismo píxel. Se
  cambió porque el tope de ocho aplicaciones no tenía más razón de ser que esa: lo que no
  cabía no se podía alcanzar. Con las que caben no hay nada que desplazar y la pantalla se
  comporta exactamente como antes.
- **Salida propia en cada pantalla** — con las barras del sistema ocultas, el gesto de
  atrás lo intercepta Zen (el primer deslizamiento de Android saca las barras en vez de
  volver, así que la aplicación reconoce el arrastre desde el borde por su cuenta y
  vuelve al primer intento; las barras asoman igual un instante). Cada pantalla lleva su
  flecha de volver.
- **Launcher minimalista** — hora, fecha y una retícula de
  texto de dos columnas con las aplicaciones **que no quitan tiempo** (buscar, WhatsApp,
  teléfono, reloj, ajustes, mensajes, Spotify y el banco). Sin iconos. Hasta que se eligen
  las propias en **Ajustes Zen → Aplicaciones en el Inicio**, esa lista es la de por
  defecto y solo aparecen las que están instaladas. El resto de aplicaciones vive en una
  lista de texto con buscador, a un toque de la fila **Todas las aplicaciones**.

  Esa fila estuvo escondida tras un deslizamiento hacia arriba y el gesto **se retiró**:
  se disparaba desde cualquier punto de la pantalla de inicio —también encima de la
  retícula y con el menú abierto—, así que la lista se abría en mitad de cualquier otra
  intención. En una pantalla de inicio, lo que abre algo tiene que verse; volver desde un
  lateral sigue siendo el único gesto de Zen que **lleva** a alguna parte.
- **Colocar las aplicaciones con el dedo** — mantén pulsada una celda de la retícula y
  llévala a otro hueco. El número de la celda va diciendo dónde va a caer mientras la
  mueves, y al soltar ese orden es el mismo que numera **Aplicaciones en el Inicio**: no
  hay dos listas que puedan discrepar.

  Hace falta **mantener pulsado**, no basta con arrastrar: son celdas que se tocan
  cincuenta veces al día, y un arrastre a secas convertiría cualquier roce al sacar el
  teléfono del bolsillo en una pantalla de inicio reordenada. Notas y Lectura no se
  mueven —son las dos celdas que no son aplicaciones y su sitio no se negocia—, y una
  aplicación restringida que tuvieras guardada en el inicio **no se pierde** al reordenar
  el resto: sigue en su hueco esperando a que le levantes la restricción. Como arrastrar
  no es un gesto disponible con un lector de pantalla, cada celda ofrece además las
  acciones «Mover al hueco anterior» y «Mover al hueco siguiente».
- **Pulso de uso y aviso de distracción** — Zen mide cuánto móvil llevas hoy (tiempo de
  pantalla y desbloqueos) y solo se pronuncia cuando hay algo que decir: el pulso
  aparece bajo el reloj a partir de USO ALTO y desaparece con el día en calma. Cuando la
  forma de usarlo se vuelve compulsiva —una sentada larguísima, la misma aplicación
  abierta una y otra vez, o saltar sin parar— te lo enseña **al volver a la pantalla de
  inicio**, con las cifras delante, sin bloquear nada y sin repetirlo en hora y media.
  Necesita el acceso de uso, que se concede a mano; sin él, nada de esto existe. Ver
  [El pulso de uso y el aviso de distracción](#el-pulso-de-uso-y-el-aviso-de-distracción).
- **Sin barra de gestos** — la línea blanca del borde inferior no se dibuja: no hay
  "atrás" ni "recientes" a los que ir desde una pantalla de inicio. La **barra de estado
  sí se queda**: ocultarla quitaba los iconos de notificación, pero hacía que Android la
  sacara de golpe encima del contenido en cada gesto desde un borde, y una barra que
  aparece y desaparece llama más la atención que una que simplemente está. El precio
  asumido es que la hora, la batería y la cobertura salen dos veces en la home. El gesto
  de atrás no hace nada aquí, salvo cerrar el menú si está abierto (ver
  [límites](#la-barra-de-gestos-no-se-puede-desactivar)).
- **Botón ZEN** — a la derecha de la hora, el primero de los tres botones propios de la
  home. Todo lo
  demás (iniciar Zen con duración, notificaciones, restringidas, registro, Ajustes Zen y
  salir de Zen) está guardado tras la fila **Menú**, plegada por
  defecto. Al abrirla el menú **ocupa la pantalla entera**: se van el reloj, el botón
  ZEN, la batería y el mando, y solo queda la franja con la fecha y el estado de la
  sesión. La fila se enciende en blanco mientras está abierto, y se cierra por donde se
  abrió —la misma fila, ahora `CERRAR`— o con el gesto de atrás.
- **Botón RESPIRA** — justo debajo de ZEN, el otro botón propio de la home: un minuto
  de respiración guiada, cuatro segundos dentro y seis fuera, seis veces. La pantalla
  dibuja el minuto entero como una curva y una marca la recorre; lo ya respirado queda
  trazado en blanco y lo que falta, en gris. Un toque táctil seco marca cada cambio de
  fase para poder hacerlo con los ojos cerrados. Ver
  [por qué esos números](#respirar-un-minuto).
- **Botón NOTICIAS** — el tercero y último de la pila. Abre la portada del día de
  [La Doxa](https://noticiasdoxa.es/): el titular que resume la jornada con su párrafo, y
  los **siete puntos** de «Lo que te toca hoy» con su número, su sección, su resumen y su
  enlace a la noticia entera. Se **descarga una sola vez al día** y se queda escrita:
  volver a entrar la misma tarde no abre ninguna conexión. No hay titulares en la
  pantalla de inicio, no hay número de no leídos y nada se actualiza solo. Ver
  [Las noticias del día](#las-noticias-del-día).
- **Mando del reproductor** — anterior, pausa y siguiente para lo que esté sonando
  (Spotify incluido), con un ecualizador de cuatro barras que se mueve mientras suena
  algo y se queda quieto al pausar. **Solo aparece si hay algo que mandar**: sin audio
  y sin sesión de medios viva, la barra no se pinta. Con el acceso a notificaciones concedido
  —**opcional**, apagado por defecto— muestra además carátula, título y artista, y
  tocarlos abre el reproductor. Sin progreso arrastrable y sin botones de biblioteca:
  informa de qué suena, no invita a ponerse a elegir música.
- **Marcas de notificación** — un número junto a la aplicación en la retícula con sus
  avisos pendientes, y al tocarlo la lista de esos avisos —quién escribió y qué dice—
  sin abrir la aplicación. Nada suena, nada vibra y nada aparece encima: el número
  espera quieto y solo se ve en la pantalla de inicio. No cuenta lo que no es un aviso
  (el reproductor, una descarga) ni las cabeceras de grupo, y de las restringidas no
  enseña ninguna. Usa el **mismo acceso opcional** que la información de la canción: sin
  concederlo no hay marcas y todo lo demás funciona igual.
- **Notas** — captura de ideas, **como una aplicación más de la retícula**: tiene su
  número y se abre igual que WhatsApp. Estuvo abajo, junto a «Menú», y ahí se leía como
  una opción de administración en lugar de como el sitio donde se escribe.
  Dentro, `Nota rápida` abre un campo de texto con el cursor puesto y el teclado
  abierto: se escribe, se guarda y se vuelve **a la pantalla de inicio**, no a la lista.
  Debajo, un buscador que encuentra sin acentos ni mayúsculas, y las ideas recientes en
  orden cronológico. Sin carpetas y sin etiquetas para filtrar: en un cuaderno, lo de
  ayer está donde estaba ayer.

  Se puede **dictar**: la transcripción la hace el reconocedor **en el dispositivo** de
  Android, sin red y sin modelo empotrado, y el texto aparece según se habla. El audio
  no se guarda en ningún sitio; lo único que llega a la nota es texto. Lo dictado se
  añade detrás de lo ya escrito, así que teclado y voz se mezclan en la misma nota.

  Se pueden **adjuntar imágenes** (selector de fotos del sistema: no pide ningún
  permiso y Zen nunca ve el carrete, solo la foto elegida) y la imagen se **copia** al
  almacenamiento privado, reducida: una nota no puede perder su foto porque limpies la
  galería. Los **enlaces no tienen botón**: se reconocen solos dentro del texto al
  guardar, porque un enlace siempre llega pegado.

  La nota se guarda **tal cual**, con lo que el asistente local aún no ha deducido
  (título, resumen, etiquetas y conexiones) apuntado como pendiente. Esa es la regla que
  mantiene la captura instantánea: si generar un título pudiera retrasar el guardado, la
  idea dependería de que un modelo responda a tiempo. Ver
  [El asistente local](#el-asistente-local).
- **Buscar por significado** — el buscador encuentra primero lo que contiene lo que
  escribiste, y debajo, bajo `TAMBIÉN RELACIONADAS`, lo que se parece aunque use otras
  palabras. Son dos listas y no una: lo que contiene lo buscado y lo que se parece a lo
  buscado no son lo mismo, y mezclarlas haría dudar de si el buscador entiende.
- **Conexiones entre ideas** — el índice propone parejas de notas que hablan de lo
  mismo. Aparecen dentro de la nota, bajo `¿SE PARECE A ESTAS?` —pregunta, no afirma: el
  índice sabe que dos ideas se parecen, no si son la misma—, con `Conectar` e `Ignorar`
  del mismo tamaño. Lo que se ignora **no se vuelve a proponer nunca**. En la pantalla de
  Notas aparecen como mucho tres ideas con algo esperando respuesta: es un aviso, no una
  bandeja de entrada.
- **Aplicaciones restringidas** — se marcan y desaparecen del launcher y de la lista.
  El estado se lee como texto (`BLOQUEADA` / `LIBRE`), no como un interruptor.
- **Elegir lo que se ve en el inicio** — pantalla propia (`Ajustes Zen → Aplicaciones en
  el Inicio`, o `Elegir aplicaciones` en una home todavía sin favoritas): arriba lo que
  ya está puesto, numerado igual que la retícula y tocando se quita; abajo un buscador
  que **no lista nada hasta que se escribe**. No hay máximo: pon las que quieras y la home
  se desplaza para que quepan. El orden se cambia desde la propia home, arrastrando la
  celda; aquí se elige qué hay, no dónde está. Colgaba de Ajustes como una lista con las
  doscientas aplicaciones del teléfono, y elegir entre doscientas no es elegir.
- **Movimiento, solo cuando algo cambia** — abrir una pantalla, abrir el menú o que
  aparezca el mando del reproductor se anima 180 ms; volver, 120. Nada corre en bucle ni
  se mueve sin que lo hayas provocado, y los desplazamientos son 1/10 de la pantalla:
  indican de dónde viene algo, no lo pasean. Si bajas o apagas la escala de animación de
  Android, Zen se queda sin movimiento sin preguntar nada (comprobado en dispositivo).
  Todo en `ZenMotion`.
- **Sesiones Zen** — 15, 30, 60, 90, 120 minutos o duración personalizada. Durante la
  sesión, pulsar Inicio lleva al cronómetro, no a las aplicaciones.
- **Registro** — tiempo total, completadas, abandonadas, batería consumida, sesión más
  larga, media y porcentaje de completadas. Sin rachas, sin logros, sin objetivos.

## Respirar un minuto

**Botón RESPIRA → un minuto, seis respiraciones, y fuera.** Sin cuenta de días, sin
racha, sin felicitación al terminar: la pantalla dice `Hecho` y ya está.

El patrón es **4 s inspirando / 6 s espirando**, sin apneas, seis veces. No es un ritmo
elegido por estética:

- **Seis respiraciones por minuto (0,1 Hz)** es la *frecuencia de resonancia* del
  sistema cardiovascular: a ese ritmo la respiración entra en fase con la onda de
  presión arterial, la variabilidad de la frecuencia cardiaca llega a su máximo y la
  ganancia barorrefleja es la más alta que se mide a cualquier otro ritmo (Vaschillo et
  al. 2002; Lehrer y Gevirtz, *Frontiers in Psychology*, 2014). Es el ritmo sobre el que
  se construye el biofeedback de HRV.
- **La espiración más larga que la inspiración** porque el freno vagal sobre el corazón
  actúa al soltar el aire: el pulso baja espirando y sube inspirando. Alargar la
  espiración es lo que inclina el equilibrio hacia el lado parasimpático, no el simple
  hecho de respirar despacio (Zaccaro et al., *Frontiers in Human Neuroscience*, 2018;
  Laborde et al., 2022).
- **Sin retenciones.** Los patrones "en caja" (4-4-4-4) bajan el ritmo a base de aguantar
  el aire, y a quien llega agitado la retención le sube la sensación de ahogo. El
  cociente entre inspirar y espirar es lo que hace el trabajo.
- **Un minuto** porque es la dosis que de verdad se toma. El efecto sobre la
  variabilidad cardiaca aparece en las primeras respiraciones lentas, no al cuarto de
  hora, y los ensayos sobre práctica breve diaria (Balban et al., *Cell Reports
  Medicine*, 2023) trabajan con cinco minutos al día. Seis ciclos de diez segundos
  entran exactos en 60 000 ms: el minuto no corta a mitad de una respiración.

**Lo que Zen no hace aquí**: no mide nada. No hay sensor de pulso, ni cámara, ni
micrófono en juego —marcar el ritmo no necesita ninguno—, así que la pantalla guía y no
promete un resultado. No es un tratamiento, y el aviso al pie lo dice: por la nariz, sin
forzar, y si falta el aire se respira menos hondo.

**Cómo está hecho**: [`BreathingPattern`](app/src/main/java/com/zenlauncher/zen/domain/breathing/BreathingPattern.kt)
es una función pura del tiempo transcurrido —amplitud de la curva, fase, ciclo y
segundos restantes—, así que el patrón entero se prueba en la JVM sin esperar un minuto
ni dibujar un fotograma. La pantalla lleva la cuenta con el **reloj de fotogramas**
(`withFrameMillis`) y no con una animación de 60 000 ms: Compose escala las animaciones
con `animator_duration_scale`, y con las animaciones apagadas en opciones de
desarrollador un `Animatable` habría terminado el minuto en cero segundos. El valor del
cronómetro se lee **dentro del dibujo**, no en la composición: la marca va a sesenta
fotogramas por segundo y la pantalla se recompone una vez por segundo.

El toque táctil de cada cambio de fase usa la respuesta háptica del sistema
(`performHapticFeedback`), **no** el permiso `VIBRATE`: quien la tenga apagada en
Android no siente nada y el ejercicio funciona igual. Mientras corre, la pantalla se
mantiene encendida (`keepScreenOn`) y se suelta al salir o al parar.

---

## Las noticias del día

**Botón NOTICIAS → la portada de [La Doxa](https://noticiasdoxa.es/), una vez al día.**
Arriba, el titular que resume la jornada con el párrafo que lo explica; debajo, los
**siete puntos** de «Lo que te toca hoy», cada uno con su número, su sección, su resumen
y una salida a la noticia entera en el navegador.

**Una descarga al día, y el corte es el día natural.** Si lo guardado es de hoy, entrar
en la pantalla no abre ninguna conexión: se lee lo que ya está escrito. El corte no son
veinticuatro horas desde la última descarga, porque con un intervalo así quien mira las
noticias a las once de la noche leería la portada de ayer durante toda la mañana
siguiente; cambiar de día es exactamente el momento en que hay algo nuevo que traer. Es
[`NewsRefresh`](app/src/main/java/com/zenlauncher/zen/domain/news/NewsRefresh.kt), una
función pura, y el botón **ACTUALIZAR** del pie es la vía de bajarla a mano.

**La portada se queda escrita.** Se guarda entera en DataStore como una cadena JSON, así
que sobrevive a que el sistema mate el proceso del launcher —que pasa constantemente— y
sigue ahí cuando no hay cobertura. Un fallo de red **no borra** lo anterior: se enseña la
última portada que se bajó, con `PORTADA DE OTRO DÍA` encima y la fecha de descarga al
pie. Es la misma regla que el resto de la aplicación: un dato viejo con la misma cara que
uno de ahora es una mentira, así que se enseña diciendo lo que es.

**No hay canal que pedir: hay que leer la página.** Se comprobó en el propio sitio que no
publica `rss.xml`, `feed.xml`, `index.json` ni ningún `sitemap` con contenido —solo un
índice de URLs—, así que la única fuente es el HTML de la portada. Se descarga entera
(unos 30 kB) y la traduce
[`DoxaPortada`](app/src/main/java/com/zenlauncher/zen/data/news/DoxaPortada.kt), un
objeto **puro**: sin red y sin Android, probado contra una portada real guardada en
`app/src/test/resources`. Con expresiones regulares y no con un analizador de HTML,
porque meter jsoup por una página son cientos de kilobytes en el proceso que menos puede
permitirse morir en este teléfono. El día que el sitio cambie de marcado, esto devolverá
`null` y la pantalla dirá que no se pudo leer, en lugar de enseñar media portada.

**Lo que no se entiende se descarta, no se rellena.** Un punto sin enlace, sin título o
sin resumen no se pinta a medias. Y los enlaces se filtran antes de salir: lo que acaba
en un `ACTION_VIEW` tiene que ser una dirección `https` **del propio dominio**, así que
un `href` con otro esquema —`javascript:`, `intent:`— o de otro sitio se tira. Zen enlaza
a la noticia que resumió, no a lo que aparezca en el atributo.

**Lo que Zen no hace aquí**: no hay titulares en la pantalla de inicio —serían algo que
invita a leer cincuenta veces al día, justo lo que este launcher evita—, no hay número de
no leídos, no hay tirar para refrescar, no hay «cargar más» y no hay nada que se
actualice solo. Tampoco se lee la noticia dentro de Zen: el punto trae su resumen, que es
lo que se viene a leer, y quien quiera la pieza entera sale al navegador. Zen no es un
lector de noticias.

**La petición no lleva nada tuyo**: un `GET` sin parámetros, sin clave, sin cuenta y sin
identificador. Lo único que el servidor puede saber es que alguien pidió su portada.

---

## El asistente local

Todo el procesamiento de las notas ocurre **en el dispositivo**. No hay ninguna API de
pago, ningún servidor y ninguna cuenta. Lo que se escribe no sale del teléfono.

Está montado en tres niveles detrás de dos interfaces —`EmbeddingModel` e
`IdeaAssistant`—, para que cambiar de motor no obligue a rehacer ninguna pantalla:

| Nivel | Peso | Qué hace |
| --- | --- | --- |
| **0 — de fábrica** | 0 MB | Kotlin puro: normalización del castellano, búsqueda, conexiones por vocabulario y raíz compartida, temas recurrentes y andamios de pensamiento |
| **1 — opcional** | ~200 MB | EmbeddingGemma (308M): conexiones semánticas aunque dos notas no compartan ni una palabra |
| **2 — opcional** | 0,5–2,6 GB | Un Gemma pequeño: título, resumen, etiquetas, preguntas y propuestas de proyecto |

**Zen funciona entero en el nivel 0.** Los niveles 1 y 2 se activan a mano desde
`Ajustes Zen`, con el tamaño escrito antes de descargar nada y un botón para borrarlos.
Si el modelo no está, se corrompe o el proceso muere, se cae al nivel inferior sin
avisar de nada. Es la misma regla que el resto de Zen: degradar siempre, nunca bloquear.

El nivel 0 ya está montado y funcionando: `LexicalEmbedder` reparte las raíces de cada
nota en un vector de 512 posiciones con el truco del hashing (con signo, para que las
colisiones se cancelen en vez de acumularse), y `SemanticIndex` compara por fuerza bruta
—con unos miles de notas son unos pocos millones de multiplicaciones, y un índice
aproximado sería mucho código para ahorrar milisegundos que nadie percibe.

**Su límite está medido y no se disimula**: relaciona notas que comparten vocabulario o
familia de palabras, pero no relaciona dos ideas que hablan de lo mismo sin compartir ni
una palabra («aburrirse» ↔ «los momentos muertos», 0,00). Eso es exactamente lo que
resuelve EmbeddingGemma detrás de la misma interfaz.

El umbral (**0,18**) vive en el propio motor y no en quien compara: dos motores no
reparten las semejanzas en la misma escala, y un número fijo en el código daría conexiones
absurdas al cambiar de modelo. Está ajustado con datos y no a ojo — sobre pares reales,
lo que debe conectarse va de 0,21 hacia arriba y lo que no, a 0,00. Si alguna vez se
solapan, el arreglo es la lista de palabras vacías, **no bajar el umbral**: una conexión
que no viene a cuento enseña a ignorar la sección entera, mientras que una que falta solo
deja una nota sin compañía.

El id del motor lleva **versión del cálculo** (`lexico-v2`), no solo del motor: `NoteIndexer`
únicamente reindexa lo que no tiene vector de ese id, así que cambiar tokens, raíces,
palabras vacías, pesos o dimensiones **sin subir la versión** deja los vectores viejos ahí
para siempre, calculados con las reglas de antes.

El nivel 2 vive en **otro proceso** (`:ia`). No es un detalle de rendimiento: Zen es la
pantalla de inicio, y cargar más de un giga de modelo en su proceso significaría que
Android, al quedarse sin memoria, mata el launcher y deja el móvil sin home. Aislado,
lo que muere es el asistente.

Nada de esto se ejecuta durante la captura. Guardar una nota es escribir en SQLite y
volver; lo que el asistente deduzca llega después, y una nota sin procesar se lee igual.

## El pulso de uso y el aviso de distracción

Zen mide **cuánto móvil llevas hoy** y, cuando la forma de usarlo se vuelve compulsiva,
te lo enseña una vez y se aparta. Es opcional de principio a fin: sin el acceso de uso
concedido a mano en Ajustes de Android, nada de esto existe y el launcher funciona
igual.

**Dos varas, y manda la peor.** El tiempo de pantalla dice cuánto te ha costado el
móvil; los desbloqueos, cuántas veces lo has necesitado. Coger el teléfono ciento veinte
veces para mirar nada es una conducta compulsiva aunque el total del día sea de una
hora, y al revés. El resultado son cuatro escalones —CALMA, NORMAL, USO ALTO, EXCESO— y
no un porcentaje: un número fino invita a optimizarlo, y Zen no quiere que nadie juegue
a bajar su marca.

**La cara del día está siempre a la vista, y no ocupa nada.** En la esquina derecha de
la franja superior de la pantalla de inicio hay dos caracteres —`:)`, `:|`, `:(`, `:O`—
que resumen cómo va el día. Ahí antes ponía `SIN SESIÓN`, que en esa pantalla es una
**constante**: si hubiera una sesión, la sesión sustituye a la pantalla entera y eso no
se ve. Cambiar un rótulo que no puede decir otra cosa por uno que sí es la única forma de
meter algo permanente en la home sin que la home crezca.

Es texto, no un icono: se dibuja con la misma tipografía monoespaciada que el resto y no
trae un mapa de bits, ni color, ni la marca de nadie. Mira las tres cosas que uno se
pregunta al mirar si va bien —**cuánto tiempo** lleva, **cuántas veces** ha cogido el
teléfono y **en qué** se le está yendo—. Esa tercera es la que añade sobre el escalón:
dos horas repartidas entre el correo, el banco y los mensajes son dos horas de usar el
teléfono; dos horas en las que el 80% se lo lleva una sola aplicación hecha para que te
quedes son otra cosa, y el reloj no las distingue. Por eso una aplicación que acapara
baja la cara aunque el tiempo diga que el día va normal.

Todo resumen del día sale de esa misma cara: el glifo en la franja, la palabra en el
menú y en la pantalla de Uso, y **si aparece o no el pulso**. Cuando cada uno se
calculaba por su cuenta se contradecían: en el dispositivo salió una cara `:(` —porque
Instagram se llevaba el 77% de las dos horas— junto a un `NORMAL` que venía solo del
reloj, y al tocar la cara se llegaba a una pantalla que también ponía NORMAL. Un resumen
que no concuerda con su detalle no se cree.

Sin acceso de uso concedido la cara es `:?` y no `:)`: felicitar por un día que no se ha
medido sería la misma mentira que enseñar un cero. Y como `:)` es texto pero no es texto
que se pueda leer en voz alta, el glifo viaja siempre con su descripción en palabras;
tocarlo lleva al detalle.

**El pulso no es una cuarta fila permanente.** Vive bajo la misma regla que el mando del
reproductor —lo que no tiene nada detrás no se pinta— y solo aparece bajo el reloj a
partir de USO ALTO, con las dos cifras que decidieron el escalón. Un día tranquilo deja
la pantalla de inicio exactamente como estaba. La puerta que existe siempre es la fila
**Uso del móvil** del menú, que ya lleva el escalón escrito a la derecha sin abrir nada.

**El aviso mira ventanas cortas, nunca el total del día.** Cuatro horas repartidas por
la jornada no son lo mismo que cuarenta minutos seguidos, aunque el reloj diga cuatro
horas en los dos casos. Se reconocen tres formas, y el aviso las nombra porque decirle a
alguien "llevas mucho móvil" no le dice nada:

| | Qué es | Cuándo salta |
|---|---|---|
| **Arrastre** | Una sola sentada muy larga en la misma aplicación | 40 min dentro de la última hora |
| **Repetición** | La misma aplicación abierta una y otra vez | 5 aperturas en media hora |
| **Picoteo** | Saltar de aplicación en aplicación sin quedarse | 12 aperturas en un cuarto de hora |

Las aplicaciones **que no quitan tiempo** están exentas del arrastre y de la repetición:
una hora de navegador GPS conduciendo, o una llamada larga, son tiempo de pantalla y no
son una recaída. Es la misma lista que llena la retícula por defecto, leída al revés.

**El aviso llega al volver a la pantalla de inicio, y no dentro de otra aplicación.**
Interrumpirte en mitad de lo que estás haciendo sería hacer exactamente lo que Zen
critica; además, no hay servicio en segundo plano ni sondeo, así que el momento en que
el dato cambia y el momento en que alguien puede leerlo son el mismo. Sustituye a la
pantalla entera, como la sesión activa, y enseña las cifras primero y el texto después:
lo que hace que alguien se pare es reconocer el dato, no leer un sermón.

**No bloquea nada y no castiga.** Se sale con "Seguir como estaba", con el gesto de
atrás y arrastrando desde el borde: tres salidas. Las dos alternativas que ofrece
—respirar un minuto, empezar una sesión— son las dos cosas que Zen ya sabe hacer, no una
penitencia inventada para ese momento. Después se calla hora y media: un aviso que salta
cada vez que vuelves a la pantalla de inicio se aprende a descartar en dos días, y a
partir de ahí no dice nada. Durante una sesión Zen no aparece nunca.

**Nada en disco, tampoco para la semana.** Guardar un histórico propio obligaría a
escribir qué hace el usuario con cada aplicación, día a día, y eso es justo lo que Zen no
quiere tener. Los días anteriores se le vuelven a pedir a Android cada vez que se abre la
pantalla de la semana; lo leído vive en memoria mientras dura la consulta y se va con
ella. Lo único que se persiste es un número: cuándo se enseñó el último aviso.

El precio es que **la ventana no la decide Zen**: si el sistema ya no conserva un día,
ese día llega sin medir y la gráfica lo dibuja distinto, en lugar de pintar un cero que
sería mentira.

## La semana y tu patrón

La pantalla de Uso lleva a **Semana**, que responde a la única pregunta con la que se
entra ahí: *¿esto lo tengo controlado?*

**La gráfica es la única de toda la aplicación**, y existe porque "¿voy a más o a menos?"
no cabe en un número: siete barras lo contestan de un vistazo y siete cifras en fila no.
Es monocroma y no tiene ejes; su única referencia es **la línea del umbral de USO ALTO**,
que es justo lo que convierte el dibujo en información —los días que la pasan se ven sin
contar nada—. La escala nunca baja de ese umbral, así que una semana tranquila sale con
barras bajas de verdad en lugar de estirarse hasta arriba y aparentar un problema que no
existe. Cada barra dice su día y su tiempo en voz alta para quien no la vea.

**El veredicto va antes que el detalle**: SÍ / JUSTITO / NO, en una palabra, y se calcula
aplicando los mismos umbrales del día a la media diaria —no hay una segunda tabla de
números que se pueda desincronizar de la primera.

Debajo, hasta **tres observaciones sobre tu patrón**, cada una anclada a un número real:

| | Cuándo aparece | Qué ofrece |
|---|---|---|
| **Ladrona** | Una app se lleva ≥35% de la semana **y** ≥45 min/día | Restringirla |
| **Repetida** | Una app se abre ≥20 veces al día de media | Restringirla |
| **Subiendo** | Los dos últimos días van ≥25% por encima | Nada: es una observación |

Cuatro restricciones sobre lo que **no** puede hacer, que es donde está el diseño:

- **Ninguna observación sin su cifra.** "Usas mucho Instagram" no es información;
  "Instagram se lleva el 47% de tu semana" sí. Lo que hace que alguien cambie algo es
  reconocer el dato, no que le digan lo que tiene que hacer.
- **Ninguna cifra sin salida.** La acción que se ofrece no está inventada para la
  ocasión: es una palanca que Zen ya tenía —Aplicaciones restringidas—. Una recomendación
  que no lleva a ninguna parte es un sermón.
- **Nunca dos observaciones sobre la misma aplicación**, ni más de tres en total. Decir
  "Instagram se lleva el 47%" y debajo "abres Instagram 40 veces al día" es el mismo
  hallazgo escrito dos veces, y hace parecer que hay dos problemas donde hay uno.
- **Con menos de dos días medidos no se concluye nada.** Un día no es un hábito; llamar
  ladrona a una aplicación porque ayer viste una serie sería adivinar, y decir que todo va
  bien con un solo día también. La pantalla distingue "no hay patrón" de "todavía no se
  puede saber".

Sobre lo que ya está restringido no se opina: recomendar restringir lo que ya lo está es
ruido con forma de consejo.

## Lectura

Un PDF de la carrera se abre en el móvil y lo que sale es una hoja A4 a la que hay que
hacer zoom y arrastrar en dos ejes para leer una columna. **Lectura no enseña el PDF: lo
convierte en un libro.**

Al importar, Zen extrae el texto, tira las cabeceras y los folios que se repiten en cada
hoja, vuelve a unir las palabras partidas con guion, reconstruye los párrafos y busca el
índice. Lo que queda es texto **reflowable**, como un ebook: se adapta al ancho de la
pantalla y al tamaño de letra que pongas, y por eso el sitio de lectura se guarda en
bloques y no en páginas —«página 87» significa otra cosa en cuanto subes el cuerpo dos
escalones—. La página se sigue enseñando, porque es como se habla de un libro, pero se
deriva del bloque en lugar de ser el dato.

### Sin ninguna librería de PDF

`android.graphics.pdf.PdfRenderer.Page.getTextContents()` extrae el texto de una página,
y llegó en **Android 15 (API 35)**. Comprobado sobre `android.jar` antes de escribir una
línea. Hasta esa versión, sacar texto de un PDF en Android obligaba a empotrar PdfBox o
iText —varios megabytes y una dependencia más en el arranque del launcher— para
reimplementar lo que el sistema ya trae.

`minSdk` es 34, así que hay un escalón de una versión en el que Zen instala y Lectura no
puede funcionar: en ese caso **el botón de añadir libro no existe** y la pantalla lo dice.
Hacer elegir un fichero para luego no poder abrirlo es peor que no ofrecerlo. El
dispositivo objetivo va con Android 16.

### La detección es heurística y puede fallar sin romper nada

No se da por supuesto ningún formato. El índice impreso se busca por sus dos formas
reales —con puntos conductores y con espacios hasta el número— y se descarta si los
números no suben, porque una bibliografía con años al final se le parece mucho. Los
títulos del cuerpo se detectan por forma: línea sola, corta, en mayúsculas o con
numeración de capítulo.

Todo esto vive en funciones puras (`TextReflow`, `HeadingDetector`, `TableOfContents`,
`BookMetadata`, `BookBuilder`) y se prueba contra texto guardado, nunca contra un fichero
real, igual que el análisis de la portada de noticias. **Lo que no se entiende se
descarta en lugar de rellenarse.** Y siempre queda una forma de moverse por el
documento: si no hay índice impreso se navega con los títulos detectados, y si tampoco
hay títulos, el lector ofrece saltar por páginas.

Android **no da metadatos** de un PDF —`PdfRenderer` no expone el diccionario `/Info`—,
así que el título y el autor se leen de la portada como los leería una persona, y el
nombre del fichero es el último recurso. Si no se deduce autor, la ficha no pinta esa
línea: un «autor desconocido» es texto que ocupa sitio para no decir nada.

### El PDF no se copia, y no sale del teléfono

Se guarda el texto ya entendido —unos dos megabytes para un libro de 350 páginas, frente
a los diez o veinte del PDF— más la referencia al fichero elegido, con permiso
persistente. El lector no vuelve a abrir el original nunca: si mueves o borras el PDF, lo
que ya importaste se sigue leyendo.

**Lectura no usa `INTERNET`.** Importar, extraer, detectar la estructura, leer, buscar y
guardar el progreso funcionan enteros sin conexión. El selector de documentos del sistema
no pide ningún permiso: devuelve solo el fichero que elijes, así que Zen nunca ve el
almacenamiento entero.

### El lector: página completa y nada más

Fondo negro puro, serif del sistema, márgenes anchos. Mientras lees **no hay nada más que
el libro**: ni franja de cabecera, ni barra de página, ni mandos. La hoja ocupa la pantalla
entera. Al tocar el centro aparece todo —volver, marcar, ajustes, índice, buscar, marcas y
los botones de pasar hoja— y al volver a tocarlo se va.

Es la única pantalla de Zen que esconde su propia salida, y se sostiene porque siguen
existiendo dos: el botón al despertar la pantalla y el arrastre desde el borde, que es el
gesto de volver de toda la aplicación. Los mandos se dibujan **encima** del texto en lugar
de empujarlo, así que la línea que estabas leyendo no se mueve de sitio al abrirlos.

**Se pasa página, no se desplaza.** Tercios laterales para pasar hoja, tercio central para
despertar los mandos. Tocando y no deslizando por una razón concreta: `ZenScreen` ya usa el
arrastre horizontal para volver, y un hijo que lo consumiera dejaría el lector sin salida.

Las páginas **no se calculan todas de golpe**: medir un libro entero son segundos de espera
dentro del proceso del launcher, y habría que repetirlo con cada cambio de cuerpo de letra.
Se compone la hoja que estás mirando, y retroceder se resuelve midiendo un poco por detrás
en vez de guardando una pila de páginas visitadas —esa pila se quedaría vacía justo después
de saltar desde el índice, que es donde más falta hace poder volver una hoja—. Por eso el
número que se enseña sigue siendo **la página del PDF**: es estable, no cambia al tocar el
cuerpo de letra y es la que se cita en clase.

La serif es **la única tipografía de Zen que no se empaqueta**: Archivo ya son 643 KB, y
aquí la elección no es de marca sino de oficio. Los ajustes son del lector y no de cada
libro: quien encuentra su tamaño de letra lo quiere en todos.

### Marcar, subrayar y anotar

Marcar una página cuesta un toque, y el mismo botón la quita si ya estaba marcada. La marca
guarda **un trozo del texto**: una lista que dijera solo «página 87» obliga a saltar a cada
entrada para saber cuál era la que buscabas.

Para subrayar se mantiene pulsado, y lo que se coge es **la frase** de debajo del dedo;
`MÁS` va añadiendo las siguientes. No hay manillas de arrastre a propósito: en un móvil se
arrastra tapando con el dedo justo lo que quieres marcar, y en un libro de filosofía lo que
se subraya casi siempre es una frase entera. La detección de frases no parte en «cfr.» ni
en «op. cit.» —un punto solo cierra frase si detrás viene un espacio y una mayúscula— y se
lleva dentro el cierre de comillas de una cita.

**Subrayar y anotar son la misma cosa**, con y sin texto detrás. Separarlas obligaría a
elegir antes de saber si vas a tener algo que decir, y dejaría dos listas hablando del mismo
párrafo. En la hoja, lo subrayado va con un fondo apagado de la misma escala de grises —el
ámbar está reservado a las marcas de estado de 6dp— y lo que lleva nota va además subrayado
de verdad, para distinguirlo sin abrir nada. Todo junto se repasa en **MARCAS**, en orden de
lectura y no por fecha: una lista de marcas es un recorrido del libro.

### Lo que todavía no hace

- **PDF escaneados**: si el fichero son imágenes, no hay texto que extraer y la
  importación lo dice con esas palabras, en lugar de crear un libro vacío. El OCR sería
  **otra implementación de `PdfTextSource`**, que es donde está el hueco.
- **Modo estudio**: seleccionar un fragmento y pedir que lo expliquen, lo resuman o lo
  relacionen. La frontera existe hoy y está vacía a propósito (`StudyModel`): el lector ya
  sabe convertir un bloque en un `Passage`, que es la única pieza que un modelo necesita.
  Una implementación local no tendría ninguna atadura; una que usara un servicio externo
  sería **explícita y opcional**, y sería el tercer consumidor de `INTERNET` de toda la
  aplicación —una decisión de producto, no un detalle.

---

## El escáner de documentos

Se entra desde **Menú → Escanear**, y solo desde ahí. No es una celda de la retícula ni
una fila fija de la pantalla de inicio: la home no crece, las dos únicas celdas que no
son aplicaciones ya son Notas y Lectura, y una tercera empujaría el reloj fuera de su
sitio. Escanear además se hace de vez en cuando —un recibo, unos apuntes, un impreso—,
no cincuenta veces al día, que es el perfil exacto de lo que vive plegado en el menú.

### Lo que hace, en orden

1. **Detecta la hoja en cada frame** y dibuja el marco encima de la cámara.
2. **Espera a que el encuadre sirva**: cuatro esquinas fiables, la hoja ocupando al menos
   una cuarta parte de la imagen, el móvil quieto y las esquinas sin saltos durante diez
   frames seguidos.
3. **Dispara solo** al cumplirse todo. También hay obturador, y un modo MANUAL que apaga
   el disparo automático.
4. **Corrige la perspectiva** con una homografía y **recorta** al borde del papel.
5. **Mejora la imagen**: quita la sombra, aplana la iluminación y deja el fondo blanco.
6. **Se revisa**: ajustar las cuatro esquinas arrastrándolas, girar, cambiar de modo,
   repetir la foto o pasar a la siguiente página.
7. **Se guarda** como imagen en `Imágenes/Zen` o como **PDF** en `Documentos/Zen`, una
   página del PDF por escaneo.
8. **Opcionalmente lee el texto** de la página, en el propio teléfono y sin conexión.

En todo momento el estado se lee **como texto** —BUSCANDO UNA HOJA, ACÉRCATE MÁS, SUJETA
QUIETO, LISTO—, no solo como un marco que cambia de tono.

### La detección es visión por computador clásica, no un modelo

Gris, reducción de ruido que conserva bordes, detección de bordes, contornos, polígonos
de cuatro vértices y validación de forma. Sin modelo, sin red y sin nada aprendido.

Los dos umbrales de Canny **no son constantes**: salen del corte que calcula Otsu sobre
la propia imagen. Es lo que hace que funcione igual en una cocina de noche y junto a una
ventana; fijar 50 y 150 obliga a elegir una iluminación y fallar en las demás.

Hay **dos estrategias y se prueban en orden**. La primera busca el canto del papel como
un salto de brillo, y es la buena con una hoja sobre una mesa de otro color. Si no
encuentra nada entra la segunda, que separa papel y fondo por brillo con Otsu y salva el
caso contrario: folio blanco sobre mesa clara, donde casi no hay canto. La barata primero
porque esto corre quince veces por segundo.

Se mira **la imagen reducida a 480 px de lado largo**, y no solo por velocidad: a
resolución completa el grano del papel y la trama del texto son bordes tan válidos como
el borde de la hoja, y el trazado de contornos se pierde entre ellos.

### Las proporciones se recuperan, no se miden

Es la parte que más se nota y la que más fácil sale mal. Un A4 fotografiado de lado se
proyecta como un trapecio, y estirar ese trapecio hasta el rectángulo que envuelve sus
esquinas da un documento **aplastado o estirado**: los lados que están más lejos de la
cámara salen más cortos de lo que son, y la media de los lados hereda ese error.

Zen recupera la proporción real con la solución cerrada de Zhang y He: si se sabe que el
original **era** un rectángulo, la forma del trapecio contiene a la vez la distancia
focal de la cámara y la relación ancho/alto, y las dos se despejan sin calibrar nada.

Hay un caso en el que eso **no se puede**: cuando el móvil está alineado con la hoja en
uno de los dos ejes, uno de los pares de lados sale paralelo en la imagen, no tiene punto
de fuga y la focal deja de estar en la foto. No es raro —es lo que hace quien apoya los
codos y mira la mesa desde arriba—. Ahí se supone una focal de cámara de móvil corriente
(unos 60° de campo). Es una suposición declarada, y aun así acierta mucho más que medir:
sobre cámaras sintetizadas con focales muy distintas de la supuesta, el error se queda
por debajo del 21 % en el peor caso, frente al 53 % de la media de los lados. Está fijado
en `DocumentAspectTest`.

### El modo Documento divide, no resta

Los cinco modos son Original, Documento, Blanco y negro, Alto contraste y Escala de
grises. El que importa es **Documento**.

Estima **la luz que había** —un cierre morfológico con un elemento más grande que
cualquier letra se come el texto y deja solo el degradado de la iluminación— y divide la
imagen por esa estimación. Es una división y no una resta porque la sombra **multiplica**
la luz que llega al papel: restando, el texto de la zona oscura se aclararía tanto como
el fondo y se perdería. Después se estiran los niveles entre percentiles de la propia
imagen, no entre su mínimo y su máximo: un solo píxel quemado por un reflejo fijaría el
rango entero y el estirado no haría nada.

### El original no se toca nunca

Cada página guarda **tres ficheros**, y cada uno está por algo:

- la **foto tal cual salió de la cámara**, que es lo que permite volver a mover las
  esquinas media hora después sin haber perdido nada. Sin ella, arrastrar una esquina
  hacia fuera sacaría píxeles que ya no existen;
- la **hoja enderezada sin filtro**, de la que salen todos los modos, para que cambiar de
  filtro nunca degrade lo anterior;
- la **enderezada con el modo puesto**, que es lo que se ve y lo que se guarda.

Cambiar de modo **no vuelve a enderezar**: sería repetir lo más caro para nada.

Los tres viven en la caché privada de la aplicación, no en `filesDir`, y **se borran al
salir del escáner**. Un escaneo a medias no es un documento del usuario: si el disco se
llena, el sistema puede tirarlos él mismo en lugar de avisar de que se ha quedado sin
sitio por culpa del launcher.

### El OCR es local y el PDF lleva capa de texto

ML Kit con el modelo **dentro del APK**, no descargado por Google Play Services: en un
teléfono recién estrenado y sin red funciona igual. Escritura latina, que es la del
castellano. Se ejecuta **después** de enderezar y filtrar, porque el reconocimiento
mejora mucho con el texto ya recto y el fondo ya blanco.

**Zen sigue teniendo dos consumidores de `INTERNET`, el tiempo y las noticias.** Ni la
detección, ni el enderezado, ni el OCR abren una conexión.

Si hay texto reconocido, el PDF lo lleva como capa seleccionable. El texto se pinta
**primero y la imagen encima**, y no al revés con tinta transparente: `PdfDocument` dibuja
a través de Skia, que no expone el modo de renderizado invisible del formato PDF —el modo
3, que es como lo hacen las herramientas de escritorio— y además puede descartar del todo
un trazo con alfa cero. Pintando debajo, el texto queda igual de escondido a la vista y
sigue estando en la capa de texto del documento, que es de donde lo saca el visor al
seleccionar o al buscar.

### El precio: es la única dependencia nativa, y pesa

OpenCV son **24,7 MB** de `.so` y el modelo de OCR otros **11 MB**. El APK pasa de unos
8 MB a **73 MB**, y son megabytes cargados en el proceso de la **pantalla de inicio**,
que es justo el que el sistema no debería tener que matar (ver
[la RAM](#la-ram-del-teléfono-no-se-puede-limpiar)).

Se compensa por tres vías, y ninguna es opcional:

- **`abiFilters` a `arm64-v8a`**, la del Nothing Phone (2a). Empaquetar las otras tres
  ABI multiplicaría el peso por cuatro para nada. Consecuencia: `connectedDebugAndroidTest`
  necesita un dispositivo arm64; en un emulador x86_64 hay que añadir `"x86_64"` a esa
  lista en `app/build.gradle.kts`.
- **Todo es perezoso.** El detector, el procesador, el reconocedor de texto y el almacén
  se construyen la primera vez que se usan: quien no abre el escáner no paga ni un byte.
  Es la misma cuenta que el tiempo y las noticias, solo que aquí la factura es mucho más
  alta.
- **El ViewModel cuelga de la entrada de navegación**, no del ámbito de la Activity: al
  salir del escáner se limpia solo, y con él se van el detector, el modelo de OCR y la
  memoria nativa de los `Mat`.

  **Lo que no se recupera es la biblioteca nativa.** Una vez que `System.loadLibrary` ha
  corrido, se queda mapeada hasta que muera el proceso: Java no tiene forma de
  descargarla. Medido en el dispositivo, tras usar el escáner y volver a la pantalla de
  inicio el proceso conserva unos 15 MB de `.so mmap`. La pereza sirve porque quien nunca
  abre el escáner no paga nada, y quien lo abre paga una vez; no porque se pueda devolver.

Además, la memoria nativa se suelta a mano. Un `Mat` vive fuera del montón de Java y el
recolector de basura no lo ve; la detección crea media docena por frame quince veces por
segundo. Sin soltarlos, el escáner reserva cientos de megabytes en un minuto. Ver
`MatScope`.

### Rendimiento: tres ritmos, no uno

- **Por frame**, en el hilo de análisis de CameraX: solo detectar y decidir. Nada grande
  reservado, nada de disco y nada en el hilo principal. Además se limita a unos 15 frames
  por segundo: la mano no se mueve más rápido que eso y cada frame de más es batería.
- **Por captura**: enderezar, filtrar y guardar. Caro, pero pasa una vez y el usuario ya
  está esperando.
- **A petición**: OCR y exportar.

El análisis pide **640×480** y la captura **la mayor resolución disponible**. Detectar
sobre frames de 12 megapíxeles sería mover 48 MB por frame para tirarlos; capturar a
640×480 daría un documento ilegible.

### Qué no hace

- **No comprime en lote ni sube nada a ninguna parte.** No hay cuenta, ni carpeta
  sincronizada, ni "mis documentos" dentro de Zen: lo escaneado sale a la galería y a la
  carpeta de documentos del teléfono, y ahí se acaba la responsabilidad de un launcher.
- **No guarda un historial de escaneos.** Al salir, lo que no se guardó se borra.
- **No reordena las páginas** ni permite insertar una en medio. El PDF sale en el orden
  en que se escanearon.
- **No lee códigos QR ni de barras**, aunque OpenCV sepa. Sería una segunda función
  metida en la misma pantalla.

---

## Cómo se sale

Zen no bloquea nada en v0.1: recientes, panel de notificaciones y ajustes rápidos
siguen funcionando con Zen como pantalla de inicio, y la barra de gestos vuelve un
momento deslizando desde el borde inferior. Para devolver la pantalla de inicio
al launcher anterior: **Menú → Salir de Zen** (o **Ajustes Zen → Cambiar la pantalla de
inicio**), que abre el selector del sistema; también desde Android en
Aplicaciones → Aplicación de inicio predeterminada, o desinstalando Zen. Android no
permite renunciar al rol de pantalla de inicio desde la propia aplicación: lo máximo
que puede hacer Zen es llevarte a esa pantalla.

Durante una sesión, **TERMINAR SESIÓN** siempre está disponible; matar la aplicación no
altera el cronómetro, porque vive en marcas de tiempo persistidas.

## Qué NO hace, a propósito

Sin gamificación, sin puntos, sin celebraciones, sin frases motivacionales, sin
notificaciones más allá de una silenciosa al terminar, sin servicios en segundo plano
permanentes y sin analítica. Solo hay dos salidas a la red y las dos son de ida: el
tiempo, si eliges ciudad, y la portada de noticias, si tocas NOTICIAS. Ninguna de las
dos manda nada tuyo: nada de lo que escribes, mides o guardas sale del teléfono.

---

## Arquitectura

Un solo módulo `:app`, cuatro capas, sin framework de inyección.

```
com.zenlauncher.zen/
  ZenApplication, ZenContainer     contenedor de dependencias hecho a mano
  core/          ZenClock (los dos relojes del sistema, inyectable)
  domain/
    model/       ZenSession, ActiveSession, ZenDuration, SessionProgressCalculator
    repository/  SessionRepository, PreferencesRepository, InstalledAppsRepository
    session/     ZenSessionManager        ← frontera v0.2
    apps/        AppRestrictionManager    ← frontera v0.2
    battery/     BatterySaverController   ← frontera v0.2
    stats/       StatsCalculator (puro)
    notifications/ NotificationBadges, NotificationGrouping (puros)
    breathing/   BreathingPattern (puro): la curva 4-6 en función del tiempo
    reading/     Book, BookRepository, BookImporter y el análisis, todo puro:
                 TextReflow, HeadingDetector, TableOfContents, BookMetadata,
                 BookBuilder, ReadingSearch, ReadingProgress, ReadingSettings.
                 PdfTextSource ← frontera del OCR. StudyModel ← frontera de IA
  data/          SQLite, DataStore, LauncherApps, BatteryManager
  system/        alarma, receptor de fin de sesión, notificación
  presentation/  Compose: theme, components, una pantalla + ViewModel por destino
```

**Sin Hilt** a propósito: con un módulo y una decena de objetos añadiría un plugin,
generación de código y tiempo de compilación sin resolver ningún problema real. Las
implementaciones concretas solo se nombran en `ZenContainer.kt`.

### El temporizador no es un contador

La fuente de la verdad es una `ActiveSession` persistida en DataStore **antes** de
navegar a la pantalla de sesión. La UI solo renderiza un cálculo derivado de marcas de
tiempo, así que rotación, muerte de proceso y reinicio del dispositivo están cubiertos
sin ningún servicio.

Se guardan **dos** relojes y `SessionProgressCalculator` elige cuál es fiable:

| Situación | Reloj usado | Por qué |
|---|---|---|
| Normal | `elapsedRealtime` | Monótono: no se puede manipular |
| Tras reiniciar | Reloj de pared | `elapsedRealtime` se reinició con el dispositivo |
| Hora del sistema alterada | `elapsedRealtime` | Adelantar el reloj no completa la sesión |

`ZenSessionManager.resolveExpired()` es idempotente y se invoca desde tres sitios (al
arrancar, al volver a primer plano y desde la alarma). La doble protección es un mutex
en el proceso y un `INSERT ... ON CONFLICT IGNORE` en la base de datos.

### Persistencia: SQLite directo, no Room

Room necesita KSP, y **KSP no es compatible con el Kotlin integrado de AGP 9**
(`KSP is not compatible with Android Gradle Plugin's built-in Kotlin`). Renunciar al
Kotlin integrado tampoco es viable: `org.jetbrains.kotlin.android` no soporta el DSL
nuevo de AGP 9 y falla al aplicarse (`ApplicationExtensionImpl cannot be cast to
BaseExtension`). Con una sola tabla el coste de escribirla a mano es bajo, y
`SessionRepository` aísla la decisión para revertirla cuando la incompatibilidad se
resuelva.

---

## Cómo compilar

Requiere JDK 17 o superior y el Android SDK con la plataforma **android-36.1**.

```bash
./gradlew testDebugUnitTest      # 189 tests, sin dispositivo
./gradlew lint                   # informe en app/build/reports/
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/
```

Cadena de herramientas fijada:

| | |
|---|---|
| Gradle | 9.5.0 |
| AGP | 9.3.1, con **Kotlin 2.2.10 integrado** (sin plugin `kotlin-android`) |
| Compose | BOM 2026.06.01 (Compose 1.11.4) |
| compileSdk / minSdk / targetSdk | 36.1 / 34 / 36 |

> Compose 1.12 (BOM 2026.08.00) exige `compileSdk 37`; se fija el BOM anterior para
> respetar el `compileSdk 36.1` del proyecto. El plugin de Compose se declara en la
> versión exacta del Kotlin que integra AGP.

## Cómo instalarlo en el Nothing Phone (2a)

1. Activar **Opciones de desarrollo** (Ajustes → Información del teléfono → pulsar
   siete veces en Número de compilación) y dentro, **Depuración USB**.
2. Conectar por USB y aceptar la huella del ordenador.
3. `./gradlew installDebug`
4. Abrir Zen → **Ajustes Zen → Zen como pantalla de inicio** y aceptar el diálogo del
   sistema (`RoleManager.ROLE_HOME`).
5. Opcional: **Ajustes Zen → Duración preferida** y **Aplicaciones en el Inicio**.

Para volver al launcher de Nothing: Ajustes de Android → Aplicaciones → Aplicación de
inicio predeterminada.

---

## Límites reales de Android 16

Verificado inspeccionando `android-36.1/android.jar`, no por suposición.

### Battery Saver no se puede activar

`PowerManager` público expone `isPowerSaveMode()` y `ACTION_POWER_SAVE_MODE_CHANGED`,
pero **`setPowerSaveModeEnabled()` no está en el SDK público**: es `@SystemApi` y exige
`DEVICE_POWER`, un permiso de firma. `Settings.ACTION_VOICE_CONTROL_BATTERY_SAVER_MODE`
existe pero solo puede lanzarse desde una sesión de asistente de voz
(`startVoiceActivity`).

Zen **lee** el estado y ofrece `Settings.ACTION_BATTERY_SAVER_SETTINGS` para que lo
active el usuario. No hay atajo seguro y no se intenta ninguno: ni shell, ni reflexión
sobre `@SystemApi`, ni servicio de accesibilidad.

### El modo monocromo del sistema no tiene API pública

`Settings.Secure` no expone ningún campo de daltonizer o escala de grises; la
corrección de color vive tras `WRITE_SECURE_SETTINGS`. Zen es monocroma **dentro de su
propia interfaz** y ofrece un acceso directo a los ajustes de accesibilidad.

### La barra de gestos no se puede desactivar

`WindowInsetsController.hide(navigationBars())` la quita de la vista, y es todo lo que
puede hacer una aplicación sin Device Owner: el gesto sigue ahí y deslizar desde el borde
inferior devuelve la barra unos segundos. Zen la oculta y además deja el gesto de atrás
sin efecto **dentro de la home** (un `BackHandler` vacío, porque desde la pantalla de
inicio no hay a dónde volver). El bloqueo de verdad solo ocurre durante una sesión, con
el anclado de pantalla.

### El reproductor tiene dos niveles, y el bajo no pide nada

**Las órdenes** van siempre por `AudioManager.dispatchMediaKeyEvent`: no pide ningún
permiso y entrega la tecla a la sesión de medios activa igual que el botón de unos
auriculares. A cambio, el mando no sabe quién la recibe.

**Los metadatos** (carátula, título, artista) solo existen a través de
`MediaSessionManager.getActiveSessions`, y Android exige para eso el nombre de un
**oyente de notificaciones habilitado** como prueba de la concesión. No hay otra API
pública. Zen lo resuelve así:

- El acceso es **opcional y está apagado**. Se concede desde *Ajustes Zen → Información de
  la canción*, que abre la pantalla del sistema; se revoca en el mismo sitio.
- Sin conceder nada, el mando funciona igual y el estado (`SONANDO` / `EN PAUSA`) se
  deduce de `AudioManager.isMusicActive`.

`ZenNotificationListener` empezó vacío a propósito y hoy hace un segundo trabajo: leer
el panel para las **marcas de aviso** de la pantalla de inicio. La diferencia con los
iconos de la barra de estado es que aquí **nada interrumpe**: no hay sonido, ni
vibración, ni nada que aparezca encima de lo que estabas haciendo; el número espera
quieto y solo lo ves si vas a la pantalla de inicio, que es donde ya ibas a mirar la
hora. Lo leído vive en memoria mientras el proceso existe, no se escribe en disco y no
sale del dispositivo.

### La franja de conexiones se probó y se quitó

Llegó a funcionar sin permisos peligrosos y se retiró al dejar de ocultar la barra de
estado: decía lo mismo que el sistema ya dibuja arriba, y repetir un dato no lo hace más
legible. Se anota lo aprendido porque el camino tiene trampas y evita repetirlo:

- **El nombre de la red WiFi no se puede leer.** Desde Android 10 el SSID exige
  `ACCESS_FINE_LOCATION` —saber a qué red estás conectado dice dónde estás—, así que lo
  máximo era escribir `WIFI` a secas.
- **La línea móvil hay que leerla del estado de la SIM**, no del transporte por defecto:
  con WiFi conectado la cobertura móvil sigue existiendo y el transporte no la ve.
- **El Bluetooth se lee de `Settings.Global`** (`bluetooth_on`) y no de
  `BluetoothAdapter.isEnabled()`, que desde Android 12 exige `BLUETOOTH_CONNECT` y su
  diálogo.
- El resto (WiFi conectado por `ConnectivityManager`, operador y nivel de señal por
  `TelephonyManager`) solo necesitaba `ACCESS_NETWORK_STATE`, un permiso *normal*.

Con la franja fuera, ese permiso también se retiró: Zen ya no declara ninguno relacionado
con la red.

### El ecualizador no puede seguir el sonido de verdad

Se mueve mientras hay reproducción y se para al pausar, y eso es lo único que promete.
Reaccionar a la onda real exige `Visualizer`, que ya no captura la mezcla global del
dispositivo desde hace varias versiones de Android. Zen declara `RECORD_AUDIO` desde que
se puede dictar una nota, pero eso no cambia la decisión: el dictado abre el micrófono
unos segundos y a petición del usuario, mientras que animar cuatro barras exigiría
tenerlo abierto **todo el tiempo que suene música**. Así que la animación es sintética: periodos distintos y no múltiplos entre sí
para que el conjunto no se sincronice. Parado no dibuja ni un fotograma de más.

### La RAM del teléfono no se puede "limpiar"

**Desde Android 14, `ActivityManager.killBackgroundProcesses` solo mata procesos de la
propia aplicación.** Cualquier botón de "liberar memoria" en un launcher de Android
moderno cierra el launcher y nada más; en las ROMs donde parece funcionar, lo hace un
componente del sistema con privilegios que una aplicación normal no tiene. Zen no
declara `KILL_BACKGROUND_PROCESSES` y no finge la operación.

Tampoco sería buena idea si se pudiera: un proceso en caché no ocupa memoria útil —el
sistema la reclama en cuanto hace falta— y matarlo obliga a arrancar la aplicación
entera la próxima vez, que **gasta más batería en lugar de ahorrarla**. `lmkd` decide
mejor que cualquier heurística escrita desde fuera.

Lo que sí está en manos de Zen es **ocupar poco para no ser el proceso que el sistema
elija matar**: a un launcher al que matan lo tienes que esperar un segundo largo la
próxima vez que pulsas Inicio, y ese es el problema real de memoria de un launcher. Zen
lo trabaja en dos sitios:

- **Una sola lectura de la lista de aplicaciones para toda la aplicación**
  (`CachedInstalledApps`). Media docena de pantallas la observan; sin compartirla, cada
  una abría su propio `LauncherApps.getActivityList` —IPC más disco, con el rótulo de
  cada aplicación cruzando el proceso— y registraba su propio callback. Además la lista
  cacheada **se emite en el primer fotograma**: la retícula ya está puesta antes de
  pintar en lugar de rellenarse cuando vuelve el IPC.
- **Se suelta lo cacheable cuando el proceso entra en la cola de candidatos a morir**, y
  no al salir de Zen. Va al revés de lo que parece: `TRIM_MEMORY_UI_HIDDEN` llega cada
  vez que se abre una aplicación —decenas de veces al día— y no significa que falte
  memoria; soltar ahí obliga a releer la lista entera en **cada** vuelta a la pantalla de
  inicio. Con `TRIM_MEMORY_BACKGROUND` no se discute: más vale una pantalla de inicio
  lenta que un teléfono sin pantalla de inicio. La decisión vive en `MemoryTrimPolicy`,
  que es una función pura con test.

  **Solo se miran esos dos niveles, y no es una simplificación.** Verificado sobre
  `android-36/android.jar`: de los siete de `ComponentCallbacks2`, cinco están marcados
  `@Deprecated` —`TRIM_MEMORY_RUNNING_MODERATE`, `RUNNING_LOW`, `RUNNING_CRITICAL`,
  `MODERATE` y `COMPLETE`— y Android ya no los entrega. Un mapeo apoyado en ellos
  habría dejado la rama de soltar memoria muerta sin que nada lo dijera: en un teléfono
  real no habría llegado nunca ninguno.

### El uso del móvil se lee, pero el acceso lo concede el usuario a mano

`UsageStatsManager.queryEvents` es la única vía pública para saber qué aplicación estuvo
delante y cuánto. Exige `PACKAGE_USAGE_STATS`, que es de nivel `signature|appop`: **una
aplicación normal no puede otorgárselo**. Declararlo en el manifiesto es lo único que
hace que Zen aparezca en Ajustes de Android → Acceso de uso, que es donde el usuario lo
concede y lo revoca; de ahí el `tools:ignore="ProtectedPermissions"`.

Sin conceder, `queryEvents` devuelve una lista vacía **en lugar de fallar**, y esos dos
casos no son el mismo: Zen distingue "hoy no has usado el móvil" de "no puedo verlo", y
sin medida no hay pulso, no hay aviso y la pantalla de Uso lo dice en lugar de enseñar
un cero. Se usa `queryEvents` y no `queryAndAggregateUsageStats` porque el agregado
viene en cubos de intervalo fijo, no distingue aperturas de tiempo y no sirve para mirar
los últimos quince minutos.

Cuatro detalles que muerden y están fijados con test:

- **La pantalla de Recientes pertenece al launcher de fábrica, y se cuenta como uso.**
  Zen no implementa Recientes, así que Android sigue usando el de la ROM: cada gesto de
  recientes pone en primer plano su `RecentsActivity`. Medido en el Nothing Phone (2a):
  **66 "aperturas" diarias de `com.nothing.launcher`**, suficientes para salir
  recomendado como hábito a corregir en la pantalla de la semana. Navegar por el sistema
  no es usar una aplicación. Se excluyen el propio Zen, `com.android.systemui` y
  **cualquier paquete capaz de ser pantalla de inicio**, resuelto por intent y no con una
  lista de nombres escritos a mano, porque el launcher de fábrica se llama distinto en
  cada ROM.

- **Android emite un `ACTIVITY_RESUMED` por pantalla, no por aplicación.** Es el que se
  escapó a los tests y apareció en el Nothing Phone (2a) a los dos minutos de instalar:
  entrar en Ajustes y bajar tres niveles emite `SettingsHomepage`, `SubSettings`,
  `SubSettings`, `SubSettings`, y eso se contaba como **cuatro aperturas**; la app del
  banco sumaba dos solo con su pantalla de arranque. Con la cuenta inflada, el aviso de
  picoteo saltó sin que el usuario hubiera saltado a ninguna parte —"12 APERTURAS · 1m",
  que se lee solo como lo que era—. Ahora los tramos consecutivos de la misma aplicación
  se unen en una visita, y solo **después** se filtra: al revés, las subpantallas de
  medio segundo desaparecerían una a una en lugar de sumar, y salir a la pantalla de
  inicio y volver se fundiría en una sola visita.
- **Unir tiene su propio límite**: solo se unen tramos casi pegados. Apagar la pantalla
  cierra el tramo pero no abre ninguno, así que los dos tramos quedaban adyacentes y esa
  "visita" se llevaba toda la noche con el móvil en el bolsillo.
- **Hay ROMs que no emiten el cierre de la aplicación anterior al abrir otra.** Sin un
  cierre implícito, la primera aplicación del día se quedaba abierta hasta la noche y se
  llevaba la jornada entera.
- **Sin bloqueo de pantalla configurado no hay `KEYGUARD_HIDDEN`.** Se cuenta ese evento
  y, solo si no aparece ninguno, `SCREEN_INTERACTIVE`; sumando los dos, un teléfono con
  bloqueo contaba cada desbloqueo dos veces.

Todo el cálculo vive fuera de Android, en `UsageTimeline`, para poder probar los casos
raros —el cierre que no llega, la aplicación que sigue abierta ahora mismo, los eventos
desordenados— en la JVM.

### El tiempo de tu móvil no se le puede preguntar a nadie

Android **no tiene ninguna API para leer el tiempo de otra aplicación**. No hay un
proveedor de contenido estándar, no hay un `Intent` que devuelva grados y no existe una
categoría "aplicación del tiempo del sistema" como sí existe la de pantalla de inicio
(`CATEGORY_HOME`): el sistema no sabe cuál es la tuya y por lo tanto tampoco puede
decírselo a un launcher.

Se comprobó puerta por puerta sobre el Nothing Phone (2a) con Nothing OS 4.1, y las tres
están cerradas:

1. **El proveedor de datos existe y es de firma.** `com.nothing.weather` exporta
   `content://com.nothing.weather.share/query/geo_weather_info`, y consultarlo responde
   `SecurityException: requires com.nothing.weather.permission.ACCESS_WEATHER_INFO`. Ese
   permiso es `prot=signature`: lo obtienen las aplicaciones firmadas por Nothing —el
   launcher de fábrica— y ninguna más. No es cuestión de pedirlo.
2. **No publica ningún aviso permanente.** Sus dos únicos canales de notificación son
   `notice_channel_id` («Notificaciones meteorológicas») y `alert_channel_id` («Avisos
   meteorológicos»), ninguno de estado. La vía de leer los grados del panel de
   notificaciones —que no habría costado ningún permiso nuevo, porque Zen ya lee ese
   panel para las marcas de aviso— se implementó, se probó y **no encontró nada**.
3. **Sus widgets no son widgets de Android.** No declara ningún `AppWidgetProvider`, así
   que `AppWidgetHost` no puede alojarlos: son "tarjetas" de Nothing servidas por siete
   proveedores propios. El permiso que piden (`com.nothing.permission.BIND_CARD_SERVICE`)
   **sí** es de nivel `normal` y se concede, pero su `query()` devuelve null: el
   protocolo va por `call()` con métodos de empuje (`updateAppWidget`,
   `onShareWidgetDataUpdate`) que devuelven vistas ya renderizadas, no datos. Sacar los
   grados de ahí sería reimplementar un protocolo privado y raspar texto de un
   `RemoteViews`, y se rompería con la siguiente actualización de Nothing OS.

**Así que Zen se lo pregunta a Open-Meteo**, y es la única función de la aplicación que
sale a internet. Se eligió ese servicio porque no pide clave, no pide registro y no hay
ninguna cuenta a la que atar lo consultado. Lo que sale del teléfono son **dos
coordenadas recortadas a dos decimales** —unos 300 m— y son las de la ciudad que tú
escribes en Ajustes → Tiempo: **Zen no pide el permiso de ubicación**, y mientras no
elijas ciudad no se abre ni una conexión.

Sin dependencias nuevas: `HttpURLConnection` y `org.json`, que vienen en Android. Traer
Retrofit y un serializador para dos peticiones GET sería más código del que se ahorra, y
esta aplicación ya escribe su SQLite a mano por la misma razón.

**No hay sondeo.** El tiempo se pide al volver a la pantalla de inicio, igual que se mide
el uso del móvil, y como mucho una vez cada media hora (`WeatherRefresh`): a la home se
vuelve decenas de veces al día y la temperatura no cambia entre dos de ellas. La marca de
"último intento" se escribe antes de saber si la petición salió bien, porque contando
solo los aciertos un teléfono sin cobertura pediría en cada vuelta.

Y hay caducidad: pasadas seis horas sin conseguir un dato nuevo, **el viejo deja de
enseñarse**. Un "18°" de anoche con la misma cara que uno de ahora es una mentira con
forma de dato, y es el mismo criterio que `UsageSnapshot.measured` aplica al uso del
móvil. La pantalla del tiempo sí distingue las dos cosas y lo dice con palabras.

El cielo se resume en un glifo de tres caracteres dibujado con DM Mono —`-O-`, `~~~`,
`///`— y no con un símbolo de Unicode, porque el sol y la nube no existen en las fuentes
de Zen y Android los sacaría de la fuente de reserva del sistema, que en un móvil actual
es la de emoji: un mapa de bits a todo color en la única pantalla monocroma de la
aplicación. La traducción del código de la OMM al glifo es una función pura con tests
(`WeatherCodes`), y un código que no está en la tabla se queda sin glifo y enseña los
grados solos, en vez de adivinar.

Verificado en el dispositivo: buscar «Oviedo» devuelve siete resultados de cinco países
—por eso el nombre lleva la región y el país pegados, o elegir sería una lotería— y el
primero da `25°` con cielo nublado, glifo `~~~`.

### El bloqueo real de aplicaciones no existe sin privilegios

Ninguna API pública permite a una app normal impedir que se abra otra. En v0.1 la
restricción es de **visibilidad**: las marcadas desaparecen de Zen y se cuentan en la
sesión, pero siguen siendo accesibles por otras vías. La pantalla lo dice explícitamente
en lugar de aparentar un bloqueo que no existe.

`AppRestrictionManager.enforce()` y `release()` ya existen y hoy no hacen nada. Se
invocan igualmente desde el gestor de sesiones para que el punto de extensión esté
ejercitado cuando llegue la implementación privilegiada.

---

## APIs públicas utilizadas

| API | Para qué | Permiso |
|---|---|---|
| `LauncherApps.getActivityList` / `registerCallback` | Enumerar apps lanzables y reaccionar a instalaciones | Ninguno (`<queries>`) |
| `RoleManager.ROLE_HOME` + `createRequestRoleIntent` | Pedir ser el launcher | Ninguno |
| `BatteryManager.BATTERY_PROPERTY_CAPACITY` / `isCharging` | Nivel y estado de carga | Ninguno |
| `PowerManager.isPowerSaveMode` + `ACTION_POWER_SAVE_MODE_CHANGED` | Leer el ahorro de batería | Ninguno |
| `AlarmManager.canScheduleExactAlarms` / `setExactAndAllowWhileIdle` | Avisar a T=0 | `USE_EXACT_ALARM` |
| `NotificationManager` (canal `IMPORTANCE_LOW`) | Un aviso silencioso al terminar | `POST_NOTIFICATIONS` |
| `Settings.ACTION_BATTERY_SAVER_SETTINGS` / `ACTION_ACCESSIBILITY_SETTINGS` | Enviar a los ajustes correctos | Ninguno |
| `AudioManager.dispatchMediaKeyEvent` / `isMusicActive` | Mando del reproductor | Ninguno |
| `MediaSessionManager.getActiveSessions` | Carátula, título y artista | Acceso a notificaciones, **opcional** |
| `NotificationListenerService.getActiveNotifications` | Marcas de aviso y su lista | Acceso a notificaciones, **opcional** |
| `HttpURLConnection` a Open-Meteo | El tiempo de la ciudad que elijas | `INTERNET` |
| `HttpURLConnection` a noticiasdoxa.es | La portada de noticias, una vez al día | `INTERNET` |
| `SpeechRecognizer.createOnDeviceSpeechRecognizer` | Dictar una nota, sin red | `RECORD_AUDIO`, **opcional** |
| `UsageStatsManager.queryEvents` | Tiempo por aplicación, aperturas y desbloqueos | Acceso de uso, **opcional** |
| `AppOpsManager.checkOpNoThrow` (`OPSTR_GET_USAGE_STATS`) | Saber si el acceso de uso está concedido | Ninguno |
| `Settings.ACTION_USAGE_ACCESS_SETTINGS` | Conceder y revocar el acceso de uso | Ninguno |
| `Application.onTrimMemory` (`UI_HIDDEN` / `BACKGROUND`) | Soltar las cachés cuando el proceso pasa a ser candidato a morir | Ninguno |
| `Settings.ACTION_HOME_SETTINGS` | Salir de Zen: elegir otra pantalla de inicio | Ninguno |
| `WindowInsetsController.hide` (navegación) | Ocultar la barra de gestos | Ninguno |

### Permisos, y por qué tan pocos

- **`POST_NOTIFICATIONS`** — la única notificación de la app: fin de sesión. Se pide
  justo antes de la primera sesión, no al arrancar.
- **`USE_EXACT_ALARM`** — disparar exactamente a T=0. Se concede en la instalación y
  está reservado a aplicaciones cuya función principal es una alarma o un temporizador,
  que es exactamente el caso de Zen. Se eligió sobre `SCHEDULE_EXACT_ALARM` tras
  comprobar en dispositivo que ese está denegado por defecto desde Android 14 y hacía
  caer la alarma a inexacta, con una ventana de ~45 s.
- **`RECORD_AUDIO`** — dictar una nota. Se pide **al tocar «Dictar»**, no al instalar ni
  al abrir la pantalla: quien escribe con el teclado no ve nunca el diálogo. La
  transcripción la hace `createOnDeviceSpeechRecognizer`, el reconocedor **del propio
  dispositivo**: sin red, sin modelo empotrado y sin descarga. Zen **no guarda el audio
  en ningún sitio**; lo único que llega a la nota es texto.

- **`PACKAGE_USAGE_STATS`** — medir el uso del móvil: tiempo por aplicación, aperturas y
  desbloqueos. **No se concede en la instalación aunque esté declarado**; ver
  [el límite](#el-uso-del-móvil-se-lee-pero-el-acceso-lo-concede-el-usuario-a-mano). Está
  apagado por defecto, se concede a mano en Ajustes de Android → Acceso de uso y se
  revoca en el mismo sitio. Lo leído vive en memoria mientras el proceso existe, no se
  escribe en disco y no sale del dispositivo.

- **`CAMERA`** — escanear un documento, y nada más. Se pide **al abrir el escáner**, no
  al instalar ni al arrancar: quien no entra ahí no ve nunca el diálogo, igual que quien
  escribe sus notas con el teclado no ve el del micrófono. Denegarlo no rompe nada: la
  pantalla lo dice en texto y ofrece volver a pedirlo. La cámara se declara además como
  `uses-feature` **no obligatoria**, para que Google Play no esconda el launcher entero en
  un dispositivo sin cámara.

  **No lleva ningún permiso de almacenamiento al lado**, y es a propósito: lo escaneado
  sale por `MediaStore`, que no exige ninguno para los ficheros que crea la propia
  aplicación, y lo que está a medias vive en la caché privada de Zen. Mismo planteamiento
  que el selector de documentos de Lectura y el de fotos de Notas: el permiso se evita
  eligiendo la API correcta, no pidiéndolo por si acaso.

- **`INTERNET`** — dos funciones y ni una más: **el tiempo** y **la portada de
  noticias**. Las dos son peticiones `GET` de ida, sin clave, sin cuenta y sin
  identificador, y las dos están apagadas mientras nadie las pida.

  El tiempo se añadió después de comprobar que la aplicación del tiempo del teléfono no
  deja leer sus datos por ninguna vía; ver
  [el límite](#el-tiempo-de-tu-móvil-no-se-le-puede-preguntar-a-nadie). Lo que sale son
  dos coordenadas recortadas a dos decimales, las de la ciudad que escribiste a mano:
  **no hay permiso de ubicación**, no hay clave y no hay cuenta. Sin ciudad elegida no se
  abre ninguna conexión, y quitando la ciudad se apaga del todo.

  Las noticias salen a un único sitio, `noticiasdoxa.es`, **al tocar NOTICIAS y una vez
  al día**: la petición no lleva parámetros, así que lo único que el servidor sabe es que
  alguien pidió su portada. Sin tocar el botón no se abre ninguna conexión, y los enlaces
  de los puntos se filtran a ese mismo dominio antes de entregárselos al navegador. Ver
  [Las noticias del día](#las-noticias-del-día).

**Lectura no añade ninguno.** El selector de documentos del sistema no pide permiso —
devuelve solo el fichero elegido— y la extracción de texto es del propio Android: no usa
`INTERNET` ni ninguna otra cosa. Ver [Lectura](#lectura).

**El escáner añade uno solo, `CAMERA`**, y no toca `INTERNET`: la detección, el
enderezado y el reconocimiento de texto son todos locales, y el modelo de OCR va
empaquetado en el APK. Ver [El escáner de documentos](#el-escáner-de-documentos).

Los seis **degradan solos**: sin alarma exacta se usa una inexacta, sin notificaciones la
sesión se cierra igualmente al volver a Zen, y el dictado desaparece por triplicado —sin
reconocedor de dispositivo la fila no se pinta, sin el paquete de voz del idioma tampoco,
y si se deniega el permiso la fila lo dice como texto y el teclado sigue igual; y sin
acceso de uso no hay pulso ni aviso de distracción y la pantalla de Uso dice que no hay
medida en lugar de enseñar un cero. Y sin red, la franja de la pantalla de inicio se
queda sin el glifo del tiempo, las noticias enseñan la última portada que se bajara
—diciendo que es de otro día— y todo lo demás sigue igual. Sin cámara, o sin permiso, el
escáner lo dice en pantalla y ofrece salir; si la librería nativa no cargara en un
teléfono, también. Ninguno bloquea el flujo.

Hay un tercero que **no se pide**: `BIND_NOTIFICATION_LISTENER_SERVICE` lo declara el
servicio para que solo el sistema pueda enlazarlo, y la concesión la da el usuario a mano
en Ajustes de Android. Está apagado por defecto y da dos cosas —los metadatos de la
sesión de medios y las marcas de aviso—; quitarlo hace desaparecer la carátula, el
título y los números, y nada más. Ni las marcas ni los metadatos son un permiso nuevo:
son la misma concesión.

No se declaran `SCHEDULE_EXACT_ALARM` (ver arriba), `QUERY_ALL_PACKAGES` (basta `<queries>`), `RECEIVE_BOOT_COMPLETED` (tras
un reinicio la resolución perezosa cubre el caso), `FOREGROUND_SERVICE` (no hay
servicios) ni ningún permiso de red.

---

## Reservado para v0.2

Nada de esto está implementado. La arquitectura ya tiene el hueco:

- **Device Owner** vía `adb shell dpm set-device-owner com.zenlauncher.zen/.DeviceAdmin`
  sobre un dispositivo recién restaurado y sin cuentas.
- **`DevicePolicyManager.setPackagesSuspended`** — bloqueo real de aplicaciones, detrás
  de `AppRestrictionManager` con `EnforcementLevel.SYSTEM_ENFORCED`.
- **Lock Task Mode / kiosco** — impedir salir de Zen durante la sesión.
- **Impedir el acceso a Ajustes** durante una sesión, vía políticas de usuario.
- Controles más fuertes contra abandonar una sesión.

Cambiar de v0.1 a v0.2 debería ser sustituir las implementaciones registradas en
`ZenContainer.kt`, sin tocar dominio ni UI.

---

## Tests

```bash
./gradlew testDebugUnitTest
```

952 tests en la JVM, sin dispositivo. Cubren el cálculo del tiempo restante (incluidos
reinicio y manipulación del reloj), sesión completada y abandonada, duración registrada,
idempotencia del cierre, cálculo de batería consumida —con sus casos no fiables—,
persistencia en SQLite y en DataStore, selección de aplicaciones, la resolución de las
aplicaciones esenciales por candidatos de paquete, el sembrado de la pantalla de inicio,
el reparto de huecos al reordenar la retícula —a qué hueco llega un arrastre y qué pasa
con una favorita guardada que no se ve—,
la lectura del acceso al oyente de notificaciones, qué cuenta como aviso pendiente y
cómo se agrupan, la política de barras del sistema, el patrón de respiración guiada, la agregación de
varios días y las observaciones sobre el patrón —con sus topes y sus silencios—, el plegado de la
cronología de uso —el cierre que no llega, la aplicación que sigue abierta, los eventos
desordenados—, los cuatro escalones del pulso, las tres formas de conducta compulsiva
con sus exentas, la espera del aviso y la política de memoria, el corte diario de la
portada de noticias y su análisis contra una portada real guardada —los siete puntos,
los enlaces de otro dominio que se descartan y las secciones de abajo que no se cuelan—,
y ocho ViewModel.

Del **escáner** se cubre todo lo que no es nativo, que es donde están las decisiones:
el ordenado de las cuatro esquinas —empiece el contorno por donde empiece y lo recorra en
el sentido que lo recorra— y el descarte de las formas que no pueden ser una hoja; la
homografía, comprobando que un trapecio se convierte en rectángulo, que el centro **no**
va al centro (o sea, que hay perspectiva de verdad y no un simple estirado), que ir y
volver devuelve el punto de partida y que un cuadrilátero degenerado da `null` en vez de
lanzar; la recuperación de proporciones **contra proyecciones sintetizadas a mano**, con
un rectángulo de proporción conocida, una cámara estenopeica inventada y un abanico de
posturas que a mano no se conseguiría repetir; la política de captura automática entera;
la quietud del acelerómetro; el tamaño de las hojas del PDF y los nombres de fichero.

Nada de OpenCV ni de ML Kit se ejecuta en la JVM, así que la calidad de la detección
sobre fotos reales **no tiene test y se comprueba en el dispositivo**, igual que los
truncamientos de texto del buscador de ciudades.

Las pantallas se cubren con **tests de UI de Compose sobre Robolectric**, también sin
dispositivo: `HomeScreenTest`, `SettingsScreenTest`, `NotificationsScreenTest`,
`NewsScreenTest`, `ActiveSessionScreenTest` y `BreatheScreenTest` —este último mueve el reloj de
fotogramas a mano, porque el ejercicio no se queda quieto solo hasta el minuto—. Cada
corrección lleva su test de regresión, con un comentario que explica qué fallo fija.

---

## Verificado en dispositivo

Probado en un Nothing Phone (2a) real (`A142`, Nothing OS B4.1, Android 16, SDK 36):
arranque sin fallos, 143 aplicaciones enumeradas sin `QUERY_ALL_PACKAGES`, sesión
iniciada y completada, cierre correcto tras `am force-stop` a mitad de sesión (el
cronómetro se reconstruye desde las marcas de tiempo), abandono con confirmación,
registro agregando ambas sesiones y diálogo de `ROLE_HOME` mostrándose.

Verificado además con Zen puesto como launcher predeterminado y tras reiniciar el
dispositivo: el rol persiste y la salida desde **Ajustes Zen → Cambiar la pantalla de
inicio** abre el selector de aplicación de inicio de Android.

El aviso disparado por la alarma **está verificado en hardware**: con la pantalla
apagada (`mWakefulness=Dozing`) y la Activity pausada, la alarma exacta cerró la sesión,
publicó la notificación silenciosa y dejó el resumen persistido esperando; al volver a
abrir Zen, la pantalla de resumen apareció con los datos correctos.

---

## Licencias de terceros

Archivo y DM Mono se distribuyen bajo SIL Open Font License 1.1. El texto de cada
licencia está en `app/src/main/assets/licenses/`.

**OpenCV 4.14.0** (`org.opencv:opencv`) se distribuye bajo Apache License 2.0, y es la
única dependencia nativa de Zen: detección del documento y corrección de perspectiva del
escáner. **ML Kit Text Recognition** (`com.google.mlkit:text-recognition`, variante con el
modelo empaquetado) se distribuye bajo los términos de Google APIs Terms of Service; el
fichero de licencias de terceros que trae el propio artefacto viaja dentro del AAR. Ver
[El escáner de documentos](#el-escáner-de-documentos).
