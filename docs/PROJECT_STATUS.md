# Estado del proyecto

**Actualizado:** 2026-09-08
**Fase actual:** 0 — Fundaciones
**Versión objetivo inmediata:** v0.1.0
**¿Compila?** sí — línea base de Mihon verde en 5m 42s
**¿Instalado en el dispositivo del cliente?** no — bloqueado por emparejamiento

## Semáforo por área

| Área | Estado | Nota |
|---|---|---|
| Repo y ramas | ✅ | `main` / `develop` desde `mihon/main` (v0.20.4) |
| Documentación base | ✅ | charter, arquitectura, roadmap, 3 ADRs |
| Repo en GitHub | ✅ | github.com/AsuraBeliever/zenyomi, público, default `develop` |
| CI | ✅ | workflow de Mihon adaptado; corre en `main` y `develop` |
| Build de línea base | ✅ | `:app:assembleDebug` verde, APKs por ABI generados |
| Rebranding a Zenyomi | ⏳ | commiteado, verificación de build en curso |
| Wireless debugging | 🔴 | **bloqueado: faltan datos de emparejamiento del cliente** |
| Dominio anime | ⬜ | fase 1 |
| BD anime | ⬜ | fase 1 |
| Extensiones de anime | ⬜ | fase 1 |
| Player | ⬜ | fase 2 |
| Descargas / historial / tracker anime | ⬜ | fase 3 |

Leyenda: ✅ hecho · ⏳ en curso · 🔴 bloqueado · ⬜ no empezado

## Bloqueos activos

1. **Wireless debugging sin emparejar.** No hay dispositivo conectado y no existía
   memoria de sesiones anteriores. Hace falta que el cliente abra *Depuración
   inalámbrica* y pase el código de vinculación. Procedimiento en `docs/TESTING.md`.
   Impide cerrar la fase 0: sin dispositivo no se puede verificar el APK.

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

## Siguiente paso

Con el dispositivo emparejado: `./gradlew :app:installDebug`, humo de regresión de
manga (checklist en `docs/TESTING.md`), tag `v0.1.0` y cierre de la fase 0.
Después arranca la fase 1 por la base de datos de anime.
