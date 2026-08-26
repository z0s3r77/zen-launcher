# CLAUDE.md

Guía para Claude Code (claude.ai/code) al trabajar en este repositorio.

## Qué es

`zen` (paquete `com.zenlauncher.zen`) es un **launcher de Android funcional**, no un
andamio: pantalla de inicio, lista de aplicaciones con buscador, aplicaciones
restringidas, sesiones Zen con cronómetro persistido, registro y ajustes. Una sola
Activity (`presentation/ZenActivity`) declarada con `MAIN` + `HOME` + `LAUNCHER`, Compose
en todas las pantallas, navegación con `ZenNavHost`.

Dispositivo objetivo: **Nothing Phone (2a)**, Nothing OS 4.1 / Android 16. `README.md`
documenta el producto y los límites reales de Android verificados sobre `android.jar`;
léelo antes de proponer cualquier cosa que "bloquee" aplicaciones, active el ahorro de
batería o desactive gestos: esas puertas ya se investigaron y están cerradas sin
privilegios de Device Owner.

## Zen es un LAUNCHER, no una aplicación normal

Esto no es un detalle de categoría: condiciona cada decisión de diseño y de código. Al
tocar cualquier cosa, dar por supuesto lo siguiente:

- **Es la pantalla de inicio del dispositivo.** No hay "salir de la app", no hay barra de
  acción y no hay pila de navegación hacia el sistema. Si Zen falla al arrancar, el móvil
  se queda sin pantalla de inicio: degradar siempre, nunca bloquear.
- **Toda pantalla secundaria necesita su propia salida.** La barra de gestos está oculta
  (la de estado no, ver `SystemBarsPolicy`), así que Android se queda el primer
  deslizamiento desde el borde para sacarla.
  Zen lo compensa reconociendo el arrastre por su cuenta (`EdgeBackPolicy`, conectado en
  `ZenScreen` vía `onSwipeBack`), así que se vuelve al primer intento; la barra de gestos
  sigue asomando un instante y eso no tiene arreglo sin Device Owner. Cada pantalla nueva lleva `onBack` y lo pasa a
  `ZenHeaderStrip`. **Volver desde un lateral es el único gesto propio de Zen**, y no
  habrá más: la lista de aplicaciones se abría deslizando hacia arriba y se retiró
  —saltaba desde cualquier punto de la home, también con el menú abierto—; lo que abre
  algo se toca y se ve. La home se traga el gesto de atrás —no hay a dónde volver—, **salvo
  con el menú abierto**, donde lo cierra: es la única cara de la home de la que se sale.
- **La home no se desplaza y no crece.** Todo cabe de una vez y el reloj está siempre en
  el mismo píxel. Lo nuevo va al menú plegado, nunca a una fila permanente más. Las tres
  únicas filas permanentes son **Todas las aplicaciones** (bajo la retícula) y **Menú**,
  y los tres únicos botones son **ZEN**, **RESPIRA** y **NOTICIAS**, apilados a la
  derecha de la hora; el resto del alto es retícula, y lo que se sume ahí empuja algo
  fuera de la pantalla. **Tres es el tope de esa pila**: los tres marcos ya miden más de
  alto que el reloj que tienen al lado, y un cuarto se comería el aire que la retícula
  reparte abajo; lo siguiente que se quiera añadir va al menú.
  **Notas y Lectura no son filas: son celdas más de la retícula**, con su
  número, porque son cosas que se abren a diario y se usan como aplicaciones —abajo, junto
  a «Menú», se leían como opciones de administración—. Si algo nuevo tiene que estar
  siempre visible, ese es el único sitio donde cabe, y solo si no añade una fila de
  retícula: con un número impar de aplicaciones, la primera celda cae en el hueco que ya
  sobraba. **Y son las dos únicas que no son aplicaciones**: juntas ocupan una fila
  entera, así que una tercera empujaría el reloj. Fijado en `HomeScreenTest`; lo
  siguiente vuelve a ir al menú. El menú abierto **sustituye a la pantalla entera** y
  deja solo la franja de cabecera: lo que se añada ahí no compite con el reloj.
- **La cara del día vive en el slot derecho de la franja de cabecera de la home.** Ahí
  había un `SIN SESIÓN` que en esa pantalla era una **constante** —si hubiera sesión, la
  sesión sustituye a la pantalla entera—, o sea el "00 permanente" que este documento
  critica. Cambiar un rótulo que no puede decir otra cosa por uno que sí es la única vía
  que hay de meter algo permanente en la home sin que la home crezca: no añade fila y no
  mueve el reloj ni un píxel.
- **A su izquierda, y solo ahí, cabe el tiempo.** Es el segundo y **último** hueco de la
  franja: el otro dato que se mira cincuenta veces al día antes de salir de casa y que no
  cabía en ninguna otra parte de la home sin añadir una fila. Cumple la regla de lo que
  no tiene nada detrás: sin ciudad elegida, sin red o con el último dato ya viejo, la
  franja queda exactamente como estaba. Con los dos huecos ocupados, la franja se acabó:
  si aparece otra idea de "algo siempre visible", la respuesta vuelve a ser el menú.
- **Lectura funciona entera sin conexión y sin ninguna librería de PDF.**
  `PdfRenderer.Page.getTextContents()` extrae el texto y llegó en Android 15 (API 35);
  está comprobado sobre `android.jar`, no de memoria. `minSdk` es 34, así que la llamada
  va con guarda de versión y en un teléfono más antiguo **el botón de añadir libro no se
  pinta**: hacer elegir un fichero para luego no poder abrirlo es peor que no ofrecerlo.
  **No traigas PdfBox ni iText**: son megabytes para reimplementar lo que el sistema ya
  trae. El PDF **no se copia** —se guarda el texto ya entendido más la referencia al
  fichero— y no sale del teléfono: Lectura no usa `INTERNET`.
- **Todo el análisis de un libro es puro y se prueba contra texto guardado**, nunca
  contra un fichero real, igual que `DoxaPortada`. Es heurística tolerante y **lo que no
  se entiende se descarta en lugar de rellenarse**: un párrafo inventado es un libro que
  no dice lo que dice. Siempre queda una forma de recorrer el documento —índice impreso,
  títulos detectados o saltos por página—, y esa es la regla que no se puede romper.
  El orden importa: el índice impreso se detecta **antes** de reconstruir el cuerpo, o el
  lector abre el libro leyendo su propio índice.
- **El sitio de lectura se guarda en una posición de texto, no en páginas.** El texto es
  reflowable, así que «página 87» significa otra cosa en cuanto se sube el cuerpo dos
  escalones. Lleva bloque **y desplazamiento dentro del bloque**: pasando página, un
  párrafo largo se parte por la mitad y sin el desplazamiento el lector volvería al
  principio del párrafo en cada apertura. La página del PDF se sigue enseñando porque es
  como se habla de un libro y es lo que se cita en clase, pero se deriva del bloque.
- **El lector pagina, no se desplaza, y las páginas no se calculan todas de golpe.**
  Medir un libro entero son segundos de espera dentro del proceso del launcher, y habría
  que repetirlo con cada cambio de cuerpo de letra. Se compone la hoja que se está
  mirando; retroceder se resuelve **midiendo un poco por detrás** (`Paginator.previous`) y
  no con una pila de páginas visitadas, que se quedaría vacía justo después de saltar
  desde el índice o desde una marca. Lo que decide vive en `Paginator`, que es puro;
  `PageMeasurer` es la frontera con Compose y en los tests se sustituye por líneas de
  ancho fijo.
- **Se pasa página tocando, nunca deslizando.** No es una preferencia: `ZenScreen` ya usa
  el arrastre horizontal para volver, y un hijo que lo consumiera dejaría el lector sin
  salida. Tercios laterales para pasar hoja, tercio central para despertar los mandos.
- **El lector es la única pantalla que esconde su propia salida.** Leer a página completa
  significa que mientras se lee no hay ni franja de cabecera; al tocar el centro aparece
  todo. Sigue habiendo dos vías de salida —el botón al despertar la pantalla y el arrastre
  desde el borde—, y por eso la excepción se sostiene. **No la copies en otra pantalla**:
  aquí se justifica porque el contenido *es* la pantalla.
- **Subrayar y anotar son la misma cosa**, con y sin texto detrás (`Highlight.note`).
  Separarlas obligaría a elegir antes de saber si vas a tener algo que decir, y dejaría dos
  listas hablando del mismo párrafo. La unidad de selección es **la frase** (`Sentences`),
  no un arrastre con manillas: en un móvil se arrastra tapando con el dedo justo lo que se
  quiere marcar, y en filosofía lo que se subraya casi siempre es una frase entera.
- **El escáner de documentos es la única función con código nativo, y por eso vive
  plegado.** OpenCV son 24,7 MB de `.so` y el modelo de OCR otros 11: el APK pasa de 8 MB
  a 73, y son megabytes en el proceso de la **pantalla de inicio**. Se compensa con tres
  cosas que **no son opcionales**: `abiFilters` a `arm64-v8a` (la del Phone 2a; en un
  emulador x86_64 hay que añadirla a mano), todo perezoso en `ZenContainer` —quien no
  abre el escáner no paga ni un byte— y el ViewModel colgado de la **entrada de
  navegación**, nunca del ámbito de la Activity. Ojo con lo que eso compra: al salir se
  sueltan el detector, el modelo de OCR y la memoria nativa de los `Mat`, pero **la
  biblioteca nativa se queda mapeada hasta que muera el proceso** —Java no puede
  descargarla—, unos 15 MB medidos en el dispositivo. La pereza importa porque quien no
  abre el escáner no paga nada, no porque se pueda devolver. Entra por el **menú** y no por la retícula, por la regla de
  siempre: la home no crece y Notas y Lectura ya ocupan las dos únicas celdas que no son
  aplicaciones.
- **Un `Mat` es memoria nativa que el recolector de basura no ve.** La detección crea
  media docena por frame quince veces por segundo; sin soltarlos a mano el escáner reserva
  cientos de megabytes en un minuto y el sistema mata al launcher. Todo lo que cree un
  `Mat` va dentro de `withMats { }` (ver `MatScope`), y nada nativo se llama sin atrapar
  **`Throwable`**: `UnsatisfiedLinkError` es un `Error`, no una `Exception`, y un
  `runCatching` normal no lo coge.
- **Las proporciones del escaneo se recuperan, no se miden.** Estirar el trapecio hasta el
  rectángulo que envuelve sus esquinas da un A4 aplastado, y nada en la pantalla lo
  delata. Se despeja con la solución cerrada de Zhang y He (`DocumentAspect`), y cuando el
  móvil está alineado con la hoja en un eje —que es la postura normal de quien mira la
  mesa desde arriba— la focal deja de estar en la foto y se supone una de móvil
  corriente. **No lo cambies por la media de los lados**: está medido en
  `DocumentAspectTest` que falla el doble o el triple.
- **El escáner guarda tres ficheros por página y el original no se toca nunca.** La foto
  cruda deja volver a mover las esquinas media hora después; la enderezada sin filtro es
  de donde salen todos los modos, así que cambiar de filtro no degrada nada ni vuelve a
  enderezar. Los tres están en la **caché**, no en `filesDir`: un escaneo a medias no es
  un documento del usuario, y se borran al salir.
- **El escáner reparte el trabajo en tres ritmos y mezclarlos lo rompe.** Por frame, solo
  detectar y decidir, en el hilo de análisis y a 15 fps como mucho; por captura, enderezar
  y filtrar; a petición, OCR y exportar. El análisis pide 640x480 y la captura la máxima
  resolución: detectar sobre 12 megapíxeles es mover 48 MB por frame para tirarlos.
- **Solo dos cosas salen a internet: el tiempo y la portada de noticias.** Las dos son
  peticiones de ida, sin clave y sin cuenta, y las dos están apagadas mientras nadie las
  pida. Si aparece una tercera, es una decisión de producto y va al README antes que al
  código.
- **El tiempo hubo que comprobar que no había otra vía.** La aplicación del tiempo del teléfono no deja leer sus datos: su proveedor
  exige un permiso `signature` de Nothing, no publica aviso permanente del que sacarlos y
  sus widgets no son widgets de Android sino "tarjetas" con un protocolo privado de
  empuje. Está todo medido en el dispositivo y escrito en el README; **no vuelvas a
  intentar esas tres puertas**. Se pide a Open-Meteo, sin clave y sin cuenta, y lo único
  que sale son dos coordenadas recortadas a dos decimales de una ciudad que el usuario
  escribió a mano: **no se pide el permiso de ubicación**, y sin ciudad no se abre
  ninguna conexión.
- **Las noticias se bajan una vez al día, al entrar en su pantalla, y el corte es el día
  natural.** No hay sondeo, ni servicio, ni descarga en segundo plano: hace falta que
  alguien toque NOTICIAS, y si lo guardado es de hoy no se abre ninguna conexión (ver
  `NewsRefresh`). El corte no son 24 h desde la descarga —quien mira las noticias a las
  once de la noche leería la portada de ayer toda la mañana siguiente—. Un fallo de red
  **no borra** lo anterior: se enseña la última portada diciendo que es de otro día, que
  es la misma regla que el dato del tiempo caducado. Y **no hay titulares en la home**:
  un titular bajo el reloj es algo que invita a leer cincuenta veces al día, que es justo
  lo que este launcher evita.
- **El sitio de noticias no publica ningún canal** —ni RSS, ni JSON, ni sitemap con
  contenido: comprobado— así que se lee el HTML de la portada. El análisis vive en
  `DoxaPortada`, **puro y sin Android**, y se prueba contra una portada real guardada en
  `app/src/test/resources`: nunca contra la red. Lo que no se entiende se descarta en
  lugar de rellenarse, y los enlaces se filtran al propio dominio antes de entregárselos
  al sistema.
- **El tiempo se pide al volver a la home y como mucho cada media hora**, igual que el
  uso del móvil: ni sondeo, ni servicio, ni flujo que lata (`WeatherRefresh`). La marca
  se escribe al **intentarlo**, no al acertar; contando aciertos, un teléfono sin
  cobertura pediría en cada vuelta. Y a las seis horas el dato deja de enseñarse: un
  "18°" de anoche con la misma cara que uno de ahora es la misma mentira que un cero de
  uso sin haber medido.
- **Todo resumen sale de `UsageMood.face`, nunca del escalón por tiempo.** El glifo en la
  franja de la home, la palabra en el menú y en la pantalla de Uso, y la visibilidad del
  pulso: los cuatro. Cuando cada uno se calculaba por su cuenta se contradecían en el
  dispositivo —cara `:(` porque una aplicación acaparaba, y debajo un `NORMAL` que salía
  solo del reloj—. Si añades otro sitio donde se resuma el día, sale de aquí.
- **Un glifo no es texto legible.** `:)` se dibuja con la tipografía monoespaciada, así
  que no es un icono y cabe en el sistema visual; pero no se puede leer en voz alta, y
  por eso `ZenHeaderStrip` tiene `rightDescription`. La regla de que todo estado se lea
  como texto no se cumple con que el estado *sea* caracteres.
- **Lo que no tiene nada detrás no se pinta.** El mando del reproductor aparece solo si
  hay audio o una sesión de medios viva, la marca de avisos solo si hay avisos, y el
  **pulso de uso** solo a partir de USO ALTO —con el día en calma la home queda
  exactamente como estaba—. Una barra "EN PAUSA" que no manda nada o un "00" permanente
  son ruido con forma de dato. Esta regla es la única puerta por la que algo nuevo puede
  aparecer en la home sin convertirse en una fila permanente: si no puede estar ausente
  la mayor parte del tiempo, va al menú.
- **Lo que ya dice la barra de estado, Zen no lo repite.** Al dejar de ocultarla (ver
  `SystemBarsPolicy`) se quitaron de la home el medidor de batería y la franja de
  conexiones: decían lo mismo dos centímetros más abajo. La hora se queda porque su
  tamaño **es** la pantalla de inicio, no un indicador más. Antes de añadir cualquier
  indicador nuevo, mirar si el sistema ya lo dibuja.
- **Los indicadores propios que quedan —hora y reproductor— viven SOLO en la home.** Las demás pantallas llevan su franja de cabecera y nada más: repetirlos las
  convertiría en paneles de control.
- **Un launcher se mira cincuenta veces al día.** Nada que parpadee, se anime sin motivo o
  invite a explorar. Las animaciones existentes (ecualizador, latido de carga) solo corren
  mientras el estado que representan está activo; paradas no dibujan ni un fotograma.
  La curva de **Respira** es la excepción declarada: ahí el movimiento **es** el
  contenido —se respira siguiéndolo—, vive en su propia pantalla, hay que entrar a
  propósito y solo corre mientras corre el minuto.
  Las transiciones de `ZenMotion` son la otra excepción y viven bajo la misma regla:
  duran lo que dura un cambio **que el usuario acaba de provocar** (180 ms entrando,
  120 saliendo) y sirven para decir de dónde sale lo que aparece. Nada que dure más ni
  que empiece solo.
- **Permisos**: cada uno se justifica en el README y degrada solo. Antes de añadir uno,
  comprobar si hay una vía sin permiso (ver Bluetooth por `Settings.Global`, o el mando
  del reproductor por teclas de medios).

## Comandos

```bash
./gradlew testDebugUnitTest             # 926 tests JVM (incluye UI de Compose sobre Robolectric)
./gradlew assembleDebug                 # APK -> app/build/outputs/apk/debug/
./gradlew installDebug                  # build + instalar en dispositivo conectado
./gradlew lint                          # informe -> app/build/reports/lint-results-*.html
./gradlew connectedDebugAndroidTest     # instrumentados (app/src/androidTest), necesita un dispositivo arm64
./gradlew clean
```

Un solo test:

```bash
./gradlew testDebugUnitTest --tests "com.zenlauncher.zen.domain.EssentialAppsTest"
```

## Arquitectura

Cuatro capas en un módulo, **sin framework de inyección**:

```
core/          ZenClock (los dos relojes del sistema, inyectable)
domain/        modelo, repositorios (interfaces), sesión, apps, batería, media,
               news (NewsEdition y NewsRefresh: la segunda es pura y dice cuándo toca
               bajar otra portada; NewsRepository es la frontera),
               notifications (NotificationBadges, NotificationGrouping: puras),
               notes (Note, NotesRepository, AttachmentStore, Dictation; puras:
               TextNormalizer, LinkExtractor, LexicalEmbedder, SemanticIndex,
               NoteIndexer. EmbeddingModel es la frontera para cambiar de motor
               —el umbral de parecido vive en el motor, no en quien compara),
               breathing (BreathingPattern: pura, la curva 4-6 en función del tiempo),
               reading (Book, BookRepository, BookImporter, Bookmark, Highlight; puras:
               TextReflow, HeadingDetector, TableOfContents, BookMetadata, BookBuilder,
               ReadingSearch, ReadingProgress, ReadingSettings, Paginator, Sentences,
               HighlightSpans. PdfTextSource es la frontera —y el hueco del OCR—,
               PageMeasurer la de medir texto y StudyModel la del «modo estudio»,
               declarada y vacía a propósito),
               weather (WeatherCodes traduce el código de la OMM a glifo y
               WeatherRefresh dice cuándo volver a pedir y cuándo el dato caducó, las
               dos puras; WeatherRepository es la frontera y la única que sale a la red),
               usage (todo puro: UsageTimeline pliega los eventos de Android en tramos,
               UsagePressure da el escalón del día, CompulsionDetector las tres formas de
               conducta, DistractionPolicy cuándo callarse, WeeklyUsage agrega varios
               días, UsagePatterns saca las observaciones y el veredicto, y UsageMood
               resume el día en una cara),
               scanner (puras: Quad y Corners ordenan y validan las cuatro esquinas,
               Homography resuelve la perspectiva, DocumentAspect recupera la proporción
               real de una hoja en escorzo, CaptureDecision dice cuándo disparar solo,
               Stillness lee el acelerómetro, PdfPageSize mide la hoja del PDF y
               ScanNaming pone los nombres. DocumentDetector, DocumentProcessor,
               TextRecognizer, ScanWorkspace y ScanExporter son las fronteras),
               system (políticas puras: LockTaskDecision, SystemBarsPolicy,
               MemoryTrimPolicy), stats
data/          SQLite, DataStore, LauncherApps, BatteryManager, AudioManager,
               news (DoxaNews: HttpURLConnection, nunca lanza; DoxaPortada traduce el
               HTML de la portada y NewsJson la guarda en una clave de DataStore, las
               dos puras),
               weather (OpenMeteoWeather: HttpURLConnection + org.json, sin librerías
               nuevas; nunca lanza, degrada a null),
               voice (OnDeviceDictation: reconocedor del dispositivo, sin red),
               reading (AndroidPdfTextSource: PdfRenderer del sistema, cero librerías;
               SqliteBookRepository; FileBookCoverStore),
               usage (UsageStatsRepository: solo traduce constantes de Android;
               el cálculo está en el dominio),
               scanner (OpenCvDocumentDetector y OpenCvDocumentProcessor: la única
               dependencia nativa, nunca lanzan y sueltan los Mat con MatScope;
               MlKitTextRecognizer con el modelo dentro del APK, sin red;
               CameraXScanner: vista previa, análisis y captura;
               SensorStillness: acelerómetro; FileScanWorkspace: caché privada;
               AndroidScanExporter: MediaStore y PdfDocument, sin permiso de
               almacenamiento)
system/        alarma, receptor de fin de sesión, notificación, admin de dispositivo
presentation/  theme, components, una pantalla + ViewModel por destino
```

- **El id de `EmbeddingModel` lleva versión del cálculo** (`lexico-v2`). Al tocar
  `TextNormalizer` o `LexicalEmbedder` hay que subirla: `NoteIndexer` solo reindexa lo
  que no tiene vector de ese id, y sin subirla los vectores viejos se quedan calculados
  con las reglas antiguas y comparándose con los nuevos.
- `ZenContainer.kt` es el **único** sitio donde se nombran implementaciones concretas;
  `ZenViewModelFactory.kt` las une a los ViewModel. Añadir una dependencia significa
  tocar esos dos ficheros, no las pantallas.
- Las decisiones se sacan a **funciones puras del dominio** para poder probarlas sin
  Android: `LockTaskDecision`, `SystemBarsPolicy`, `EssentialApps`, `StatsCalculator`,
  `SessionProgressCalculator`, `HomeRoleTarget`, `BreathingPattern`, `NewsRefresh`,
  `DoxaPortada`, `LexicalEmbedder`,
  `SemanticIndex`, `TextNormalizer`, `LinkExtractor`, `WeatherCodes`, `WeatherRefresh`,
  `TextReflow`, `HeadingDetector`, `TableOfContents`, `BookBuilder`, `ReadingProgress`,
  `Paginator`, `Sentences`, `HighlightSpans`,
  `UsageTimeline`, `UsagePressure`,
  `CompulsionDetector`, `DistractionPolicy`, `MemoryTrimPolicy`, `Corners`,
  `Homography`, `DocumentAspect`, `CaptureDecision`, `Stillness`, `PdfPageSize`,
  `ScanNaming`.
- **Lo que no cuenta como uso**: Zen, `com.android.systemui` y cualquier paquete capaz
  de ser pantalla de inicio. Zen no implementa Recientes, así que el gesto de recientes
  abre el `RecentsActivity` del launcher de fábrica: en el dispositivo salían 66
  "aperturas" diarias de `com.nothing.launcher` y se recomendaba restringirlo. Se
  resuelve por intent (`<queries>` de MAIN/HOME), nunca con nombres escritos a mano.
- **Una apertura es una visita, no una pantalla.** Android emite un `ACTIVITY_RESUMED`
  por cada pantalla, también al navegar *dentro* de una aplicación, así que
  `UsageTimeline` une los tramos consecutivos del mismo paquete. El orden importa: unir
  **antes** de filtrar. Al revés, las subpantallas de medio segundo desaparecen en lugar
  de sumar, y salir a la home y volver se funde en una sola visita.
- **La semana cuesta siete consultas y por eso solo se pide al abrir su pantalla**
  (`UsageViewModel.loadWeek`). Nunca desde la home, nunca en un flujo que late.
- **El veredicto de la semana reusa `UsagePressure.level` sobre la media diaria.** No
  hay una segunda tabla de umbrales para la semana: dos tablas se desincronizan y
  acaban contradiciéndose en la misma pantalla.
- **Ninguna observación sin su cifra, y ninguna cifra sin salida.** Una recomendación
  que no lleva a ninguna parte es un sermón; las que tienen arreglo dentro de Zen llevan
  a `RESTRICTED`, que ya existía. Y nunca dos observaciones sobre la misma aplicación:
  es el mismo hallazgo escrito dos veces.
- **El uso del móvil se mide solo al volver a la pantalla de inicio.** No hay sondeo, ni
  flujo que lata, ni servicio: el momento en que el dato cambia y el momento en que
  alguien puede leerlo son el mismo. Añadir un latido periódico aquí es gastar batería
  para no añadir información. El día completo, además, se relee como mucho una vez por
  minuto (`UsageViewModel.FULL_READ_INTERVAL_MILLIS`).
- **`CachedInstalledApps` es obligatorio de usar, no opcional.** Media docena de
  pantallas observan las aplicaciones instaladas; sin compartir la fuente, cada una abre
  su propio `getActivityList` por IPC. Cualquier consumidor nuevo va contra
  `container.installedApps`, nunca contra `LauncherAppsRepository`.
- **"Limpiar la RAM" del teléfono no se puede y no se finge**: desde Android 14
  `killBackgroundProcesses` solo mata procesos propios. Lo que Zen sí hace es ocupar poco
  para no ser el proceso que el sistema mate; ver `LauncherMemory` y el README.
- La sesión activa vive en DataStore como marcas de tiempo, no como un contador: la UI
  solo renderiza un cálculo derivado. Nada de servicios en primer plano.
- Fronteras marcadas hacia v0.2 (Device Owner): `AppRestrictionManager`,
  `BatterySaverController`, `ZenSessionManager`.

## Convenciones que no se negocian

- **Todo en castellano**: nombres de test entre acentos graves, comentarios y cadenas.
  Los comentarios explican *por qué* se eligió algo o qué fallo fija, nunca *qué* hace
  la línea siguiente.
- **Cada corrección lleva su test de regresión**, con un comentario que dice qué falla.
- **Las barras de progreso de Lectura son caracteres (`█░`), no un dibujo**: así el
  estado se lee tal cual con un lector de pantalla y no hay una segunda gráfica.
- **Una sola gráfica en toda la aplicación**, la de la semana, y existe porque "¿esto va
  a más o a menos?" no cabe en un número. Lleva la línea del umbral de USO ALTO —sin
  referencia, siete barras solo se comparan entre ellas— y cada barra dice su día y su
  tiempo por `contentDescription`, que es como se cumple aquí la regla de que todo estado
  se lea como texto. No se anima.
- **Sistema visual Industrial**: negro puro (AMOLED), monocromo salvo un ámbar reservado
  a marcas de estado de 6dp, sin iconos, sin ondas al tocar (`NoIndication`), sin
  animaciones decorativas. La **única** excepción tipográfica es el texto de un libro:
  serif del sistema (`ReadingSerifFamily`) y `ZenColors.Reading`, más apagado que
  `Foreground` porque son media hora seguidas de prosa sobre negro puro y no un rótulo
  de dos palabras. Fuera del lector no se usan ni una cosa ni la otra. Colores en `ZenColors`, espaciado en `ZenSpacing`, estilos
  con nombre de rol en `ZenTextStyles`, transiciones en `ZenMotion`: no fijes números
  sueltos en una pantalla.
- **Ningún test JVM puede comprobar la detección sobre una foto real.** Nada de OpenCV ni
  de ML Kit se ejecuta en la JVM. Lo que sí se prueba —y es lo que decide— son las
  funciones puras: el ordenado de esquinas contra números escritos a mano, y la
  recuperación de proporciones **contra proyecciones sintetizadas**, tomando un rectángulo
  de proporción conocida y proyectándolo con una cámara estenopeica inventada. La calidad
  de la detección con luz real se mira **instalando**, como los truncamientos de texto.
- **Ningún permiso nuevo sin justificarlo en el README.** Lectura no añadió ninguno: el
  selector de documentos del sistema no pide permiso —devuelve solo el fichero elegido—
  y la extracción de texto es del propio Android. Solo hay seis
  (`POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECORD_AUDIO`, `PACKAGE_USAGE_STATS`,
  `INTERNET`, `CAMERA`) y los seis degradan solos. `CAMERA` lo pide **solo el escáner** y
  solo al abrirlo; no lleva ningún permiso de almacenamiento al lado porque `MediaStore`
  no lo exige para lo que escribe la propia aplicación, igual que el selector de
  documentos de Lectura no pide nada. `INTERNET` lo usan **dos** funciones y solo
  dos, el tiempo y las noticias: un tercer consumidor de red es una decisión de producto,
  no un detalle. `PACKAGE_USAGE_STATS` está declarado pero **no se concede en la
  instalación**: es `signature|appop` y lo otorga el usuario a mano en Ajustes de
  Android → Acceso de uso, igual que el oyente de notificaciones. Sin él no hay pulso de
  uso, no hay aviso de distracción y la pantalla de Uso dice que no hay medida en lugar
  de enseñar un cero: la diferencia entre "no has usado el móvil" y "no puedo verlo"
  viaja en el dato (`UsageSnapshot.measured`).
  `RECORD_AUDIO` se pide al tocar «Dictar», nunca antes, y la transcripción es del
  reconocedor del propio dispositivo: el audio no se guarda ni sale del teléfono. El acceso al oyente
  de notificaciones no es un permiso del manifiesto: lo concede el usuario a mano, da a
  la vez los metadatos del reproductor y las marcas de aviso, y sin él todo funciona.
- Todo estado se lee **como texto** además de por la forma o el color (`BLOQUEADA`,
  `SONANDO`), para no depender de la agudeza visual.

## Tests

`app/src/test` cubre dominio, datos (Robolectric para SQLite y DataStore), ViewModel
(Turbine + `MainDispatcherRule`) y **pantallas de Compose sobre Robolectric**, sin
dispositivo. Detalles que muerden:

- `createComposeRule` se importa de `androidx.compose.ui.test.junit4.v2`.
- `BreatheScreenTest` pone `mainClock.autoAdvance = false`: el ejercicio corre sobre el
  reloj de fotogramas y no se queda quieto hasta el minuto, así que con el avance
  automático la primera comprobación esperaría para siempre. El tiempo lo mueve el test.
- **Un campo de texto no puede leer su valor de un flujo asíncrono.** El buscador de
  Notas leía el texto que volvía del filtro (`mapLatest` + consulta a SQLite) y perdía
  letras al escribir: teclear «aburri» dejaba «buar». `NotesViewModel` expone `query`
  aparte del estado justo por eso.
- **Toda `LazyColumn` con dos secciones necesita claves con prefijo**, y un test que
  renderice **dos** elementos en cada una: con claves repetidas Compose lanza excepción,
  y aquí eso deja el teléfono sin pantalla de inicio.
- **Una retícula perezosa no puede llevar el aire final como un elemento más.** En el
  primer fotograma de Notas —cargando, sin notas todavía— ese espaciador era el único
  elemento y quedaba de ancla; al llegar las notas y pasar al final, `LazyStaggeredGrid`
  se desplazaba para mantenerlo a la vista y la pantalla se abría por la mitad de la
  lista. Va en `contentPadding`. Fijado en `NotesScreenTest`, con un test que cambia el
  estado **después** de la primera composición: renderizando la lista ya llena no falla.
- `HomeScreenTest` fija `@Config(qualifiers = "w411dp-h891dp")`: la pantalla por defecto
  de Robolectric es mucho más baja que un móvil real. La home se desplaza a propósito,
  así que lo que vive bajo el pliegue se alcanza con `performScrollTo()`.
- **Una `StateFlow` de un ViewModel no siempre despacha antes del primer `awaitItem()`.**
  `UsageViewModelTest` comprueba con `advanceUntilIdle()` que volver dos veces a la home
  no relee el día entero; hecho dentro de un `turbine.test { }` el contador salía a cero
  porque las corrutinas encoladas aún no habían corrido.
- **Ningún test JVM puede fijar un recorte de texto.** La fuente de relleno de
  Robolectric mide ~1 px por glifo: "Oviedo, Pedernales, República Dominicana" da
  `maxIntrinsicWidth = 40,5` frente a los 359 disponibles, así que `hasVisualOverflow`
  es **siempre false** y `lineCount` siempre 1, con `maxLines = 1` o con 2. Y
  `onNodeWithText` casa con el texto **semántico**, que sigue completo aunque en pantalla
  se vea cortado. Los dos truncamientos que aparecieron en el buscador de ciudades y en
  la pantalla del tiempo se encontraron **mirando el dispositivo**, y no hay test que los
  proteja: al tocar `maxLines` o un rótulo largo, hay que instalar y mirar. Un test verde
  aquí no es prueba de que el texto se lea entero.
- **`MutablePreferences.remove` devuelve el valor borrado, y sobre una clave ausente eso
  es un `null` que Kotlin desempaqueta a `long` y revienta.** Pasa siempre que se limpie
  algo que no estaba —cerrar una sesión ya cerrada, quitar una ciudad que nunca se puso—
  y una excepción ahí deja el teléfono sin pantalla de inicio. Se borra con `-=`
  (`minusAssign`), que devuelve `Unit`. Fijado en `DataStorePreferencesRepositoryTest`.
- **Una lectura del tiempo falsa tiene que llevar la hora del reloj falso.** Con
  `observedAtMillis = 1_000L` y `FakeZenClock` en 2023, la lectura nace caducada y
  `WeatherViewModel` la esconde —correctamente—, así que el test falla por el dato de
  prueba y no por el código.
- **Una migración que crea tablas las crea con la forma ACTUAL, no con la que tenían
  cuando se estrenaron.** Saltar de v1 o v2 a v4 ejecutaba `createReadingSchema` —que ya
  incluye lo de v4— y después el `ALTER` de v4 encima: `duplicate column name`, y el
  teléfono sin pantalla de inicio. De ahí la guarda `readingJustCreated` en `onUpgrade`.
  Cualquier paso futuro que **retoque** tablas ya existentes necesita la misma guarda, y
  el test de migración tiene que cubrir el salto largo, no solo el de la última versión.
- **Ningún test JVM puede comprobar el reparto en páginas de verdad.** La fuente de relleno
  de Robolectric mide ~1 px por glifo, así que un párrafo de cien caracteres cabe entero en
  cualquier línea y todo el libro entra en una hoja. `PaginatorTest` prueba el corte contra
  un medidor de mentira con cuentas exactas; `ReaderScreenTest` solo comprueba que el
  mecanismo se mueve. Por lo mismo, **mantener pulsado en un test hay que hacerlo por el
  borde izquierdo**: en el centro el toque cae más allá del texto y `getOffsetForPosition`
  devuelve el último carácter, así que se seleccionaría siempre la última frase.
- **Un fondo opaco tapa el texto pero no para el dedo.** Los mandos del lector se dibujan
  encima de la hoja, y sin un `pointerInput` que se trague los toques, tocar el hueco de al
  lado de un botón atravesaba hasta el texto y pasaba página o cancelaba la selección. Se
  encontró **en el dispositivo**, intentando escribir una nota: el campo cogía el foco y la
  hoja de detrás se llevaba la selección por delante. Ver `Modifier.swallowTaps`.
- **Un `pointerInput` con `detectTapGestures` no añade acción de tocar a la semántica.** No
  se puede filtrar por `hasClickAction()` para desambiguar esos nodos, aunque la inyección
  de toques sí funcione sobre ellos.
- **Una `StateFlow` con `WhileSubscribed` devuelve su valor inicial mientras nadie la
  colecta.** `LibraryViewModelTest` comprobaba `state.value` después de importar y salía
  `Idle`: en la aplicación siempre hay una pantalla colectando, en el test no. Se
  comprueba dentro de un `turbine.test { }`. Y lo que dependa de un momento intermedio de
  la importación no se puede observar con un lector de mentira —termina en el mismo
  instante en que el test se suspende para mirarlo—: eso se prueba sobre el estado.
- Los fakes de Lectura viven en `fakes/ReadingFakes.kt`, aparte por la misma razón que
  `FakeUsageRepository`: la mitad de lo que hay que probar es el camino en el que el
  teléfono **no puede** extraer texto o el fichero no se deja leer.
- Los fakes compartidos están en `fakes/Fakes.kt`, y `FakeUsageRepository` aparte: el
  acceso de uso se pone y se quita, y la mitad de lo que hay que probar es el camino sin
  concesión.

## Build

Kotlin DSL, versiones centralizadas en `gradle/libs.versions.toml` (añade ahí y
referencia como `libs.*`). Puntos que difieren de plantillas antiguas y que **fallarán
si se asume lo contrario**:

- **AGP 9.3.1** compila Kotlin de forma nativa. No hay plugin
  `org.jetbrains.kotlin.android` a propósito: es incompatible con el DSL de AGP 9.
- `compileSdk` usa la forma de bloque de AGP 9, no la asignación `compileSdk = 36`.
- `minSdk = 34` (Android 14): las APIs por debajo no necesitan guardas de compatibilidad.
- Reglas de R8 en `app/src/main/keepRules/rules.keep`, no en `proguard-rules.pro`. En
  release `optimization { enable = false }`, así que R8 está apagado.
- Room/KSP **no** son opción: KSP es incompatible con el Kotlin integrado de AGP 9 (ver
  README). Por eso la persistencia es SQLite a mano.
- `org.gradle.configuration-cache=true`: leer estado mutable en configuración rompe el
  build en vez de funcionar en silencio.
- Java/Kotlin target 11. UI: Compose (BOM) + Material 3, tema propio siempre oscuro.

`local.properties` guarda la ruta del SDK y no está versionado: nunca lo commitees ni
lo reescribas.
