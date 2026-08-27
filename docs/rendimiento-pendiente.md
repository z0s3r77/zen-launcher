# Rendimiento de Zen: lo que queda pendiente

## Contexto

Sale de una auditoría de Zen como launcher, hecha sobre el commit `de8f8a6`, con un
requisito por encima de los demás: **con la pantalla de inicio a la vista y sin que nadie
la toque, el launcher debe hacer el mínimo trabajo posible**.

Lo que se arregló va en el commit que trae este documento. Aquí queda **lo que no se
tocó y por qué**, para que la próxima vez no haya que volver a descubrirlo.

Aviso que vale para todo el documento: la auditoría fue **lectura de código y tests
JVM**, no medición en dispositivo. Donde se afirma un coste se describe el mecanismo
—qué se despierta y cada cuánto—, nunca milisegundos.

---

## 1. Nada de esto está medido en el dispositivo

Es lo primero y lo más importante. Las mejoras del commit están razonadas y fijadas con
tests, pero **no confirmadas con un teléfono delante**. Antes de dar el requisito por
cumplido:

```bash
# CPU en reposo: home a la vista, sin tocar, 60 s.
adb shell dumpsys gfxinfo com.zenlauncher.zen framestats
adb shell top -H -b -n 12 -d 5 | grep zen
```

Lo que hay que ver: **cero frames pedidos mientras nada cambia**. Antes del commit debía
verse aproximadamente uno por segundo (el `tickerFlow` de `SessionViewModel`, que la
Activity colectaba en todas las pantallas).

```bash
# Despertares y receptores, tras una hora de pantalla encendida en la home.
adb shell dumpsys batterystats --charged com.zenlauncher.zen

# Arranque en frío, diez veces.
adb shell am start -W -n com.zenlauncher.zen/.presentation.ZenActivity
```

Y los casos límite que ningún test cubre, porque `CLAUDE.md` ya avisa de que las
pantallas se miran instalando:

- abrir escáner → salir → abrir escáner → pedir OCR (debe seguir vivo);
- estar en Notas y pulsar Inicio (debe volver a la home);
- bloquear y desbloquear con música sonando;
- volver a la home cincuenta veces seguidas, mirando el retardo del primer fotograma;
- rotar el teléfono en cada pantalla.

---

## 2. Sin baseline profile y con R8 apagado

`app/build.gradle.kts` tiene `optimization { enable = false }` en release y el proyecto
no trae `androidx.profileinstaller` ni ningún perfil.

**Es la mayor palanca que queda sobre el "tiempo hasta ver el launcher".** Sin perfil, el
primer arranque tras cada actualización interpreta y compila al vuelo todo el runtime de
Compose. Y sin R8, el APK carga miles de clases que nunca se usan: OpenCV, ML Kit y
CameraX entran enteros aunque el escáner no se abra.

No se tocó porque cambia el empaquetado del launcher y merece una decisión aparte, con su
propia verificación: un R8 mal configurado sobre reflexión de ML Kit se manifiesta como un
escáner que revienta en release y funciona en debug.

Al abordarlo, activar también los informes del compilador de Compose
(`composeCompiler { reportsDestination }`): es la única forma objetiva de comprobar qué
composables se saltan la recomposición y cuáles no.

---

## 3. Rotar durante un escaneo borra el escaneo

`ScannerRoute.kt` hace `DisposableEffect(Unit) { onDispose(viewModel::discardAll) }`, y el
manifiesto **no declara `android:configChanges` ni `android:screenOrientation`**. Cualquier
cambio de configuración —rotación, tamaño de letra, idioma, tema, ventana dividida—
destruye la Activity, dispone la composición y ejecuta `discardAll()`, que hace
`workspace.clear()`. El `ScannerViewModel` sobrevive (vive en el `ViewModelStore`), así que
la pantalla se recompone enseñando un documento cuyas páginas ya no existen en disco.

No se arregló porque la salida no es técnica sino de producto: **o se bloquea la
orientación de la home, o se declaran los `configChanges`**, y las dos cosas cambian el
comportamiento del launcher entero, no solo del escáner. Hoy esa decisión no está tomada
explícitamente en ninguna parte.

---

## 4. `MlKitTextRecognizer.available` es ahora siempre `true`

Es un matiz **introducido a propósito** en este commit y conviene que quede escrito.

Antes, `available` era `client != null`, y leerlo construía el cliente de ML Kit. Como
`ScannerViewModel` lo consulta al construirse, abrir el escáner cargaba el modelo de OCR
aunque nadie fuera a pedirlo —justo lo contrario de lo que decía el comentario de la
propia clase—. Ahora responde `true` sin construir nada.

**Lo que se paga**: en un dispositivo donde ML Kit no pudiera inicializarse, el botón de
OCR se pinta y falla (`OCR_FAILED`) en vez de no existir. Rompe en ese caso concreto la
regla de "lo que no tiene nada detrás no se pinta". Con el modelo empaquetado dentro del
APK es un caso casi imposible, y el intercambio es no cargar megabytes en el proceso del
launcher para responder a una pregunta.

Si algún día molesta, la salida no es volver atrás: es que `ScannerViewModel` actualice
`ocrAvailable` en el estado tras el primer intento fallido, en lugar de fijarlo al
construirse.

---

## 5. El tiempo caducado puede quedarse pintado

`WeatherViewModel` calcula `stale` con `clock.wallTimeMillis()` **dentro del `combine`**, y
ese `combine` solo se reevalúa cuando cambia una preferencia. Sin escritura de por medio,
una lectura de hace ocho horas se sigue enseñando en la franja de la home.

Es exactamente el "00 permanente" que este proyecto evita: un `18°` de anoche con la misma
cara que uno de ahora. No se tocó porque es corrección, no rendimiento. La salida:
meter el latido de minuto como tercera fuente del `combine`, o recalcular la caducidad en
el punto donde se pinta.

---

## 6. `CachedInstalledApps` no puede llevar `replay = 1`

Queda escrito porque **es una trampa en la que ya se cayó una vez durante este trabajo**.

La auditoría señaló un hueco: con `replay = 0`, un colector que llegue después de que el
flujo compartido ya haya emitido se queda esperando a la siguiente alta o baja de
aplicaciones. Se intentó arreglar con `replay = 1` y el test
`CachedInstalledAppsTest.soltar la cache la vacia` lo tumbó, con razón: con replay,
`shareIn` conserva la lista dentro de su propio buffer, y esa copia **no la puede soltar
`release()`**. El aviso de memoria dejaría de liberar nada —que es todo el propósito de
`LauncherMemory`— y además un colector nuevo recibiría la lista vieja justo después de
haberla soltado.

Se revirtió. El hueco original lo tapa el prefijo cacheado de `observeInstalledApps()`, y
la ventana que queda —caché vaciada por un aviso de memoria mientras el `share` sigue vivo
diez segundos— es estrecha. Si alguna vez se ve una home sin aplicaciones tras un aviso de
memoria, la salida **no** es `replay`: es que `release()` también cierre el `share`.

---

## 7. La retícula de la home no es perezosa

`ZenAppGrid` es una `Column` de `Row`, no un `LazyVerticalGrid`, así que se componen todas
las celdas haya las que haya.

Fue correcto mientras existía el tope de ocho aplicaciones, y la propia clase explica por
qué sigue siéndolo: una rejilla perezosa anidada dentro de una pantalla que ya se desplaza
no puede medirse. Pero **el tope se quitó** al hacer que la home se desplace, así que hoy
nada impide poner cuarenta aplicaciones en el inicio y componer cuarenta celdas en cada
pasada.

No es un problema medido —cuarenta celdas de texto no son caras— pero es el punto donde la
decisión dejó de estar respaldada por el argumento que la sostenía. Merece una medida
antes que un cambio.

---

## 8. Cosas menores que quedaron fuera

- **`StudyModel.kt`** no tiene ninguna referencia en todo `app/src/main` salvo su propia
  declaración. Está vacío a propósito según `CLAUDE.md`; cuesta cero en ejecución, pero es
  una interfaz sin implementación ni consumidor.
- **`ZenNotificationListener` sigue siendo un servicio enlazado por el sistema** mientras el
  acceso esté concedido. No se puede evitar —es lo que da los metadatos del reproductor y
  las marcas de aviso— y ahora al menos su trabajo va con antirrebote y fuera del hilo
  principal.
- **`InstalledApp.sortKey`** sigue existiendo para listas cortas y tests. Las tres listas
  largas ya ordenan con `String.CASE_INSENSITIVE_ORDER`, que no reserva nada; conviene no
  volver a usar `sortedBy { it.sortKey }` sobre la lista completa de aplicaciones.
- **Pulsar Inicio con el menú de la home abierto** vuelve a la home pero **no cierra el
  menú**: `menuOpen` es un `rememberSaveable` dentro de `HomeScreen` y cerrarlo desde fuera
  exigiría izarlo. Es un detalle de comportamiento, no un fallo.

---

## Orden sugerido para lo que queda

| Orden | Trabajo | Por qué |
|---|---|---|
| 1 | Medir en el dispositivo (§1) | Sin esto, el requisito no está cerrado |
| 2 | Baseline profile + `profileinstaller` (§2) | Mayor palanca sobre el arranque |
| 3 | Decidir orientación / `configChanges` (§3) | Hoy se pierde trabajo del usuario |
| 4 | Caducidad del tiempo (§5) | Enseña un dato viejo como si fuera de ahora |
| 5 | R8 en release (§2) | Requiere su propia verificación en release |
| 6 | Medir la retícula con muchas aplicaciones (§7) | Antes de cambiar nada |
