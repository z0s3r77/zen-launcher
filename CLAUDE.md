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
  únicas filas permanentes son **Todas las aplicaciones** (bajo la retícula), **Notas
  rápidas** y **Menú**; el resto del alto es retícula, y lo que se sume ahí empuja algo
  fuera de la pantalla. El menú abierto **sustituye a la pantalla entera** y
  deja solo la franja de cabecera: lo que se añada ahí no compite con el reloj.
- **Lo que no tiene nada detrás no se pinta.** El mando del reproductor aparece solo si
  hay audio o una sesión de medios viva, y la marca de avisos solo si hay avisos. Una
  barra "EN PAUSA" que no manda nada o un "00" permanente son ruido con forma de dato.
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
  Las transiciones de `ZenMotion` son la otra excepción y viven bajo la misma regla:
  duran lo que dura un cambio **que el usuario acaba de provocar** (180 ms entrando,
  120 saliendo) y sirven para decir de dónde sale lo que aparece. Nada que dure más ni
  que empiece solo.
- **Permisos**: cada uno se justifica en el README y degrada solo. Antes de añadir uno,
  comprobar si hay una vía sin permiso (ver Bluetooth por `Settings.Global`, o el mando
  del reproductor por teclas de medios).

## Comandos

```bash
./gradlew testDebugUnitTest             # 215 tests JVM (incluye UI de Compose sobre Robolectric)
./gradlew assembleDebug                 # APK -> app/build/outputs/apk/debug/
./gradlew installDebug                  # build + instalar en dispositivo conectado
./gradlew lint                          # informe -> app/build/reports/lint-results-*.html
./gradlew connectedDebugAndroidTest     # instrumentados (app/src/androidTest), necesita dispositivo
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
               notifications (NotificationBadges, NotificationGrouping: puras),
               system (políticas puras: LockTaskDecision, SystemBarsPolicy), stats
data/          SQLite, DataStore, LauncherApps, BatteryManager, AudioManager
system/        alarma, receptor de fin de sesión, notificación, admin de dispositivo
presentation/  theme, components, una pantalla + ViewModel por destino
```

- `ZenContainer.kt` es el **único** sitio donde se nombran implementaciones concretas;
  `ZenViewModelFactory.kt` las une a los ViewModel. Añadir una dependencia significa
  tocar esos dos ficheros, no las pantallas.
- Las decisiones se sacan a **funciones puras del dominio** para poder probarlas sin
  Android: `LockTaskDecision`, `SystemBarsPolicy`, `EssentialApps`, `StatsCalculator`,
  `SessionProgressCalculator`, `HomeRoleTarget`.
- La sesión activa vive en DataStore como marcas de tiempo, no como un contador: la UI
  solo renderiza un cálculo derivado. Nada de servicios en primer plano.
- Fronteras marcadas hacia v0.2 (Device Owner): `AppRestrictionManager`,
  `BatterySaverController`, `ZenSessionManager`.

## Convenciones que no se negocian

- **Todo en castellano**: nombres de test entre acentos graves, comentarios y cadenas.
  Los comentarios explican *por qué* se eligió algo o qué fallo fija, nunca *qué* hace
  la línea siguiente.
- **Cada corrección lleva su test de regresión**, con un comentario que dice qué falla.
- **Sistema visual Industrial**: negro puro (AMOLED), monocromo salvo un ámbar reservado
  a marcas de estado de 6dp, sin iconos, sin ondas al tocar (`NoIndication`), sin
  animaciones decorativas. Colores en `ZenColors`, espaciado en `ZenSpacing`, estilos
  con nombre de rol en `ZenTextStyles`, transiciones en `ZenMotion`: no fijes números
  sueltos en una pantalla.
- **Ningún permiso nuevo sin justificarlo en el README.** Solo hay dos
  (`POST_NOTIFICATIONS`, `USE_EXACT_ALARM`) y ambos degradan solos. El acceso al oyente
  de notificaciones no es un permiso del manifiesto: lo concede el usuario a mano, da a
  la vez los metadatos del reproductor y las marcas de aviso, y sin él todo funciona.
- Todo estado se lee **como texto** además de por la forma o el color (`BLOQUEADA`,
  `SONANDO`), para no depender de la agudeza visual.

## Tests

`app/src/test` cubre dominio, datos (Robolectric para SQLite y DataStore), ViewModel
(Turbine + `MainDispatcherRule`) y **pantallas de Compose sobre Robolectric**, sin
dispositivo. Detalles que muerden:

- `createComposeRule` se importa de `androidx.compose.ui.test.junit4.v2`.
- `HomeScreenTest` fija `@Config(qualifiers = "w411dp-h891dp")`: la pantalla por defecto
  de Robolectric es mucho más baja que un móvil real. La home se desplaza a propósito,
  así que lo que vive bajo el pliegue se alcanza con `performScrollTo()`.
- Los fakes compartidos están en `fakes/Fakes.kt`.

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
