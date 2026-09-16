# Roadmap

Cada fase termina con un APK instalado en el dispositivo del cliente y una entrada
en `CHANGELOG.md`. No se empieza una fase sin cerrar la anterior.

---

## Fase 0 — Fundaciones · `v0.1.0`

Objetivo: Mihon compilando, renombrado a Zenyomi, instalado y funcionando en el
dispositivo. **Cero anime todavía.** Sirve como línea base contra la que medir que
no perdemos nada.

- [x] Repo git con `mihon` y `aniyomi` como remotes; `main` desde `mihon/main`
- [x] Charter (`CLAUDE.md`), arquitectura, roadmap, ADRs
- [x] Build de línea base verde (Mihon sin modificar)
- [x] Wireless debugging operativo contra el dispositivo del cliente
- [x] Repo publicado en GitHub + CI que compile el debug en cada push
- [x] Rebranding: `applicationId` `app.zenyomi`, nombre, icono, versión 0.1.0
- [x] APK instalado y abierto en el dispositivo

**Entregable:** "Mihon con otro nombre", verificado en tu celular.

---

## Fase 1 — Cimientos de anime · `v0.2.0`

Objetivo: el anime existe en la app aunque todavía no se reproduzca.

- [x] Segunda BD sqldelight `sqldelightanime` portada de Aniyomi
- [x] Dominio `domain/anime` + `domain/episode` (desde `entries/anime`, `items/episode`)
- [x] Capa `data` de anime + repositorios + inyección de dependencias
- [x] `source-api` de anime (`AnimeSource`, `AnimeCatalogueSource`, `Video`, `Hoster`)
- [x] Cargador de extensiones de Mihon extendido para APKs de anime
- [x] Módulo `i18n-anime`
- [x] Navegación: pestañas Anime / Manga separadas

**Entregable:** instalar una extensión de anime, buscar, ver la ficha y la lista de
episodios, añadir a biblioteca. Sin reproducción.

---

## Fase 2 — Reproductor · `v0.3.0`

La fase de mayor riesgo: dependencias nativas y ABIs.

- [x] Integrar mpv-android, FFmpeg-kit, mediasession, seeker, truetypeparser
- [x] Portar la UI del player y sus controles
- [x] Extracción de vídeo desde las fuentes (`Hoster` / `Video`)
- [x] Subtítulos y pistas de audio
- [x] Progreso de reproducción y marcado de episodio visto
- [x] Verificar tamaño de APK y splits por ABI

**Entregable:** ver un episodio de principio a fin en tu celular.

---

## Fase 3 — Paridad de funciones · `v0.4.0`

- [x] Descargas de anime (cola, notificaciones, almacenamiento) — falta verificar una descarga HTTP real
- [x] Historial y "Recientes" de anime
- [x] Actualizaciones de biblioteca de anime (job periódico)
- [x] Trackers de anime — AniList y MAL. Simkl y Bangumi no: Bangumi es solo manga en Mihon y Simkl no existe ahí, así que ninguno sale gratis
- [x] Backup y restauración incluyendo anime
- [x] Ajustes de anime y del player
- [x] Estadísticas de anime

---

## Fase 4 — Pulido y v1.0.0

- [x] Auditoría: **ninguna feature de Mihon perdida** — ver `docs/MIHON_PARITY.md`
- [ ] Streaming por torrent (torrserver) — opcional, se evalúa
- [ ] Rendimiento, baseline profile, R8
- [x] Release firmado, changelog, fastlane

---

## Fase 5 — Fuera del móvil

Zenyomi pasa a ser una aplicación Kotlin Multiplatform con dos objetivos, Android y escritorio
Linux, compartiendo todo hasta la interfaz incluida. Decidido por el cliente el 2026-09-16; el
plan completo, con el inventario, la costura de plataforma y los riesgos, está en
[`DESKTOP_AND_SYNC.md`](DESKTOP_AND_SYNC.md).

- [ ] ADR de la decisión de arquitectura
- [ ] Etapa 0 — ¿corre una extensión de anime en la JVM? Mide lo que cuesta la etapa 5
- [ ] Etapa 1 — núcleo multiplataforma: `domain`, `presentation-core`, `source-api`, `data`
- [ ] Etapa 2 — costura de plataforma: preferencias, ficheros, tareas de fondo, notificaciones
- [ ] Etapa 3 — interfaz a Compose Multiplatform
- [ ] Etapa 4 — lector de manga reescrito en Compose, y player de escritorio sobre libmpv
- [ ] Etapa 5 — extensiones en el escritorio
- [ ] Etapa 6 — empaquetado para Linux y primera entrega

La migración va en `develop`, en sitio, commit a commit: **nunca en una rama larga**, porque la
app de Android sigue evolucionando normal mientras dura y una rama aparte se volvería
irreconciliable. Android verde y publicable en cada commit.

El sync entre dispositivos queda para después, pero la sección 9 del plan fija qué hay que
respetar mientras tanto para no encarecerlo.

---

## Mantenimiento continuo

Sincronización periódica con `mihon/main` en ramas `sync/mihon-<version>`.
La arquitectura de árbol paralelo (ADR-0001) existe precisamente para que esto
siga siendo barato.
