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

## Tienda de extensiones de anime

Origen: la cadena de tienda de Mihon (`mihon.data.extension.*`), no Aniyomi. Aniyomi
nunca tuvo tienda con firma verificada, así que aquí no hay porte sino derivación del
lado manga, según la regla 3 del charter.

| Fichero nuevo | Derivado de |
|---|---|
| `data/.../animeextension/model/BaseNetworkAnimeExtensionStore.kt` | `BaseNetworkExtensionStore.kt` |
| `data/.../animeextension/model/NetworkAnimeExtensionStore.kt` | `NetworkExtensionStore.kt` |
| `data/.../animeextension/model/NetworkLegacyAnimeExtension.kt` | `NetworkLegacyExtension.kt` |
| `data/.../animeextension/model/NetworkLegacyAnimeExtensionRepo.kt` | `NetworkLegacyExtensionRepo.kt` |
| `data/.../animeextension/repository/AnimeExtensionStoreRepositoryImpl.kt` | homónimo de manga |
| `data/.../animeextension/service/AnimeExtensionStoreService.kt` | `ExtensionStoreService.kt` |
| `domain/.../animeextension/repository/AnimeExtensionStoreRepository.kt` | homónimo de manga |

`ExtensionStore` (el modelo de dominio) **se reutiliza tal cual**: no contiene nada
específico de manga. Duplicarlo habría sido deuda gratuita.

**Diferencia real con el lado manga.** Los repos de Mihon publican `index_v2` y
`shortName`; el `repo.json` de Aniyomi no publica ninguno de los dos. En
kotlinx.serialization un campo nulable *sin valor por defecto* sigue siendo
obligatorio, así que el modelo de manga rechazaba el repo oficial de Aniyomi. Ambos
campos llevan ahora `= null`. Es el tipo de detalle que no se ve derivando a ojo: el
código compila igual y falla solo contra datos reales.

## Backup de anime

El anime viaja en **el mismo fichero** `.tachibk` que el manga, no en uno aparte: dos ficheros
obligarían al usuario a acordarse de guardar y restaurar los dos, y perder uno sería perder
media app.

Campos nuevos en `Backup`, a partir del número 200 para dejar sitio de sobra a Mihon:

| Campo | Número |
|---|---|
| `backupAnime` | 200 |
| `backupAnimeSources` | 201 |

Protobuf ignora los campos que no conoce, así que la compatibilidad va en los dos sentidos:
un backup de Mihon restaura aquí con estos campos vacíos, y un backup de Zenyomi lo puede
leer Mihon quedándose con la parte de manga. **Verificado en el emulador** fabricando un
backup al que se le quitaron los campos 200 y 201: `BackupRestoreJob` termina con SUCCESS,
sin excepciones de serialización, y deja la biblioteca de anime intacta.

`BackupAnime` y `BackupEpisode` numeran desde 1 por su cuenta: no comparten mensaje con
`BackupManga`/`BackupChapter`, así que reutilizar los números bajos no cuesta nada.

La restauración **fusiona, no reemplaza**: gana el progreso más avanzado de los dos lados, de
modo que restaurar un backup viejo nunca des-marca un episodio ya visto.

## Trackers de anime

No es un porte de Aniyomi sino una **capacidad añadida a los trackers de Mihon**. La interfaz
`AnimeTracker` va al lado de `Tracker`, no dentro: Komga, Kavita, MangaUpdates y compañía son
servicios solo de manga, y ofrecerlos para anime invitaría al usuario a registrarse en algo
que nunca podrá guardar una serie. El login, el logout y el estado de la cuenta se quedan en
`Tracker`: uno inicia sesión en AniList una vez, no una vez por tipo de medio.

| Servicio | Qué hizo falta |
|---|---|
| AniList | `SaveMediaListEntry` y `DeleteMediaListEntry` reciben un `mediaId` y no distinguen el tipo, así que añadir, actualizar y borrar valen tal cual; solo se duplicaron las **lecturas** con `type: ANIME` y `episodes` en vez de `chapters` |
| MyAnimeList | Endpoints propios de anime: `/v2/anime`, `num_episodes`, `num_episodes_watched`. Nada reutilizable salvo el OAuth |

Los modelos de anime (`AnimeTrack`, `AnimeTrackSearch`) son un árbol paralelo con nombres
honestos —`last_episode_seen`, no un campo neutro—, que es justo lo que evita confundirlos con
los de manga en la llamada.

**Detalle que ya nos mordió una vez:** todos los campos de `MALAnimeListItemStatus` llevan
valor por defecto. Un campo nulable *sin* default sigue siendo obligatorio para
kotlinx.serialization, que es exactamente lo que rompió la tienda de extensiones con el
`repo.json` de Aniyomi.

## El player pasa a Activity propia

`AnimePlayerScreen` era una pantalla del navegador de `MainActivity`. Picture-in-picture no
puede vivir ahí: se declara **por actividad**, y además necesita `configChanges` para que
entrar en PiP no destruya y recree el player a mitad de reproducción.

Ponerle `supportsPictureInPicture` y `configChanges` a `MainActivity` habría cambiado también
cómo sobrevive a la rotación el lado de manga, que es exactamente lo que prohíbe la regla 1.
Mihon ya separa su lector en `ReaderActivity` por el mismo motivo, así que el player sigue esa
misma forma: `AnimePlayerActivity`, lanzada con un `Intent`, y el contenido extraído a un
composable `AnimePlayerContent` reutilizable.

Al dejar de haber navegador que hacer *pop*, la pantalla gana barra propia con título y botón
de cerrar — dos parámetros (`title`, `onBack`) que antes se pasaban y no se usaban.

Los iconos salen del set del proyecto (`Close`, `FlipToBack`); no hay `picture_in_picture` en
`icons/material-symbols`, y dibujar un SVG a mano por un botón no compensa.

## Ajustes del player, y tres fallos que salieron al probarlos

`PlayerPreferences` es propia, no un añadido a `ReaderPreferences`: nada de lo que hay aquí
significa algo para un lector de manga.

Al conectar las preferencias salieron tres fallos que **ya estaban** en el código:

1. **Reanudar nunca funcionó.** `loadfile` es `<url> [<flags> [<index> [<options>]]]` y se
   pasaba `start=71` en la posición del *index*. mpv rechazaba el comando entero, así que
   cualquier episodio con progreso guardado abría a negro. Solo se veía en el log de mpv.
2. **Llamar a mpv desde el hilo principal colgaba la app.** Toda llamada a libmpv espera al
   hilo del núcleo de mpv. El sondeo y el `onDispose` lo hacían desde el hilo de UI, y salir de
   un episodio recién abierto congelaba la interfaz hasta que Android lanzaba un ANR.
3. **El observador de log se registraba después de `init()`**, así que cualquier queja de mpv
   sobre las opciones era invisible. Es lo que hizo que (1) tardara en aparecer.

Ahora **todo el ciclo de vida de libmpv va por un único hilo** (`mpv-lifecycle`). libmpv es un
singleton de proceso: crear y destruir en paralelo lo corrompe. El desmontaje espera a ese
hilo con un tope de 1,5 s — mpv tiene que soltar el surface antes de que muera, así que no se
puede lanzar y olvidar, pero una espera acotada nunca llega al límite de ANR de 5 s.

`alang`, `slang` y `speed` se fijan como **opciones antes de `init()`**, no como propiedades
después: mpv las aplica al abrir el fichero, así que ponerlas más tarde no hace nada hasta el
siguiente. Verificado midiendo: a 2x el vídeo avanza 64 s en 32 s reales.

## Estadísticas de anime

Pantalla propia, accesible desde la biblioteca de anime igual que el historial, en vez de una
sección dentro de la de Mihon: el árbol es paralelo y así su pantalla queda intacta. Reutiliza
las cadenas de Mihon donde significan lo mismo (`label_overview_section`, `label_started`,
`label_mean_score`…), que es donde compartir sí sale gratis.

Los contadores se calculan recorriendo la biblioteca con los interactors existentes, no con
vistas agregadas nuevas. Una biblioteca de anime son decenas de entradas, no los miles que
puede tener una de manga, y unas cuantas consultas son más fáciles de mantener correctas que
un SQL agregado escrito a mano — que es justo donde ya me equivoqué al derivar mappers.

**"Tiempo visto" es la suma de `last_second_seen`**, es decir lo más lejos que se ha llegado en
cada episodio. `animehistory` no guarda duración propia que sumar, al contrario que el lado
manga. No acumula revisionados; para eso haría falta una columna nueva.

Solo se cuentan como *tracked* los servicios que implementan `AnimeTracker`. Incluir los de
manga inflaría el número con entradas que no son de anime.

## Búsqueda, orden e insignias en la biblioteca de anime

La rejilla de anime se escribió aparte en su día porque los componentes de la de Mihon son
internos a su paquete. Eso sigue: aquí se añaden búsqueda, orden e insignia de episodios
pendientes sin tocar nada suyo, reutilizando solo `SearchToolbar`, que sí es público.

El filtrado y la ordenación se hacen **en memoria**, no en SQL. La biblioteca entera ya está
cargada para dibujarse, filtrar decenas de entradas no cuesta nada frente a otra consulta, y
así el orden es el mismo haya búsqueda o no.

Dos detalles que no son casualidad:

- La insignia solo aparece si quedan episodios por ver. Un `0` sobre cada anime terminado es
  ruido, no información.
- Una búsqueda sin resultados **no** es una biblioteca vacía. Decirle al usuario que añada
  algo cuando lo que pasa es que no encuentra lo que buscó es un mal consejo, así que son dos
  estados distintos.

## Instalar extensiones de anime: dos cosas que faltaban

Instalar una extensión de anime desde la app **no funcionaba**, y fallaba en silencio: el APK
se descargaba (HTTP 200) y ahí se acababa todo. Dos causas encadenadas, las dos por haber
portado las clases sin portar lo que las rodea.

1. **`AnimeExtensionInstallActivity` y `AnimeExtensionInstallService` no estaban declaradas en
   el manifest.** Las clases existían desde el porte, pero Android no puede arrancar un
   componente que no está declarado: `Unable to start service ... not found`.
2. **`InstallerAnime` pide `AnimeExtensionManager` por Injekt**, igual que el instalador de
   manga pide el suyo, pero solo el de manga estaba registrado en `MetroInteropModule`. El
   servicio arrancaba y moría al instante con `InjektionException`.

Mihon usa Metro para casi todo pero mantiene Injekt como puente para el código que las
extensiones tocan. Al portar el instalador se trajo la dependencia de Injekt sin el registro
que la sostiene.

Además, la lista de extensiones disponibles **solo se cargaba al añadir un repositorio**. Al
reabrir la app la pantalla salía vacía y parecía que se hubieran perdido los repositorios
configurados, cuando seguían en la base de datos. Ahora se recarga al abrir, como hace Mihon,
y hay un botón de recarga manual.

Verificado de punta a punta en el emulador con el repo `aniyomi-revived-anime-extensions`:
añadir el repo, ver las disponibles, instalar, confiar en la firma y que la fuente aparezca.

## Recuperarse de un catálogo que no carga

Cuando una fuente fallaba, la pantalla mostraba el texto de la excepción y nada más. Un
desafío de Cloudflare, una sesión caducada y un sitio caído se veían exactamente igual, y no
quedaba más salida que volver atrás.

Ahora hay **Reintentar** y **Abrir en WebView**, que es como se resuelve de verdad un desafío:
el usuario lo pasa en el WebView, las cookies se comparten con el cliente de red, y al
reintentar la fuente funciona. Solo se ofrece WebView para fuentes HTTP; una local o un stub
no tienen sitio que abrir.

**Verificado con AnimeLatinoHD**, que fallaba con "Failed to bypass Cloudflare": abrir el
WebView carga el sitio y, al reintentar, el error cambia a `HTTP 404` — la petición ya pasa
Cloudflare y lo que falla es el endpoint de una extensión desactualizada. Es exactamente la
distinción que antes era imposible de hacer desde la app.
