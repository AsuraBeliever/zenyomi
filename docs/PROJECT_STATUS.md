# Estado del proyecto

**Actualizado:** 2026-09-12
**Fase actual:** 4 — Pulido hacia la v1.0.0 (fases 0 a 3 cerradas)
**Última release:** v0.6.0, tag en `main`
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
| Releases 0.5.x / 0.6.x | ✅ | hasta `v0.6.0`; cada una con prueba de humo (arranque, extensiones, reproducción, PiP) |
| Fase 3 completa | ✅ | entregada; queda pendiente verificar los trackers con una cuenta real |
| Trackers de anime (código) | ✅ | MyAnimeList y AniList |
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
| Explorar anime (fuentes + extensiones) | ✅ | Explorar aloja las 5 pestañas: Sources, Manga extensions, Anime sources, Anime extensions, Migrate |
| Búsqueda y filtro de idioma en extensiones de anime | ✅ | agrupado por idioma como en el manga; marca las abandonadas |
| Catálogo de una fuente | ✅ | rejilla paginada, con búsqueda dentro de la fuente |
| Búsqueda global de anime | ✅ | una consulta a todas las fuentes a la vez, agrupada por fuente, con el estado de cada una |
| Fijar y ocultar fuentes de anime | ✅ | fijadas arriba, ocultas fuera de la lista y de la búsqueda global |
| Migrar un anime entre fuentes | ✅ | reutiliza la búsqueda global; traslada visto, categorías y tracking |
| Novedades e Historial de anime en la barra inferior | ✅ | las pestañas de Mihon intercambian entre manga y anime |
| Baseline profile | ✅ | el generador cubre ahora las pantallas de anime; 13 → 1418 reglas de anime |
| Ajustes de fuente de anime | ✅ | aloja el `setupPreferenceScreen()` de la propia extensión |
| Ficha de anime y episodios | ✅ | verificada con un fixture insertado en la BD del emulador |
| Tienda de extensiones de anime | ✅ | añadir repos e instalar desde la app; verificado con el índice oficial de Aniyomi |
| Errores de fuente legibles | ✅ | `AnimeSourceError`: nunca se enseña la excepción cruda; el mensaje propio de una extensión sí |
| Estado medido de las extensiones | ✅ | arnés en debug + `anime-source-health.json`; ver `docs/EXTENSIONS_STATUS.md` |
| Dependencias nativas del player | ✅ | mpv, FFmpeg, seeker y mediasession resuelven, empaquetan y no rompen el arranque |
| Reproductor (núcleo) | ✅ | mpv decodifica y pinta; play/pausa y barra de búsqueda verificados |
| Pistas de audio y subtítulos | ✅ | selector propio; verificado con un vídeo de 2 audios y 2 subtítulos |
| Gestos del player | ⚠️ | toque simple (pausa) verificado; el doble toque sigue sin poder dispararse por adb, ver TESTING.md |
| PiP del player | ✅ | el player pasa a Activity propia; verificado: la miniatura pinta vídeo, sigue reproduciendo y restaura a pantalla completa |
| Resolución de vídeo desde la fuente | ✅ | verificada de punta a punta con la fuente local |
| Progreso de reproducción | ✅ | verificado en la BD: `seen=1`, `last_second_seen=9`, `total_seconds=10` |
| Fuente local de anime | ✅ | reproduce vídeos de la carpeta `localanime` |
| Sincronización de episodios | ✅ | la ficha pide los episodios a la fuente y los guarda |
| Historial de anime | ✅ | se registra al reproducir; pantalla propia desde la biblioteca |
| Novedades de anime | ✅ | pantalla propia desde la biblioteca: episodios nuevos por día, reproducir, marcar visto y descargar |
| Añadir a biblioteca | ✅ | botón de favorito en la ficha, con fecha de alta |
| Descargas de anime | ⚠️ | cola persistente, worker en primer plano, notificaciones y borrado; falta verificar una descarga HTTP real (ver abajo) |
| Actualizaciones de biblioteca de anime | ✅ | job periódico propio; verificado: programa a 12 h, notifica episodios nuevos y errores |
| Trackers de anime | ⚠️ | MyAnimeList y AniList: buscar, vincular, desvincular y empujar progreso; sin verificar con cuenta real (ver abajo) |
| Ajustes del player | ✅ | salto, umbral de visto, velocidad, idiomas preferidos, pantalla completa |
| Estadísticas de anime | ✅ | contadores verificados uno a uno contra la BD |
| Backup de anime | ✅ | mismo fichero .tachibk que el manga; verificado backup → borrado → restauración |
| "All read entries" para anime | ✅ | un anime visto y fuera de la biblioteca entra en el backup y vuelve **sin** entrar en la biblioteca |

Leyenda: ✅ hecho · ⏳ en curso · 🔴 bloqueado · ⬜ no empezado

## Bloqueos activos

Ninguno.

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

- **2026-09-08 — tags heredados.** Los 159 tags de Mihon/Aniyomi (v0.1.0 … v0.20.4)
  colisionaban con nuestra numeración: al tagear `v0.1.0` git rechazó el tag por
  existir ya, y el push publicó el tag ajeno de 2016 en su lugar. Se borraron los
  tags heredados y se fijó `tagOpt = --no-tags` en ambos remotes. Sin pérdida de
  historia (7971 commits en `main`).
