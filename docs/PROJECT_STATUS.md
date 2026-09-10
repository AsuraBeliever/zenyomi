# Estado del proyecto

**Actualizado:** 2026-09-08
**Fase actual:** 3 — Paridad de funciones (fases 1 y 2 funcionalmente cerradas)
**Versión objetivo inmediata:** v0.1.0
**¿Compila?** sí — línea base de Mihon verde en 5m 42s
**¿Instalado en el dispositivo del cliente?** sí — v0.1.0 verificada en Galaxy S25 Ultra

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
| BD anime | ✅ | 10 tablas + 8 vistas, conectada al grafo de dependencias |
| Dominio anime | ✅ | 33 ficheros: modelos, repositorios e interactors; compila y corre |
| Capa data de anime | ✅ | mapper + 3 repositorios, reescritos al estilo de Mihon |
| `source-api` de anime | ✅ | 26 ficheros: `SAnime`, `SEpisode`, `Video`, `Hoster`, `AnimeHttpSource`… |
| Dominio de fuentes de anime | ✅ | modelos, repositorios, `AnimeSourceManager` |
| Motor de extensiones de anime | ✅ | derivado del de Mihon; carga APK con `tachiyomi.animeextension` |
| `AndroidAnimeSourceManager` | ✅ | conecta extensiones cargadas con fuentes usables |
| Biblioteca de anime (UI) | ✅ | pestaña propia, lee de la BD; `anime.db` se crea al abrirla |
| Explorar anime (fuentes + extensiones) | ✅ | dos pestañas; verificado: las 3 fuentes de una extensión real llegan a la UI |
| Catálogo de una fuente | ✅ | rejilla paginada; verificado ejecutando el código de una extensión real |
| Ajustes de fuente de anime | ✅ | aloja el `setupPreferenceScreen()` de la propia extensión |
| Ficha de anime y episodios | ✅ | verificada con un fixture insertado en la BD del emulador |
| Extensiones de anime | ⬜ | fase 1 |
| Dependencias nativas del player | ✅ | mpv, FFmpeg, seeker y mediasession resuelven, empaquetan y no rompen el arranque |
| Reproductor (núcleo) | ✅ | mpv decodifica y pinta; play/pausa y barra de búsqueda verificados |
| Pistas de audio y subtítulos | ✅ | selector propio; verificado con un vídeo de 2 audios y 2 subtítulos |
| Gestos y PiP del player | ⬜ | pendiente |
| Resolución de vídeo desde la fuente | ✅ | verificada de punta a punta con la fuente local |
| Progreso de reproducción | ✅ | verificado en la BD: `seen=1`, `last_second_seen=9`, `total_seconds=10` |
| Fuente local de anime | ✅ | reproduce vídeos de la carpeta `localanime` |
| Sincronización de episodios | ✅ | la ficha pide los episodios a la fuente y los guarda |
| Historial de anime | ✅ | se registra al reproducir; pantalla propia desde la biblioteca |
| Añadir a biblioteca | ✅ | botón de favorito en la ficha, con fecha de alta |
| Descargas de anime | ⬜ | fase 3 |
| Actualizaciones de biblioteca de anime | ⬜ | fase 3 |
| Trackers de anime | ⬜ | fase 3 |
| Backup de anime | ⬜ | fase 3 |

Leyenda: ✅ hecho · ⏳ en curso · 🔴 bloqueado · ⬜ no empezado

## Bloqueos activos

Ninguno.

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

## Lo que falta para que se VEA el anime

Todo lo construido está bajo el capó. La app se ve exactamente igual que la v0.1.2
porque **no hay ni una pantalla de anime todavía**. Ese es el grueso restante de la
fase 1:

| Pieza | Tamaño aproximado |
|---|---|
| Navegación con pestañas Anime / Manga | media |
| Biblioteca de anime (pantalla + modelo de vista) | grande |
| Explorar / fuentes de anime | grande |
| Ficha de anime + lista de episodios | grande |
| Ajustes de anime | media |
| Módulo `i18n-anime` con las cadenas | media |

De los 553 ficheros de Aniyomi que tocan anime, unos 200 son de interfaz. Es la mitad
más laboriosa de la fase 1 y no está empezada.

## Siguiente paso

Capa de dominio de anime: `domain/anime` y `domain/episode`, portados desde
`entries/anime` e `items/episode` de Aniyomi, y los repositorios de `data` que los
conectan con la base.

Nota: el fichero `anime.db` sigue sin existir en el dispositivo, y es correcto.
sqldelight lo crea de forma perezosa, en la primera consulta. Como aún no hay ningún
repositorio que la use, el proveedor nunca se invoca. Aparecerá con el primer consumidor
real. Lo que sí está verificado es que el grafo de Metro compila, cosa que fallaría en
tiempo de compilación si el binding estuviera mal.

## Incidencias resueltas

- **2026-09-08 — tags heredados.** Los 159 tags de Mihon/Aniyomi (v0.1.0 … v0.20.4)
  colisionaban con nuestra numeración: al tagear `v0.1.0` git rechazó el tag por
  existir ya, y el push publicó el tag ajeno de 2016 en su lugar. Se borraron los
  tags heredados y se fijó `tagOpt = --no-tags` en ambos remotes. Sin pérdida de
  historia (7971 commits en `main`).
