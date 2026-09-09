# Estado del proyecto

**Actualizado:** 2026-09-08
**Fase actual:** 0 — Fundaciones
**Versión objetivo inmediata:** v0.1.0
**¿Compila?** todavía no verificado
**¿Instalado en el dispositivo del cliente?** no

## Semáforo por área

| Área | Estado | Nota |
|---|---|---|
| Repo y ramas | ✅ | `main` creada desde `mihon/main` (0.20.4) |
| Documentación base | ✅ | charter + arquitectura + roadmap |
| Repo en GitHub | ⏳ | pendiente de crear |
| Build verde local | ⏳ | primera compilación pendiente |
| Wireless debugging | 🔴 | **bloqueado: faltan datos de emparejamiento del cliente** |
| Rebranding a Zenyomi | ⏳ | |
| Dominio anime | ⬜ | |
| BD anime | ⬜ | |
| Extensiones de anime | ⬜ | |
| Player | ⬜ | |
| Descargas / historial / tracker anime | ⬜ | |

Leyenda: ✅ hecho · ⏳ en curso · 🔴 bloqueado · ⬜ no empezado

## Bloqueos activos

1. **Wireless debugging.** No hay ningún dispositivo emparejado y mi memoria de
   sesiones anteriores está vacía. Necesito del cliente los datos de emparejamiento.
   Ver `docs/TESTING.md`.

## Decisiones tomadas

- Base técnica: Mihon. Objetivo funcional: Aniyomi. (cliente)
- Bibliotecas de anime y manga en pestañas separadas. (cliente)
- Motor de extensiones: el de Mihon. (cliente)
- No se pierde ninguna feature de Mihon. (cliente)
- Licencia Apache-2.0, no MIT — obligación legal, ver ADR-0002. (técnica)
- No se adopta la abstracción `entries`/`items` de Aniyomi, ver ADR-0001. (técnica)

## Siguiente paso

Compilar Mihon sin modificar para tener una línea base verde.
