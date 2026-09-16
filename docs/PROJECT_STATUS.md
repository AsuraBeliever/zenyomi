# Estado del proyecto

**Actualizado:** 2026-09-16
**Fase actual:** 4 — Pulido hacia la v1.0.0 (fases 0 a 3 cerradas)
**Última release:** v0.14.3, tag en `main`
**¿Compila?** sí
**¿Instalado en el dispositivo del cliente?** sí — la línea 0.5.x se prueba en el Galaxy S25 Ultra

## Semáforo por área

| Área | Estado | Nota |
|---|---|---|
| Repo y ramas | ✅ | `main` / `develop` desde `mihon/main` (v0.20.4) |
| Documentación base | ✅ | charter, arquitectura, roadmap, 3 ADRs |
| Repo en GitHub | ✅ | github.com/AsuraBeliever/zenyomi, público, default `develop` |
| CI | ✅ | workflow de Mihon adaptado; corre en `main` y `develop` |
| Build de línea base | ✅ | `:app:assembleDebug` verde, APKs por ABI generados |
| Rebranding a Zenyomi | ✅ | app.zenyomi, v0.1.0, icono e identidad propios |
| Wireless debugging | ✅ | Galaxy S25 Ultra emparejado, reconecta por mDNS |
| Fase 0 | ✅ | tag `v0.1.0`, APK instalado y abierto sin crashes |
| Releases 0.5.x – 0.14.3 | ✅ | hasta `v0.14.3`; cada una con prueba de humo (arranque, extensiones, reproducción, PiP) |
| Prueba de humo sobre el APK **publicado** | ✅ | desde la 0.9.0 no basta el APK local: se descarga el del CI y se comprueba firma, identidad, marca y reproducción real. Última: v0.14.2, 2026-09-15 — esta vez contra una **fuente HTTP real** (AnimeOnsen), no la local, porque el cambio era reflexión y R8 |
| Fase 3 completa | ✅ | entregada y verificada, trackers incluidos |
| Trackers de anime (código) | ✅ | MyAnimeList, AniList y Kitsu |
| PiP del player | ✅ | Activity propia |
| BD anime | ✅ | 10 tablas + 8 vistas, conectada al grafo de dependencias |
| Dominio anime | ✅ | 33 ficheros: modelos, repositorios e interactors; compila y corre |
| Capa data de anime | ✅ | mapper + 3 repositorios, reescritos al estilo de Mihon |
| `source-api` de anime | ✅ | 26 ficheros: `SAnime`, `SEpisode`, `Video`, `Hoster`, `AnimeHttpSource`… |
| Dominio de fuentes de anime | ✅ | modelos, repositorios, `AnimeSourceManager` |
| Motor de extensiones de anime | ✅ | derivado del de Mihon; carga APK con `tachiyomi.animeextension` |
| `AndroidAnimeSourceManager` | ✅ | conecta extensiones cargadas con fuentes usables |
| Biblioteca de anime (UI) | ✅ | pestaña propia; búsqueda, orden (título / visto / sin ver) e insignia de episodios pendientes |
| Categorías de anime | ✅ | crear, renombrar, reordenar y borrar; pestañas en la biblioteca; asignar desde la ficha; viajan en el backup |
| Filtros, orden y vista de la biblioteca de anime | ✅ | hoja con tres pestañas; 4 filtros de tres estados, 6 órdenes con sentido, 3 modos de vista; todo se recuerda |
| Explorar (fuentes + extensiones) | ✅ | 3 pestañas: Sources, Extensions, Migrate. Manga y anime conviven en cada lista, con filtro y secciones plegables |
| Búsqueda y filtro de idioma en extensiones de anime | ✅ | agrupado por idioma como en el manga; marca las abandonadas |
| Catálogo de una fuente | ✅ | rejilla paginada, con búsqueda dentro de la fuente |
| Búsqueda global de anime | ✅ | una consulta a todas las fuentes a la vez, agrupada por fuente, con el estado de cada una |
| Fijar y ocultar fuentes de anime | ✅ | fijadas arriba, ocultas fuera de la lista y de la búsqueda global |
| Migrar un anime entre fuentes | ✅ | reutiliza la búsqueda global; traslada visto, categorías y tracking |
| Novedades e Historial de anime en la barra inferior | ✅ | las pestañas de Mihon intercambian entre manga y anime |
| Baseline profile | ✅ | el generador cubre ahora las pantallas de anime; 13 → 1418 reglas de anime |
| Ajustes de fuente de anime | ✅ | aloja el `setupPreferenceScreen()` de la propia extensión |
| Ficha de anime y episodios | ✅ | mismo diseño que la de manga: fondo difuminado, autor y estado, fila de acciones, sinopsis con géneros, filtro/orden, descarga por lotes y botón de continuar |
| Paridad manga ↔ anime | ✅ | auditada punto por punto en `ANIME_PARITY_GAPS.md` y cerrada en la v0.14.0: selección de episodios y de biblioteca, notas, intervalo, portada, deslizamiento, dos paneles. Se reutilizan los componentes de Mihon; la copia `AnimeToolbar` desapareció |
| Tienda de extensiones de anime | ✅ | añadir repos e instalar desde la app; verificado con el índice oficial de Aniyomi |
| Errores de fuente legibles | ✅ | `AnimeSourceError`: nunca se enseña la excepción cruda; el mensaje propio de una extensión sí |
| Estado medido de las extensiones | ✅ | arnés en debug + `anime-source-health.json`; ver `docs/EXTENSIONS_STATUS.md` |
| Dependencias nativas del player | ✅ | mpv, FFmpeg, seeker y mediasession resuelven, empaquetan y no rompen el arranque |
| Reproductor (núcleo) | ✅ | mpv decodifica y pinta; play/pausa y barra de búsqueda verificados |
| Pistas de audio y subtítulos | ✅ | selector propio; verificado con un vídeo de 2 audios y 2 subtítulos |
| Gestos del player | ⚠️ | toque simple (pausa) verificado; el doble toque sigue sin poder dispararse por adb, ver TESTING.md |
| Controles del player | ✅ | play/pausa y saltos en el centro de la imagen, reloj de un segundo, barra que se queda donde se suelta y cubierta de carga con salida. Se ocultan a los 5 s y el toque en la imagen los muestra u oculta en vez de pausar |
| Arranque de un episodio | ✅ | audio y controles ya no esperan a que se descarguen dieciséis idiomas de subtítulos: de 37 s de vídeo mudo y medio minuto de botones muertos, a 2 s |
| PiP del player | ✅ | el player pasa a Activity propia; verificado: la miniatura pinta vídeo, sigue reproduciendo y restaura a pantalla completa |
| Resolución de vídeo desde la fuente | ✅ | verificada de punta a punta con la fuente local **y con extensiones HTTP reales** |
| Reproducción de una extensión online | ✅ | KickAssAnime, AnimeOnsen y TioAnime se ven **y se oyen** en el emulador; ver `EXTENSIONS_STATUS.md` |
| Cabeceras HTTP hasta mpv | ✅ | se resolvían bien y se perdían camino del player; corregido y comprobado contra un servidor que las imprime |
| Subtítulos y audio externos | ✅ | las pistas que la fuente entrega aparte se añaden a mpv y se seleccionan |
| Idiomas preferidos | ✅ | la preferencia no casaba con las etiquetas de la fuente y no elegía nada; verificado: audio inglés + sub español solos |
| Controles del player sin bloquear | ✅ | arrastrar la barra congelaba la app; ninguna llamada a libmpv queda ya en el hilo principal |
| Foco de audio | ✅ | sin él Android 16 silencia la app entera; verificado en `dumpsys audio` (la app aparece en la pila de foco y no hay evento de *AudioHardening*) |
| Series largas sin bloquear la app | ✅ | One Piece pasó de 440 fotogramas con 96% fuera de plazo y un GC cada 0,7 s a 37 fotogramas, 6 fuera de plazo y ningún GC |
| Buffer de streams con audio aparte | ✅ | 30 s de lectura anticipada y sin *keepalive* imposible; 0 *underruns* frente a los continuos de antes |
| Fallo de reproducción visible | ✅ | un mirror muerto dice por qué en vez de dejar la pantalla en negro |
| Progreso de reproducción | ✅ | verificado en la BD: `seen=1`, `last_second_seen=9`, `total_seconds=10` |
| Fuente local de anime | ✅ | reproduce vídeos de la carpeta `localanime` |
| Sincronización de episodios | ✅ | la ficha pide los episodios a la fuente y los guarda |
| Historial de anime | ✅ | se registra al reproducir; pantalla propia desde la biblioteca |
| Novedades de anime | ✅ | pantalla propia desde la biblioteca: episodios nuevos por día, reproducir, marcar visto y descargar |
| Añadir a biblioteca | ✅ | botón de favorito en la ficha, con fecha de alta |
| Descargas de anime | ⚠️ | cola persistente, worker en primer plano, notificaciones y borrado; falta verificar una descarga HTTP real (ver abajo) |
| Actualizaciones de biblioteca de anime | ✅ | job periódico propio; verificado: programa a 12 h, notifica episodios nuevos y errores |
| Trackers de anime | ✅ | MyAnimeList, AniList y **Kitsu** verificados de punta a punta con cuenta real (2026-09-15): sesión, búsqueda, vinculación, progreso, puntuación, privado y desvincular, comprobado en los servidores de cada servicio. En Kitsu además se comprobó que la entrada cae en la biblioteca de anime y no en la de manga |
| Ajustes del player | ✅ | salto, umbral de visto, velocidad, idiomas preferidos, pantalla completa |
| Aspecto de los subtítulos | ✅ | paridad con Aniyomi: fuente, tamaño, colores, borde, sombra, posición, retardo y velocidad, en el reproductor y en Ajustes |
| Estadísticas de anime | ✅ | contadores verificados uno a uno contra la BD |
| Backup de anime | ✅ | mismo fichero .tachibk que el manga; verificado backup → borrado → restauración |
| "All read entries" para anime | ✅ | un anime visto y fuera de la biblioteca entra en el backup y vuelve **sin** entrar en la biblioteca |

Leyenda: ✅ hecho · ⏳ en curso · 🔴 bloqueado · ⬜ no empezado

## Bloqueos activos

Ninguno.

## Pendiente menor

- **Las pantallas de OAuth dicen «Mihon».** Al conceder acceso, MyAnimeList y AniList muestran
  el nombre y la web de Mihon, porque los identificadores de cliente son los suyos, heredados
  del fork. Funciona, pero es el nombre de otro proyecto. Arreglarlo pide registrar apps
  propias en ambos servicios; decisión del cliente.

- **El contador de extensiones de anime no cuenta las no confiadas.** En una instalación
  limpia la cabecera dice «Anime extensions 0» mientras debajo hay veintiuna esperando
  a que se confíen; la de manga sí cuenta la suya. Solo es el número: la lista y el
  botón *Trust* funcionan. Mihon es la referencia, así que el número debería incluirlas.
  Visto al verificar la v0.11.0.

## Aviso: v0.3.0 y v0.4.0 tienen el player roto

En ambas releases publicadas R8 eliminó los métodos que `libmpv` llama por JNI, así que
reproducir cualquier vídeo mata la app. **Verificado** desempaquetando los APK publicados:
cero referencias a `eventProperty` en sus `classes*.dex`. Corregido en la v0.5.1; quien tenga
una de esas dos versiones necesita actualizar.

## Sin verificar en dispositivo

Lo que necesita una persona con el dispositivo delante, o una cuenta que aquí no hay, está
en [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md): el doble toque del player, vincular un tracker real
y una descarga HTTP completada.


## Aviso conocido: alineación de 16 KB

En el emulador (Pixel 10 Pro XL, página de 16 KB) Android muestra un diálogo de
compatibilidad: varias librerías nativas no están alineadas a 16 KB y la app corre en
modo compatible. **No es algo que hayamos introducido**: en la lista aparecen también
librerías propias de Mihon (`libconscrypt_jni`, `libsqliteJni`, `libquickjs`,
`libimagedecoder2`, `libwebgpu_c_bundled`), junto a las nuevas de mpv y FFmpeg.

Hoy solo es un aviso y la app funciona. A futuro conviene vigilarlo, porque Google Play
acabará exigiendo alineación de 16 KB. La parte que depende de nosotros son mpv y
FFmpeg-kit; el resto se arregla siguiendo a upstream.

## Hechos medidos (2026-09-08)

| | Mihon | Aniyomi |
|---|---|---|
| Último commit en `main` | 2026-09-08 | 2026-09-05 |
| Última release | v0.20.4, 2026-08-05 | v0.18.1.2, **2025-10-28** |
| Commits desde el ancestro común | 1557 | 1726 |

Ancestro común: Tachiyomi v0.15.2, `c6601c1f`, 2024-01-08.
Archivos de Aniyomi que tocan anime/player/episodio/torrent: 553 de 2039.

## Decisiones tomadas

Del cliente:
- Base técnica Mihon, objetivo funcional Aniyomi
- Bibliotecas de anime y manga en pestañas separadas
- Motor de extensiones: el de Mihon
- No se pierde ninguna feature de Mihon

Técnicas:
- ADR-0001 — árbol paralelo de anime, no se adopta `entries`/`items`
- ADR-0002 — Apache-2.0, no MIT (obligación legal)
- ADR-0003 — portar desde `aniyomi/main`, con v0.18.1.2 como contraste
- ADR-0004 — la BD de anime nace en la versión 1, sin las 26 migraciones de Aniyomi

## Siguiente paso

Fase 4. Lo que queda, por orden:

| Pieza | Estado |
|---|---|
| Afinado de R8 | pendiente; el baseline profile ya cubre el anime |
| Alineación de librerías nativas a 16 KB | vigilando; hoy solo es un aviso |
| Streaming por torrent (torrserver) | opcional, decisión del cliente |

La interfaz de anime está completa y en uso: pestañas Anime/Manga, biblioteca,
Explorar con fuentes y extensiones, ficha con episodios, ajustes, player, historial,
estadísticas y backup. El módulo `i18n-anime` existe y está poblado.

## Incidencias resueltas

- **2026-09-14 — la v0.11.1 pasó el CI y el `Release` a la primera.** Sin rastro del
  fallo de JitPack de la tanda anterior, que era suyo y no nuestro.

- **2026-09-14 — JitPack tumbó el job FOSS de la v0.11.0.** Tercera vez, misma firma:
  `Could not find flexible-adapter:c8013533`. Se comprobó que JitPack servía el `.pom`
  (con `packaging aar`) y el `.aar` antes de tocar nada; relanzar el job bastó. El
  procedimiento de `docs/RELEASING.md` lo cubría tal cual.

- **2026-09-08 — tags heredados.** Los 159 tags de Mihon/Aniyomi (v0.1.0 … v0.20.4)
  colisionaban con nuestra numeración: al tagear `v0.1.0` git rechazó el tag por
  existir ya, y el push publicó el tag ajeno de 2016 en su lugar. Se borraron los
  tags heredados y se fijó `tagOpt = --no-tags` en ambos remotes. Sin pérdida de
  historia (7971 commits en `main`).
