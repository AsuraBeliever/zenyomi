# ADR-0001 — Árbol paralelo de anime en vez de la abstracción `entries`/`items`

**Fecha:** 2026-09-08 · **Estado:** aceptada

## Contexto

Aniyomi generalizó el dominio heredado de Tachiyomi para que sirviera a anime y
manga simultáneamente: `domain/manga` y `domain/chapter` pasaron a ser
`domain/entries/{anime,manga}` y `domain/items/{episode,chapter}`.

Ese refactor atraviesa todas las capas. Es la causa directa de que Aniyomi no pueda
absorber los cambios de Mihon: cada commit de Mihon que toca `manga` o `chapter`
choca contra la abstracción. Su propia rama `m_mihon`, el intento de rebasar
Aniyomi sobre Mihon, quedó abandonada en 2025-10-12.

A 2026-09-08 los forks divergen 1557 (Mihon) / 1726 (Aniyomi) commits desde el
ancestro común de 2024-01-08.

## Decisión

Zenyomi **no** adopta `entries`/`items`. El código de manga de Mihon se conserva
literalmente en `domain/manga` y `domain/chapter`, y el anime entra como árbol
paralelo en `domain/anime` y `domain/episode`.

## Consecuencias

**A favor**
- Los merges desde `mihon/main` siguen siendo casi limpios indefinidamente. Es la
  propiedad que hace sostenible el proyecto a largo plazo.
- Se cumple por construcción la regla de no perder ninguna feature de Mihon: el
  código de manga no se toca.
- El lado anime se puede portar de forma incremental, sin romper la app en el camino.
- Encaja con la BD: Aniyomi ya mantiene el anime en una base sqldelight separada,
  así que la persistencia es puramente aditiva.

**En contra**
- Duplicación real entre el lado manga y el lado anime (casos de uso, pantallas,
  descargas, historial). Se acepta conscientemente: es el precio de poder seguir
  a upstream.
- Al portar código de Aniyomi que asume `entries`/`items` hay que reescribir imports
  y firmas. Es trabajo mecánico, no de diseño.

**Regla derivada:** cuando Aniyomi modificó código *compartido* de manga, no se
porta ese cambio; se conserva el de Mihon y se adapta únicamente el lado anime.
