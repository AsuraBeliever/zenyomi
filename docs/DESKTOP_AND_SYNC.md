# Zenyomi fuera del móvil — plan de escritorio

**Escrito:** 2026-09-16
**Estado:** decisiones de producto tomadas; pendiente el ADR y el arranque
**Decidió:** Alan, 2026-09-16

Llevar Zenyomi al PC sin que eso signifique mantener dos aplicaciones. El sync entre
dispositivos queda para después, pero el diseño no se le cierra la puerta: la sección 7 dice
exactamente qué hay que respetar mientras tanto.

---

## 1. Lo que se decidió

| Decisión | Elegido | Qué implica |
|---|---|---|
| Alcance de la primera versión | **Anime y manga con fuentes, desde el principio** | El escritorio no es una versión recortada: hace lo que hace el móvil |
| Código compartido | **Hasta la interfaz incluida** | Compose Multiplatform. Una feature se escribe una vez y corre en las dos |
| Biblioteca sin sync | **Cada máquina la suya** | Cero trabajo de puente. Dos bibliotecas separadas hasta que llegue el sync |
| Sistema | **Linux ahora, Windows cuando madure** | Nada específico de Linux en el código compartido, pero solo se empaqueta para Linux |
| Entregas durante la migración | **Nada hasta que se pueda usar** | No habrá builds de escritorio a medias. El avance se mide de otra forma: sección 6 |
| La app de Android mientras tanto | **Sigue evolucionando normal** | Features y correcciones siguen saliendo. Esto obliga a la regla de la sección 3 |
| Si las extensiones de anime no corren en la JVM | **Invertir lo que haga falta** | Nadie lo ha hecho antes. Se acota con un punto de control: sección 8 |

---

## 2. La arquitectura, en una frase

Zenyomi pasa a ser una aplicación **Kotlin Multiplatform con dos objetivos**, Android y
escritorio JVM, que comparten todo hasta la interfaz incluida. No existe «la app de escritorio»
como proyecto aparte: existe una aplicación con dos salidas.

Lo único que no se comparte es lo que de verdad es distinto en cada sitio — dónde se guardan los
ficheros, cómo se avisa al usuario, cómo se pinta un vídeo — y eso vive detrás de interfaces
declaradas una vez y resueltas dos. Esa frontera es la sección 5.

---

## 3. La regla que hace que esto funcione

> **La migración ocurre en `develop`, en sitio, commit a commit. Nunca en una rama larga.**
> Android tiene que compilar y ser publicable en todos y cada uno de esos commits.

No es una preferencia de estilo: es lo que hace compatibles dos de tus decisiones que de otro
modo se pelearían. Has pedido que la app de Android siga evolucionando normal. Si la migración
viviera en una rama aparte durante meses, cada feature nueva del móvil abriría una brecha, y esa
rama acabaría siendo imposible de reconciliar. Es la forma clásica de matar un proyecto así.

Se evita porque **el código de Compose Multiplatform también compila para Android**. Migrar una
pantalla no la saca del móvil: la deja funcionando igual y además disponible para el PC. Así que
no hay «rama de migración» contra la que rebasar nada — hay una secuencia de refactorizaciones
pequeñas, cada una completa, cada una mergeada, con el móvil verde todo el rato.

Esto además ya está en el charter: cada commit compila, nada de WIP en `develop`.

---

## 4. El inventario real

### Corrección de una cifra que di mal

En la versión anterior de este documento dije que **576 de 1.155 ficheros importan Android**. El
número era engañoso: contaba `androidx.compose.*`, que es precisamente la parte
**multiplataforma** y no es deuda. Medido bien:

| | Ficheros | Qué significa |
|---|---|---|
| Total Kotlin | 1.155 | |
| Importan `android.*` de verdad | **258** (22%) | Esto es lo que hay que resolver |
| Importan `androidx.compose.*` | 347 | Viene gratis con Compose Multiplatform |
| Otro `androidx` (work, core, activity…) | 207 | Parte es deuda, parte tiene equivalente multiplataforma |

La migración es bastante más pequeña de lo que la primera estimación sugería.

### Dónde vive el Android de verdad

| Módulo | Ficheros con `android.*` | Lectura |
|---|---|---|
| `domain` | 4 de 177 | Prácticamente portable ya |
| `presentation-core` | 2 de 40 | Prácticamente portable ya |
| `source-api` | 6 de 45 | Casi portable |
| `data` | 7 de 47 | Casi portable; falta el driver de SQLDelight para escritorio |
| `core` | 20 de 57 | Es el módulo de plataforma: se esperaba |
| `app / eu.kanade.presentation` | 37 de 196 | Las pantallas están mejor de lo que parecía |
| `app / eu.kanade.tachiyomi.ui` | 78 de 174 | Aquí está el grueso: Activities, ViewModels, lector y player |

De las importaciones de Android, 291 son de `android.content` — es decir, `Context`,
`Intent` y `SharedPreferences`. El `Context` es la pieza que se filtra por todas partes, y
sustituirlo es mecánico pero toca muchos sitios.

### Lo que juega a favor y ya está en el repositorio

- **El esquema ya está preparado para sincronizar.** `mangas`, `chapters`, `animes` y
  `episodes` llevan `last_modified_at`, `version` e `is_syncing`, con triggers que los mantienen
  solos. Herencia de Tachiyomi, sin usar. Ver sección 7.
- **El build ya sabe de multiplataforma.** Existe
  `gradle/build-logic/src/main/kotlin/PluginKotlinMultiplatform.kt` e `i18n` ya lo usa. Añadir
  un objetivo de escritorio es extender ese fichero.
- **Las librerías de la interfaz ya son multiplataforma.** Coil 3, Voyager, Metro,
  kotlinx-*: todas tienen soporte de escritorio. Mihon lleva tiempo modernizándose hacia ahí sin
  proponérselo.
- **mpv es el mismo en las dos máquinas.** libmpv es la librería que usa cualquier reproductor
  de escritorio de Linux. Lo que cambia es el envoltorio, no el motor.

---

## 5. La costura: lo único que no se comparte

Cada fila es una interfaz declarada una vez en el código común y resuelta dos veces.

| Pieza | Android (hoy) | Escritorio | Dificultad |
|---|---|---|---|
| Preferencias | `SharedPreferences` | Fichero en `~/.config` | Baja, pero toca mucho sitio |
| Almacenamiento | SAF / `UniFile` | `java.io.File` | Baja — en escritorio es más fácil |
| Red | OkHttp | OkHttp | Ninguna: ya es el mismo |
| Imágenes | Coil 3 | Coil 3 | Ninguna: ya es multiplataforma |
| Navegación | Voyager | Voyager | Ninguna: ya es multiplataforma |
| Inyección | Metro | Metro | Ninguna: ya es multiplataforma |
| Tareas de fondo | WorkManager (10 ficheros) | Planificador propio con corrutinas | Media |
| Notificaciones | `NotificationCompat` (9 ficheros) | Notificaciones de escritorio | Media |
| WebView (Cloudflare, ajustes de fuente) | WebView de Android | Chromium embebido (CEF) | Alta — pesa ~100 MB |
| Carga de extensiones | `PackageManager` + APK instalado | `dex2jar` + capa de compatibilidad | Alta — sección 8 |
| **Lector de manga** | **Android Views** | **Reescribir en Compose** | **Alta — ver abajo** |
| **Player de anime** | `SurfaceView` + libmpv | libmpv por JNA, empotrado por handle de ventana | **Alta — ver abajo** |

### El lector no es Compose, y eso hay que decirlo

De los 34 ficheros del visor de manga, **17 son Android Views** — `ReaderPageImageView`,
`WebtoonRecyclerView`, `PagerPageHolder` y compañía. Eso no se comparte migrando nada: hay que
reescribirlo en Compose, y entonces sirve para las dos plataformas. Es la pieza más grande de
toda la migración de interfaz, y la que más cuidado pide, porque el lector es lo más usado de
Mihon y la regla 1 del charter dice que no se degrada.

### El player, igual

`ZenyomiMPVView` es un `SurfaceView`. libmpv es idéntico en escritorio, pero empotrar vídeo
dentro de una ventana de Compose Desktop es un punto áspero conocido: el camino probado es
entregarle a mpv el identificador de la ventana. Todo el trabajo de resolución de vídeo,
cabeceras, pistas, subtítulos y preferencias de idioma —que es la mayor parte del esfuerzo del
player— es lógica y se comparte tal cual.

---

## 6. Las etapas

Cada etapa termina con Android verde y publicable. Solo la última produce algo que abras en tu
PC, que es lo que pediste.

### Etapa 0 — La prueba de las extensiones · días

Coger el APK de una extensión de anime que funciona hoy (KickAssAnime o AnimeOnsen), montarlo
sobre la capa de compatibilidad de Suwayomi en un proyecto Kotlin pelado, y pedirle que resuelva
la URL de vídeo de un episodio. Sin interfaz y sin base de datos: un `main()` que imprima una
URL.

Va primero porque es la única incógnita que puede cambiar el tamaño del proyecto por un orden de
magnitud, y porque cuesta días. No es un go/no-go —has decidido invertir lo que haga falta— sino
una medición: dice cuánto hay que invertir.

**Nota de licencia.** La capa `AndroidCompat` es Apache-2.0, igual que nosotros. El resto de
Suwayomi es MPL-2.0, copyleft por fichero: se puede depender de él, pero cualquier fichero que
copiemos sigue siendo MPL y se anota en `FORK_COMPLIANCE.md`. Ver `docs/adr/0002-licencia.md`.

### Etapa 1 — El núcleo multiplataforma

Añadir el objetivo JVM al convention plugin y mover, en este orden y de menor a mayor
resistencia: `domain` (4 ficheros que tocar), `presentation-core` (2), `source-api` (6),
`data` (7 más el driver de SQLDelight para escritorio).

Al final de la etapa el escritorio no existe todavía, pero la biblioteca, los episodios, el
historial y las fuentes ya compilan fuera de Android.

### Etapa 2 — La costura de plataforma

Declarar las interfaces de la sección 5 y meter detrás lo que hoy hace Android. No cambia
comportamiento: mueve código a su sitio. Es la etapa más aburrida y la que más protege del resto.

### Etapa 3 — La interfaz a Compose Multiplatform

Cambiar los artefactos de Compose de Android a los multiplataforma y arrastrar con ello los 347
ficheros que ya usan Compose. Resolver los 37 ficheros de pantallas y los 78 de la capa de
ViewModels que tocan Android de verdad.

Aquí es donde por primera vez se puede abrir una ventana en Linux y ver la biblioteca. No se te
entrega: es un hito interno.

### Etapa 4 — El lector y el player

El lector de manga reescrito en Compose, para las dos plataformas. El player de escritorio sobre
libmpv. Son las dos piezas grandes y van juntas porque las dos son «lo mismo, con otro
envoltorio».

### Etapa 5 — Las extensiones en el escritorio

La capa de compatibilidad, la conversión de APK, el Chromium embebido para Cloudflare, y las
extensiones de anime: lo que la etapa 0 haya dicho que cuesta.

### Etapa 6 — Empaquetado y primera entrega

Empaquetado para Linux con libmpv y sus dependencias nativas resueltas, y el primer build que
abres en tu PC. Con prueba de humo sobre el binario publicado, como todas las releases: la
disciplina no cambia por ser otra plataforma.

---

## 7. Cómo se mide el avance sin entregarte builds

Elegiste no recibir nada hasta que se pueda usar. Eso es razonable, pero deja meses sin nada
tangible, así que el avance tiene que ser verificable de otra forma y no por confianza:

- **Una tarea de CI que compile el escritorio desde la etapa 1.** Desde el primer día en que el
  objetivo JVM existe, si deja de compilar el CI lo dice. No hay «funciona en mi máquina».
- **`docs/DESKTOP_PARITY.md`**, pantalla a pantalla, con el mismo criterio que
  `MIHON_PARITY.md`: qué corre en las dos plataformas y qué falta. Es la respuesta objetiva a
  «¿por dónde vamos?».
- **El CI de Android es un portón, no un aviso.** Cualquier commit de la migración que ponga en
  rojo el móvil no entra. Eso es lo que protege tu decisión de que el móvil siga evolucionando.

---

## 8. Riesgos, con nombre

**Las extensiones de anime en la JVM.** Nadie lo ha hecho. Suwayomi resolvió las de manga y la
petición para las de anime lleva años abierta sin que nadie la implemente. Has dicho de invertir
lo que haga falta, y así se hará; lo acoto de la única forma responsable: **la etapa 0 termina
con un número**, y si la etapa 5 supera ese número de forma clara, te lo digo y decides otra vez
con datos nuevos. No es un cheque en blanco escondido en un plan.

**El lector.** Es la pieza más usada de Mihon y hay que reescribirla. La regla 1 del charter no
admite que salga peor. Va con su propia comparación contra el comportamiento actual, en
dispositivo.

**mpv dentro de Compose Desktop.** Punto áspero conocido y sin solución de librería madura. Si
se atasca, el plan de repuesto es una ventana de vídeo separada gestionada por la app, que es
feo pero funciona.

**Los merges de Mihon se encarecen.** Mover módulos a multiplataforma cambia rutas y ficheros de
build, y eso hace más caro absorber `mihon/main` en cada `sync/mihon-<version>`. El ADR-0001
existe precisamente para que eso siguiera siendo barato, y esta decisión lo empeora a propósito.
Es un coste real y permanente, y hay que aceptarlo con los ojos abiertos.

---

## 9. El sync: contemplado, no construido

No se construye ahora, pero hay cuatro cosas que hay que respetar durante toda la migración para
que después no cueste el triple. Son baratas hoy y caras de añadir luego.

1. **No tocar `last_modified_at`, `version` ni `is_syncing`.** Ni las columnas ni sus triggers,
   por mucho que parezcan código muerto. No lo son: están esperando.
2. **Toda tabla nueva que guarde estado del usuario nace con esas tres columnas.** Regla, no
   sugerencia.
3. **Empezar a registrar los borrados ya.** Una tabla de lápidas que hoy no lee nadie. Añadirla
   ahora es trivial; reconstruir a posteriori qué se borró es imposible.
4. **No asumir que `_id` significa nada fuera de esta máquina.** Es un autoincremento local. La
   identidad real de una entrada es el par fuente + URL, y cada vez que el código suponga lo
   contrario hay que anotarlo.

Cuando llegue el momento, la investigación de cómo lo resuelven otros —SyncYomi con clave de
API, el modo local de Logseq con clave precompartida y QR, Anki con el servidor como buzón y
nunca como fuente de verdad— está en el historial de este documento, y las reglas de fusión ya
pensadas son: el progreso lo gana el más avanzado (no el más reciente), las opiniones del usuario
las gana el cambio más reciente, y un borrado gana si es posterior al último cambio.

---

## 10. Lo siguiente

1. Escribir el ADR con la decisión de la sección 2, que es la que cambia la arquitectura del
   proyecto y tiene que quedar razonada en `docs/adr/`.
2. Etapa 0.

---

## Fuentes

- [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) — Tachiyomi para escritorio, y su capa de compatibilidad con Android
- [Arquitectura de Suwayomi-Server](https://deepwiki.com/Suwayomi/Suwayomi-Server/2-architecture) — cómo convierte y ejecuta los APK
- [Petición de extensiones de Aniyomi](https://github.com/Suwayomi/Tachidesk-Server/issues/387) — abierta, sin implementar
- [Mihon #167](https://github.com/mihonapp/mihon/issues/167) — port a escritorio, cerrado como *not planned*
- [libmpv desde Java](https://github.com/mpv-player/mpv-examples/tree/master/libmpv/java) — ejemplo oficial de mpv en la JVM
- [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/) — el objetivo de escritorio
- Para cuando toque el sync: [SyncYomi](https://github.com/syncyomi/syncyomi), [Kotatsu sync server](https://github.com/KotatsuApp/kotatsu-syncserver), [modo local de Logseq](https://github.com/logseq/logseq/pull/12919)
