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

## Mantenimiento continuo

Sincronización periódica con `mihon/main` en ramas `sync/mihon-<version>`.
La arquitectura de árbol paralelo (ADR-0001) existe precisamente para que esto
siga siendo barato.
