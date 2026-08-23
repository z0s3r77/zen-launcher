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
- **Notas rápidas** — sitio reservado en la pantalla de inicio, marcado `PRONTO` y sin
  reaccionar al toque hasta que exista. La idea es tomar notas que generen recordatorios
  y enlacen ideas entre sí.
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
Reaccionar a la onda real exige `Visualizer`, que pide el permiso **`RECORD_AUDIO`** —el
micrófono— y que además ya no captura la mezcla global del dispositivo desde hace varias
versiones de Android. Pedir el micrófono para animar cuatro barras sería un intercambio
pésimo, así que la animación es sintética: periodos distintos y no múltiplos entre sí
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

Ambos **degradan solos**: sin alarma exacta se usa una inexacta, y sin notificaciones la
sesión se cierra igualmente al volver a Zen. Ninguno bloquea el flujo.

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

189 tests en la JVM, sin dispositivo. Cubren el cálculo del tiempo restante (incluidos
reinicio y manipulación del reloj), sesión completada y abandonada, duración registrada,
idempotencia del cierre, cálculo de batería consumida —con sus casos no fiables—,
persistencia en SQLite y en DataStore, selección de aplicaciones, la resolución de las
aplicaciones esenciales por candidatos de paquete, el sembrado de la pantalla de inicio,
la lectura del acceso al oyente de notificaciones, qué cuenta como aviso pendiente y
cómo se agrupan, la política de barras del sistema y cinco ViewModel.

Las pantallas se cubren con **tests de UI de Compose sobre Robolectric**, también sin
dispositivo: `HomeScreenTest`, `SettingsScreenTest`, `NotificationsScreenTest` y
`ActiveSessionScreenTest`. Cada
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
