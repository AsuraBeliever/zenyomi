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

| 2026-09-08 | source-api | `animesource/` completo (22 ficheros nuevos) | `source-api/src/main/kotlin/…` | `4b5b90a37` | KMP (`commonMain`/`androidMain`) aplanado a `src/main/kotlin`; `PreferenceScreen` pierde el `actual` |
| 2026-09-08 | dominio | `source/anime/{model,repository,service,interactor}` | igual ruta | `4b5b90a37` | `Pin`/`Pins` **no** se duplican: se usan los de Mihon |
| 2026-09-08 | dominio | `NetworkToLocalAnime` | `domain/anime/interactor/` | `4b5b90a37` | Pendiente saldado al llegar `AnimeSourceManager` |

Dependencia añadida: `org.nanohttpd:nanohttpd:2.3.1`, como `api()` en `source-api`.
`HttpServer` extiende NanoHTTPD y aparece en la superficie que ven las extensiones
(`AnimeHttpSource.createHttpServer()`, `Video.usesHttpServer()`), así que quitarlo
habría roto la paridad de API con extensions-lib 17.

### Qué se duplica y qué se reutiliza

No todo tipo de Aniyomi merece un gemelo. El criterio aplicado:

- **Se duplica** cuando el tipo forma parte de la identidad del dominio de anime o
  aparece en el esquema de BD o en la API de extensiones: `AnimeUpdateStrategy`,
  `AnimeCover`, `AnimeLibraryPreferences`.
- **Se reutiliza** el de Mihon cuando es genérico y sin semántica de medio:
  `Pin`/`Pins`, `TriState`, `DateColumnAdapter`, `StringListColumnAdapter`,
  `MemoColumnAdapter`.

| 2026-09-08 | fuentes | `AndroidAnimeSourceManager` | `app/…/animesource/` | — | Derivado del de Mihon; la interfaz de dominio se reescribió a la forma suspending de Mihon en vez de la síncrona de Aniyomi |
| 2026-09-08 | ext | motor de extensiones de anime (13 ficheros) | `app/…/animeextension/`, `domain/…/animeextension/` | — | **Derivado del motor de Mihon**, no de Aniyomi. Ver nota abajo |

### El motor de extensiones se deriva de Mihon, no se copia de Aniyomi

Regla 3 del charter: el motor de extensiones es el de Mihon, porque el de Aniyomi es
justamente el que falla. Así que `AnimeExtensionLoader`, `AnimeExtensionManager`,
`AnimeExtensionInstaller` y compañía **no** se portaron desde Aniyomi: se derivaron de
los ficheros equivalentes de Mihon, cambiando el tipo de fuente y los metadatos que
identifican una extensión de anime.

| | Mihon (manga) | Zenyomi (anime) |
|---|---|---|
| Característica del APK | `tachiyomi.extension` | `tachiyomi.animeextension` |
| Clase de la fuente | `tachiyomi.extension.class` | `tachiyomi.animeextension.class` |
| Factoría | `tachiyomi.extension.factory` | `tachiyomi.animeextension.factory` |
| Nombre / lib / aviso | `tachiyomix.*` | `aniyomix.*` |
| Versiones de lib soportadas | `1.4`, `1.6` | `14.0`, `16.0` |

Consecuencia práctica: Zenyomi carga los APK de extensiones de anime publicados para
Aniyomi, pero con la lógica de carga, verificación de firmas e instalación de Mihon,
que es la que está mantenida.

Lo que **no** se renombra al derivar: `BasePreferences.ExtensionInstaller` es un enum
de preferencias compartido de Mihon y lo usan ambos motores.

### Reglas de R8 para la API de extensiones

Mihon protege su `source-api` de manga con `-keep` **sin** `allowoptimization`
(`source-api/consumer-proguard.pro`). No es cosmético: con optimización, R8 marca como
`final` los métodos que ninguna subclase *dentro de la app* sobrescribe, y `getId()` es
uno de ellos. Las extensiones viven fuera del APK, R8 no las ve, y al cargarlas revientan:

```
LinkageError: Method ...Jellyfin.getId() overrides final method
in class AnimeHttpSource
```

El fallo **solo existe en release**; en debug no hay R8 y todo funciona. Se detectó
compilando el release y probándolo, no leyendo el código. Las reglas equivalentes para
`eu.kanade.tachiyomi.animesource.**` están ahora en el mismo fichero.

**Regla derivada:** cualquier tipo nuevo que las extensiones puedan extender necesita su
`-keep` sin `allowoptimization`, y el release hay que probarlo con una extensión real
antes de publicar.

### Pendientes conocidos

| Qué | Por qué espera |
|---|---|
| Inserción por lotes de anime | `AnimeRepository` solo tiene `insertAnime` por elemento; Mihon usa `insertNetworkManga(List)`. `NetworkToLocalAnime` expone ya la forma de llamada de Mihon (`invoke`) pero itera, sin batch. |
| ~~Fuente local de anime~~ | **Hecha.** Versión mínima propia, no portada: lee la carpeta `localanime` del almacenamiento elegido. |
| Descargas de anime | `source-local` de Aniyomi sin portar; `AndroidAnimeSourceManager` no siembra el mapa con ella. Es lo que permite reproducir vídeos guardados en el dispositivo. |
| Renombrado de carpetas de descarga | `AndroidAnimeSourceManager` no llama a `renameSource` porque no existe el gestor de descargas de anime. |
| Catálogo remoto de extensiones de anime | `AnimeExtensionApi.findExtensions()` devuelve lista vacía. La cadena de tiendas de Mihon (`ExtensionStoreRepository` + 4 modelos de red + servicio, 13 ficheros) está tipada contra la `Extension` de manga y necesita su contraparte. **Las extensiones ya instaladas como APK no dependen de esto**: las descubre `AnimeExtensionLoader`. |
| ~~Verificación end-to-end del cargador~~ | **Hecha (2026-09-09).** Ver abajo. |

### Verificación del cargador contra una extensión real

Probado en el emulador (Pixel 10 Pro XL, Android 17) con
`eu.kanade.tachiyomi.animeextension.all.jellyfin` v14.17, del repositorio archivado de
Aniyomi. La cadena completa funciona:

1. El cargador encuentra el APK por su característica `tachiyomi.animeextension`.
2. Valida la versión de librería. La extensión no declara `aniyomix.extensionLib`, así
   que entra la ruta de reserva: se deriva de `versionName` (`14.17` → `14.0`), que sí
   está en las soportadas.
3. Verifica la firma y la clasifica como no confiable, correcto en instalación nueva.
4. Tras confiarla, **carga e instancia las clases del APK**: la fábrica declarada en
   `tachiyomi.animeextension.class` produce 3 objetos `AnimeSource`.

Corrección que salió de esta prueba: el cargador quitaba el prefijo `"Tachiyomi: "` del
nombre heredado de Mihon; las extensiones de anime se llaman `"Aniyomi: ..."`.

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
