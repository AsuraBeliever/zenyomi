# ADR-0004 — La base de datos de anime arranca en la versión 1, sin las migraciones de Aniyomi

**Fecha:** 2026-09-08 · **Estado:** aceptada

## Contexto

Aniyomi mantiene el anime en una base sqldelight independiente
(`data/src/main/sqldelightanime/`) con 10 tablas, 8 vistas y **26 migraciones
numeradas 113 a 138**.

Al portarla había que decidir qué hacer con esas migraciones. En sqldelight los
ficheros `.sq` describen el esquema **actual** y los `.sqm` son los saltos entre
versiones anteriores.

## Decisión

Se portan las tablas y las vistas. **No se portan las migraciones.** La base de anime
de Zenyomi nace en la versión 1 con el esquema final.

## Motivo

Esas 26 migraciones describen el camino entre estados por los que **la base de datos
de Zenyomi no ha pasado nunca**: ningún dispositivo tiene una base de anime de este
proyecto, porque hasta hoy no existía. Aplicar migraciones a una base que se crea ya
en su forma final no aporta nada y obliga a mantener para siempre 26 ficheros de
historia ajena.

Las migraciones tampoco hacen falta para importar backups de Aniyomi: los backups son
protobuf, no volcados de base de datos, así que lo que importa es que el esquema
coincida, no cómo se llegó a él.

## Consecuencias

- `verifySqlDelightMigration` pasa de forma trivial mientras no haya migraciones.
- A partir de la primera release que incluya la base de anime, **cada cambio de esquema
  necesita su propia migración**, numerada desde 1 en `sqldelightanime/migrations/`.
  Saltarse esto rompería la base de los usuarios.
- Si algún día se quiere leer una base de anime de Aniyomi directamente (no vía
  backup), habría que escribir una importación explícita. No es un objetivo hoy.
