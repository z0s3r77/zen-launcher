# Rediseñar e implementar el sistema "Notas" de Zen

## Contexto

El botón "Notas" del launcher Zen ya tiene una base sólida (commit `bc4a080`): captura
rápida con texto/dictado/imágenes/enlaces, guardado en SQLite manual, buscador literal,
un motor de embeddings léxico (`LexicalEmbedder`, hashing trick + TF sublineal +
bigramas, sin red ni modelo neuronal) y un indexador (`NoteIndexer`) que propone
conexiones entre notas parecidas. Eso cubre los puntos 1–6 de la lista de prioridades
del usuario (nota rápida, texto+imágenes+enlaces, voz local, almacenamiento, buscador,
embeddings y conexiones).

Lo que falta —y es el encargo real de esta tarea— son los puntos **7, 8 y 9**:
"Desarrollar una idea", detectar temas recurrentes, y convertir notas en proyectos. El
dominio ya dejó sitio para esto: `Note.stage` (`SEED → DEVELOPED → PROJECT → DONE`) y
`Note.projectId` existen en el modelo y en la tabla SQLite desde el principio, y
`NotesRepository` ya tiene `saveProject/assignToProject/notesInProject/observeProjects`
completos — pero **ninguna pantalla los usa todavía**. `NotesScreen.kt` tiene un
comentario explícito: la fila "Desarrollar una idea" no existe porque "una fila que no
hace nada enseña a desconfiar de las que sí funcionan, que es justo por lo que se quitó
el PRONTO de la pantalla de inicio". Ese principio (no pintar nada sin nada real
detrás) es la restricción de diseño más importante de este plan, y afecta directamente
a cómo se implementa la parte de "pensar" con IA local: sin LLM instalado, la pregunta
central / enfoques / preguntas de "Desarrollar una idea" se generan con **heurísticas
anclas a datos reales de la propia nota y sus conexiones** (nunca relleno genérico que
saldría igual para cualquier idea) — decisión confirmada con el usuario. Contraargumentos
y "proyectos derivados" generados por IA quedan fuera de esta v1 por ser los que más
necesitan razonamiento real; se deja el punto de enchufe (`IdeaDevelopmentModel`, igual
que `EmbeddingModel`) para cuando haya un LLM local.

Todo lo nuevo sigue exactamente los patrones ya usados en el proyecto: DI manual en
`ZenContainer.kt`, pantalla+ViewModel con `combine(...).stateIn(...)`, rutas como
constantes String en `ZenRoute.kt`, dominio puro testeado con JUnit sin Android, fakes en
`app/src/test/.../fakes/`. No se reescribe nada de lo que ya funciona (captura, buscador,
conexiones por pares) — solo se añade lo que falta y se enchufa donde el propio código ya
lo esperaba.

## Fase 1 — "Desarrollar una idea"

### Dominio nuevo

**`domain/notes/IdeaDevelopment.kt`**
- `data class IdeaPrompts(val centralQuestion: String?, val approaches: List<String>, val questions: List<String>)`.
- `interface IdeaDevelopmentModel { fun generate(idea: String, relatedCount: Int): IdeaPrompts }` — mismo patrón de frontera que `EmbeddingModel`: hoy una implementación heurística, mañana un LLM local sin tocar quien lo llama.
- `class HeuristicIdeaDevelopmentModel : IdeaDevelopmentModel` (pura, sin Android, usa `TextNormalizer.stems`):
  - `approaches`: mapa fijo interno stem→categoría (`tecnológico`, `psicológico`, `social`, `creativo`, ampliable) — **solo se devuelve una categoría si alguno de sus stems gatillo aparece de verdad en los stems de la idea**; si nada dispara ninguna, la lista vuelve vacía y la sección no se pinta (mismo principio que el reproductor de medios).
  - `questions`: banco pequeño de plantillas, cada una condicionada a una señal real detectada en el texto (p. ej. negación → pregunta sobre qué se pierde; `relatedCount >= 3` → pregunta que referencia el número real de notas relacionadas, no una cifra inventada). Nunca se generan las N preguntas si ninguna condición dispara.
  - `centralQuestion`: si la idea ya termina en `?`, devuelve `null` (no repetir lo que el usuario ya escribió); si no, se compone a partir del stem dominante de la idea.
  - Todos los umbrales/listas de palabras en un `companion object`, igual que `LexicalEmbedder.relatedThreshold`.
- Test: `domain/IdeaDevelopmentTest.kt` — un caso por señal (idea con negación, idea ya en forma de pregunta, idea sin ningún stem reconocido → todo vacío, `relatedCount` afectando a `questions`).

**Reutilizar sin tocar**: `NoteIndexer.similarTo(text)` ya hace exactamente "notas parecidas a un texto suelto que todavía no es una nota" (comentario en el propio código lo anticipa: "sirve para... 'desarrollar una idea'"). No hace falta ningún método nuevo en `NotesRepository` ni en `NoteIndexer`.

### `ZenContainer.kt`
Añadir junto a las demás dependencias de notas (línea ~89, junto a `embedder`):
```kotlin
val ideaDevelopment: IdeaDevelopmentModel by lazy { HeuristicIdeaDevelopmentModel() }
```

### Presentation

**`presentation/navigation/ZenRoute.kt`**: nueva ruta con argumento opcional (mismo
patrón que `NOTIFICATIONS_ROUTE`):
```kotlin
const val DEVELOP = "develop"
const val DEVELOP_NOTE_ARG = "nota"
const val DEVELOP_ROUTE = "$DEVELOP?$DEVELOP_NOTE_ARG={$DEVELOP_NOTE_ARG}"
fun develop(noteId: String? = null): String = ...
```

**`presentation/notes/DevelopIdeaViewModel.kt`** (nuevo)
- Recibe `NotesRepository`, `NoteIndexer`, `IdeaDevelopmentModel`, `ZenClock`, `appScope`.
- Si llega `noteId`, precarga el cuerpo de esa nota como idea inicial (`notes.note(id)`).
- `ideaText: StateFlow<String>` separado del `state` derivado — mismo motivo documentado
  en `NotesViewModel.query` (un campo de texto no puede leer de un flujo async filtrado).
- `state: StateFlow<DevelopIdeaUiState>` con `related: List<Note>` (vía
  `indexer.similarTo(idea)`, debounced con `mapLatest`) y `prompts: IdeaPrompts`
  (recalculado junto con `related.size` para que `questions` conozca el conteo real).
- `saveAsNote()`: si venía de una nota existente, `notes.save(note.copy(stage = DEVELOPED))`
  (la etapa avanza como consecuencia de un uso real, no por un selector suelto); si es
  una idea nueva, crea una `Note` nueva en `DEVELOPED` desde el arranque.
- `convertToProject(title: String)`: solo visible/llamable cuando `related.size >= 3`
  (ver Fase 3) — crea el `Project`, `assignToProject` para la nota actual + las
  relacionadas, pone `stage = PROJECT`.

**`presentation/notes/DevelopIdeaScreen.kt`** (nuevo) — sigue el esqueleto de
`QuickNoteScreen`/`NoteDetailScreen` (`ZenScreen(onSwipeBack = onBack)`,
`ZenHeaderStrip`, `MonoLabel`/`ZenListRow`/`ZenHairline`, sin iconos):
1. Campo de idea (texto + fila "Dictar", reutilizando `Dictation` igual que
   `QuickNoteScreen` — mismo componente, no se duplica lógica de voz).
2. Sección "CONEXIONES" — solo si `related` no está vacío: "Esta idea se relaciona con
   N notas anteriores", lista tocable (reusa `ZenListRow`, abre `NoteDetailScreen`).
3. Sección "PREGUNTA CENTRAL" — solo si `prompts.centralQuestion != null`.
4. Sección "ENFOQUES" — solo si `prompts.approaches` no está vacío.
5. Sección "PREGUNTAS" — solo si `prompts.questions` no está vacío.
6. Fila "Guardar" (siempre disponible si hay texto) y fila "Convertir en proyecto"
   (solo si `related.size >= 3`, ver Fase 3).

**`ZenNavHost.kt`**: añadir `composable(route = ZenRoute.DEVELOP_ROUTE, arguments = ...)`
junto al bloque de `NOTE_ROUTE`, cableando `DevelopIdeaViewModel` vía `factory`.

**`NotesScreen.kt`**: sustituir el comentario/hueco actual por una segunda fila fija,
igual de simple que "Nota rápida":
```kotlin
ZenListRow(label = stringResource(R.string.notes_develop), index = "··", onClick = onDevelopIdea)
```
Colocada justo debajo de "Nota rápida" (mismo orden que el mockup del encargo: captura
arriba, pensar debajo, buscador después). Quitar el comentario que documentaba su
ausencia deliberada — ya no aplica.

**`NoteDetailScreen.kt`**: nueva fila "Desarrollar esta idea" (entre las conexiones y el
borrado, o donde no compita con el cuerpo de la nota) que navega a
`ZenRoute.develop(note.id)`.

**`strings.xml`**: `notes_develop`, `develop_title`, `develop_placeholder`,
`develop_connections` (`"Se relaciona con %1$d notas anteriores"`, ya con plural real),
`develop_central_question`, `develop_approaches`, `develop_questions`, `develop_save`,
`develop_convert_project`.

## Fase 2 — Detectar temas recurrentes

Pura, en `domain/notes/`, sin pantalla propia: se apoya en datos que Zen ya calcula
(stems normalizados, grafo de `NoteLink`), nunca en afirmaciones psicológicas sobre el
usuario (principio explícito del encargo).

**`domain/notes/RecurringThemes.kt`** (nuevo, objeto puro, mismo estilo que
`StatsCalculator`):
```kotlin
data class RecurringWord(val stem: String, val noteCount: Int)
data class RecurringCluster(val noteIds: Set<String>)

object RecurringThemes {
    fun words(notes: List<Note>, minNotes: Int = 5): List<RecurringWord>
    fun clusters(notes: List<Note>, links: List<NoteLink>, minSize: Int = 3): List<RecurringCluster>
}
```
- `words`: para cada nota, `TextNormalizer.stems(note.indexableText())` **como conjunto**
  (no cuenta repeticiones dentro de la misma nota, cuenta en cuántas notas distintas
  aparece); filtra por `noteCount >= minNotes`; ordena desc. `indexableText()` es
  `internal` en `NoteIndexer.kt` — hay que abrirla (quitar `internal` o mover la
  extensión a un sitio compartido) para reutilizarla aquí sin duplicar la lógica de "qué
  entra en el índice de una nota".
- `clusters`: construye un grafo no dirigido con los `NoteLink` en estado `ACCEPTED`
  (las conexiones que el usuario confirmó, no las sugerencias sin responder) y calcula
  componentes conexas por BFS/union-find; descarta componentes `< minSize`.
- Test: `domain/RecurringThemesTest.kt` — palabra bajo el umbral no aparece, clusters de
  tamaño 2 no cuentan, notas ya en el mismo proyecto siguen agrupándose igual (el
  filtrado por proyecto ya asignado se hace en Fase 3, no aquí).

**Dónde se muestra**: en `NotesScreen`, una sección nueva "PATRONES" (rótulo a decidir en
`strings.xml`, p. ej. `notes_patterns`) **que solo aparece si `words` o `clusters`
devuelven algo** — nada pintado si no hay nada detrás. Cada `RecurringWord` es una fila
de una línea ("N notas mencionan «palabra»") que al tocarla llama a
`notes.search(stem)` (método que **ya existe**, cero código nuevo de búsqueda) y navega
a la lista de resultados. Los `RecurringCluster` se muestran como la propuesta de
proyecto de la Fase 3 (mismo dato, una sola sección).

**`NotesViewModel.kt`**: añadir `patterns: List<RecurringWord>` y
`projectSuggestions: List<RecurringCluster>` a `NotesUiState`, calculados con
`RecurringThemes` sobre `all` (la lista ya cargada) dentro del mismo `mapLatest` que
arma el resto del estado — nada de una consulta nueva a SQLite, es una función pura
sobre datos que la pantalla ya tiene en memoria.

## Fase 3 — Convertir notas en proyectos

El dominio (`Project`, `NotesRepository.saveProject/assignToProject/notesInProject/observeProjects`)
y el fake de test (`FakeNotesRepository` ya implementa las cuatro operaciones) están
completos desde antes de este plan — falta solo la superficie de UI y la conexión con
las sugerencias de la Fase 2.

### Presentation

**`ZenRoute.kt`**: `PROJECTS = "projects"`, y ruta con argumento para el detalle
(`PROJECT_ROUTE = "project/{proyecto}"`, `fun project(id: String)`), mismo patrón que
`NOTE_ROUTE`.

**`presentation/notes/ProjectsViewModel.kt`** + **`ProjectsScreen.kt`** (nuevos, mismo
esqueleto que `NotesScreen`): lista de `observeProjects()`, cada fila con el conteo de
`notesInProject(id)`, fila roja "Marcar terminado" en el detalle (pone `done = true`,
lo que a su vez pone `stage = DONE` en las notas del proyecto — decisión: hacerlo
explícito en el ViewModel, iterando `notesInProject` y llamando `notes.save` con
`stage = DONE`, no un trigger oculto en el repositorio).

**`NotesScreen.kt`**: fila "Proyectos" **solo visible si `observeProjects()` no está
vacío** (mismo principio de "nada sin nada detrás"; con cero proyectos, no hay fila).

**Sugerencia de proyecto (desde Fase 2)**: en la sección "PATRONES", cada
`RecurringCluster` se pinta como "N ideas podrían formar un proyecto" con dos botones
(`ZenTagButton`, mismo componente que "Conectar"/"Ignorar" en `NoteDetailScreen`):
- **Aceptar**: abre un paso mínimo de un solo campo de texto (título del proyecto,
  precargado con el `displayTitle` de la nota más reciente del clúster, editable) →
  `saveProject` + `assignToProject` para cada nota del clúster + `stage = PROJECT`.
- **Ignorar**: se descarta solo para esta sesión de la app (estado en memoria del
  ViewModel, no persistido) — simplificación deliberada documentada en el propio código:
  a diferencia de los pares de notas (`ignoredPairs`, que si son persistentes porque el
  cálculo es determinista y barato de repetir), persistir "clústers ignorados para
  siempre" exigiría una tabla nueva para un caso de uso secundario; si en el uso real
  molesta, se añade después.
- Un clúster ya completamente contenido en un proyecto existente no se vuelve a
  proponer: `NotesViewModel` filtra `clusters` contra `note.projectId != null` antes de
  pasarlo a la UI.

**Asignación manual** (para notas sueltas, no solo clústers detectados):
`NoteDetailScreen` gana una fila "Proyecto" que muestra el proyecto actual (si tiene) o
"Sin proyecto", y al tocarla abre un selector simple (lista de `observeProjects()` +
opción "Nuevo proyecto") — reusa `assignToProject`.

**`strings.xml`**: `notes_projects`, `project_title`, `project_done`,
`note_project_row`, `note_project_none`, `note_project_new`, `notes_patterns`,
`notes_project_suggestion` (con `%1$d`), `notes_project_suggestion_accept`,
`notes_project_suggestion_ignore`.

## Verificación

- `./gradlew testDebugUnitTest --tests "com.zenlauncher.zen.domain.IdeaDevelopmentTest"`
  y el mismo patrón para `RecurringThemesTest` — dominio puro primero.
- `./gradlew testDebugUnitTest` completo: no debe bajar de 389 tests pasando (los que ya
  hay) más los nuevos de cada fase (heurística, temas recurrentes, ViewModels nuevos,
  pantallas Compose sobre Robolectric siguiendo `NotesScreenTest`/`NoteDetailScreenTest`
  como plantilla — incluye el caso de `LazyColumn` con claves duplicadas si una sección
  nueva convive con otra en la misma lista).
- `./gradlew assembleDebug` para confirmar que compila con AGP 9 / Kotlin nativo (sin
  Room/KSP, coherente con lo ya existente).
- Prueba manual en el dispositivo (o emulador API 34+): abrir Notas → "Desarrollar una
  idea" con texto nuevo y con una idea sin ninguna nota relacionada (las secciones deben
  desaparecer, no salir vacías) → volver a una nota existente y usar "Desarrollar esta
  idea" → comprobar que su etapa pasa a "DEVELOPED" (visible como texto en
  `NoteDetailScreen`) → acumular ≥3 notas relacionadas por tema y comprobar que aparece
  la sugerencia de proyecto en "Patrones" → aceptarla y verificar en "Proyectos" que
  quedaron asociadas.
