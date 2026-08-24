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

- **La home no se desplaza** — el reloj está siempre en el mismo píxel; el menú abierto
  ocupa el sitio de la retícula en lugar de alargar la página. La lista completa de
  aplicaciones se abre desde la fila **Todas las aplicaciones**, justo debajo de la
  retícula.
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
  intención. En una pantalla de inicio, lo que abre algo tiene que verse; Zen ya no tiene
  más gesto propio que el de volver desde un lateral.
- **Sin barra de gestos** — la línea blanca del borde inferior no se dibuja: no hay
  "atrás" ni "recientes" a los que ir desde una pantalla de inicio. La **barra de estado
  sí se queda**: ocultarla quitaba los iconos de notificación, pero hacía que Android la
  sacara de golpe encima del contenido en cada gesto desde un borde, y una barra que
  aparece y desaparece llama más la atención que una que simplemente está. El precio
  asumido es que la hora, la batería y la cobertura salen dos veces en la home. El gesto
  de atrás no hace nada aquí, salvo cerrar el menú si está abierto (ver
  [límites](#la-barra-de-gestos-no-se-puede-desactivar)).
- **Botón ZEN** — a la derecha de la hora, la única acción con botón propio. Todo lo
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
- **Notas** — captura de ideas en la pantalla de inicio, a un toque de la fila **Notas**.
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
  que **no lista nada hasta que se escribe**. Colgaba de Ajustes como una lista con las
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
permanentes, sin analítica y sin red. Zen no pide ningún permiso de red.

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
sale del dispositivo: Zen sigue sin pedir `INTERNET`.

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
| `SpeechRecognizer.createOnDeviceSpeechRecognizer` | Dictar una nota, sin red | `RECORD_AUDIO`, **opcional** |
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

Los tres **degradan solos**: sin alarma exacta se usa una inexacta, sin notificaciones la
sesión se cierra igualmente al volver a Zen, y el dictado desaparece por triplicado —sin
reconocedor de dispositivo la fila no se pinta, sin el paquete de voz del idioma tampoco,
y si se deniega el permiso la fila lo dice como texto y el teclado sigue igual. Ninguno
bloquea el flujo.

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

236 tests en la JVM, sin dispositivo. Cubren el cálculo del tiempo restante (incluidos
reinicio y manipulación del reloj), sesión completada y abandonada, duración registrada,
idempotencia del cierre, cálculo de batería consumida —con sus casos no fiables—,
persistencia en SQLite y en DataStore, selección de aplicaciones, la resolución de las
aplicaciones esenciales por candidatos de paquete, el sembrado de la pantalla de inicio,
la lectura del acceso al oyente de notificaciones, qué cuenta como aviso pendiente y
cómo se agrupan, la política de barras del sistema, el patrón de respiración guiada y cinco ViewModel.

Las pantallas se cubren con **tests de UI de Compose sobre Robolectric**, también sin
dispositivo: `HomeScreenTest`, `SettingsScreenTest`, `NotificationsScreenTest`,
`ActiveSessionScreenTest` y `BreatheScreenTest` —este último mueve el reloj de
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
