# Estado del proyecto

**Actualizado:** 2026-09-08
**Fase actual:** 1 — Cimientos de anime (fase 0 cerrada, v0.1.2 publicada)
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
| BD anime | ✅ | 10 tablas + 8 vistas, `AnimeDatabase` genera y compila |
| Dominio anime | ⏳ | siguiente paso de la fase 1 |
| Extensiones de anime | ⬜ | fase 1 |
| Player | ⬜ | fase 2 |
| Descargas / historial / tracker anime | ⬜ | fase 3 |

Leyenda: ✅ hecho · ⏳ en curso · 🔴 bloqueado · ⬜ no empezado

## Bloqueos activos

Ninguno.

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

Capa de dominio de anime: `domain/anime` y `domain/episode`, portados desde
`entries/anime` e `items/episode` de Aniyomi. Después la capa `data` que los
conecta con la base ya creada.

## Incidencias resueltas

- **2026-09-08 — tags heredados.** Los 159 tags de Mihon/Aniyomi (v0.1.0 … v0.20.4)
  colisionaban con nuestra numeración: al tagear `v0.1.0` git rechazó el tag por
  existir ya, y el push publicó el tag ajeno de 2016 en su lugar. Se borraron los
  tags heredados y se fijó `tagOpt = --no-tags` en ambos remotes. Sin pérdida de
  historia (7971 commits en `main`).
