# Registro de portes desde Aniyomi

Toda pieza de código traída desde Aniyomi se anota aquí, con el SHA de origen, para
poder auditar la procedencia y volver a comparar cuando Aniyomi cambie.

Referencia de origen principal: `aniyomi/main` @ `4b5b90a37` (2026-09-05).
Referencia de contraste: `v0.18.1.2` (2025-10-28, última release publicada). Ver ADR-0003.

## Formato

| Fecha | Área | Origen (Aniyomi) | Destino (Zenyomi) | SHA origen | Commit | Notas |
|---|---|---|---|---|---|---|

## Entradas

| Fecha | Área | Origen (Aniyomi) | Destino (Zenyomi) | SHA origen | Notas |
|---|---|---|---|---|---|
| 2026-09-08 | db | `data/src/main/sqldelightanime/dataanime/*.sq` (10 tablas) | igual ruta | `4b5b90a37` | Sin cambios en el SQL |
| 2026-09-08 | db | `data/src/main/sqldelightanime/view/*.sq` (8 vistas) | igual ruta | `4b5b90a37` | Sin cambios |
| 2026-09-08 | db | `data/src/main/sqldelightanime/migrations/113–138.sqm` | **no portado** | `4b5b90a37` | Ver ADR-0004 |
| 2026-09-08 | source-api | `animesource/model/AnimeUpdateStrategy.kt` | `source-api/src/main/kotlin/…` | `4b5b90a37` | Reescrito; ruta `src/main/kotlin` de Mihon en vez de `src/commonMain` |
| 2026-09-08 | source-api | `animesource/model/FetchType.kt` | `source-api/src/main/kotlin/…` | `4b5b90a37` | Reescrito |

| 2026-09-08 | db | adaptadores de columna de anime | `data/…/AnimeDatabaseAdapter.kt` | — | Escrito aquí; `DateColumnAdapter`, `StringListColumnAdapter` y `MemoColumnAdapter` se reutilizan de Mihon |
| 2026-09-08 | di | proveedor de `AnimeDatabase` | `app/…/di/AnimeBindings.kt` | — | Escrito aquí siguiendo el patrón de `AppBindings`; fichero aparte para no tocar el de Mihon |
| 2026-09-08 | dominio | `entries/anime/{model,repository,interactor}` (18) | `domain/anime/…` | `4b5b90a37` | `entries.anime` → `anime`; añadido `@Inject` de Metro |
| 2026-09-08 | dominio | `items/episode/{model,repository,interactor,service}` (13) | `domain/episode/…` | `4b5b90a37` | `items.episode` → `episode`; añadido `@Inject` |
| 2026-09-08 | dominio | `library/anime/LibraryAnime.kt`, `source/anime/model/DeletableAnime.kt` | igual ruta | `4b5b90a37` | Sin cambios de estructura |
| 2026-09-08 | dominio | `aniyomi/domain/anime/{SeasonAnime,SeasonDisplayMode}.kt` | `domain/anime/model/` | `4b5b90a37` | Movidos del paquete raíz `aniyomi.*` al árbol `tachiyomi.domain.anime` |
| 2026-09-08 | source-api | `animesource/model/{SAnime,SAnimeImpl}.kt` | `source-api/src/main/kotlin/…` | `4b5b90a37` | Ruta `src/main/kotlin` de Mihon |
| 2026-09-08 | dominio | `EntryCover` | **no portado** | `4b5b90a37` | Interfaz marcadora vacía de la generalización de Aniyomi; `AnimeCover` queda suelto (ADR-0001) |
| 2026-09-08 | dominio | preferencias de episodio dentro de `LibraryPreferences` | `library/service/AnimeLibraryPreferences.kt` | `4b5b90a37` | **No** se tocó el fichero de Mihon; clase paralela, acceso por propiedad y claves propias |

| 2026-09-08 | data | `entries/anime/AnimeMapper.kt` | `data/anime/AnimeMapper.kt` | `4b5b90a37` | Solo reescritura de paquetes |
| 2026-09-08 | data | `AnimeRepositoryImpl`, `AnimeRelationRepositoryImpl`, `EpisodeRepositoryImpl`, `EpisodeSanitizer` | `data/{anime,episode}/` | `4b5b90a37` | **Reescritos** al estilo de Mihon: sin `DatabaseHandler` |
| 2026-09-08 | data | `handlers/anime/*` (4 ficheros) | **no portado** | `4b5b90a37` | Mihon eliminó esa abstracción; ver nota abajo |

### Nota: el `DatabaseHandler` no se porta

Aniyomi conserva el envoltorio `AnimeDatabaseHandler` heredado de Tachiyomi
(`handler.awaitList { ... }`). Mihon lo eliminó: con `generateAsync` las consultas de
sqldelight ya son suspending, así que sus repositorios usan la base directamente
(`database.mangasQueries.x(...).awaitAsList()`).

Como Mihon manda en el *cómo* (regla 2 del charter), los repositorios de anime se
reescribieron a ese estilo en vez de copiarse. La transformación fue sistemática:

| Aniyomi | Zenyomi |
|---|---|
| `handler.awaitOne { q }` | `database.q.awaitAsOne()` |
| `handler.awaitOneOrNull { q }` | `database.q.awaitAsOneOrNull()` |
| `handler.awaitList { q }` | `database.q.awaitAsList()` |
| `handler.subscribeToList { q }` | `database.q.subscribeToList()` |
| `handler.await(inTransaction = true) { … }` | `database.transaction { … }` |
| `handler.awaitOneOrNullExecutable(…) { … }` | `database.transactionWithResult { … }` |
| `.executeAsOne()` | `.awaitAsOne()` |

Dentro de una transacción, Mihon cualifica cada consulta con `database.`.

### Pendientes conocidos

| Qué | Por qué espera |
|---|---|
| `NetworkToLocalAnime` | Necesita `AnimeSourceManager`, de la capa de fuentes. Se porta con ella. |

Configuración añadida (no portada literalmente): la segunda base sqldelight se declara
en `data/build.gradle.kts` como `AnimeDatabase`, paquete `tachiyomi.data.anime`. Aniyomi
usa el paquete `tachiyomi.mi.data` (herencia de su applicationId `xyz.jmir.tachiyomi.mi`)
y un dialecto distinto; Zenyomi usa el dialecto y el `generateAsync` de Mihon, según la
regla de que Mihon manda en el *cómo*.

## Renombrados sistemáticos al portar

| Aniyomi | Zenyomi |
|---|---|
| `tachiyomi.domain.entries.anime` | `tachiyomi.domain.anime` |
| `tachiyomi.domain.items.episode` | `tachiyomi.domain.episode` |
| `tachiyomi.domain.entries.manga` | *(no se porta — se usa el de Mihon)* |
| `tachiyomi.domain.items.chapter` | *(no se porta — se usa el de Mihon)* |
| `i18n-aniyomi` / `AYMR` | `i18n-anime` / `ANMR` |
