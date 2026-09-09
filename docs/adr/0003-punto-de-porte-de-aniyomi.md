# ADR-0003 — Portamos desde `aniyomi/main`, con la última release como red de seguridad

**Fecha:** 2026-09-08 · **Estado:** aceptada

## Contexto

Aniyomi lleva más de diez meses sin publicar (v0.18.1.2, 2025-10-28) pero su rama
`main` siguió recibiendo trabajo hasta 2026-09-05. Hay entonces dos puntos posibles
de los que portar:

- **`v0.18.1.2`** — código que sabemos que se ejecutó en manos de usuarios reales.
- **`aniyomi/main`** — diez meses más de correcciones y features (streaming por
  torrent, `Hoster`/`Video` con `memo`, entradas relacionadas), pero nunca publicado
  y por tanto no validado en producción.

## Decisión

Se porta desde **`aniyomi/main`** como referencia principal. `v0.18.1.2` se conserva
como referencia de contraste: cuando algo portado falle, se compara contra el tag
para saber si el fallo venía del código no publicado o lo hemos introducido nosotros.

Excepción: **no** se porta su tienda de extensiones ni extension-lib 17. Esa capa la
pone Mihon, por decisión del cliente y porque es precisamente donde Aniyomi falla.

## Motivo

El riesgo de coger código no publicado es mucho menor de lo que parece, porque nada
de lo portado entra tal cual: todo se reescribe sobre la estructura de Mihon
(ADR-0001) y se prueba en dispositivo fase por fase. A cambio se evita empezar ya con
diez meses de deuda.

## Consecuencias

- `docs/PORTING_LOG.md` registra el SHA de origen de cada porte, así que siempre se
  puede saber si una pieza vino de código publicado o no.
- Si un área concreta de `main` resulta ser inestable, se puede retroceder esa área
  al tag sin afectar al resto.
