# ADR-0002 — La licencia es Apache-2.0, no MIT

**Fecha:** 2026-09-08 · **Estado:** aceptada (obligación legal)

## Contexto

El cliente pidió licenciar Zenyomi bajo MIT. Verificado en los repos:

- `mihonapp/mihon` → Apache License 2.0
- `aniyomiorg/aniyomi` → Apache License 2.0

Zenyomi es una obra derivada de ambos.

## Decisión

Zenyomi se distribuye bajo **Apache License 2.0**.

## Motivo

Apache-2.0 permite redistribuir y modificar, pero exige conservar los avisos de
copyright y licencia y declarar los cambios (secciones 4a–4d). No otorga permiso
para relicenciar el código derivado bajo otros términos. Publicar como MIT
presentaría el código de Mihon y Aniyomi bajo una licencia que sus autores no
concedieron, y omitiría condiciones que MIT no contiene (por ejemplo la concesión
y revocación de patentes de la sección 3).

Mantener Apache-2.0 no limita nada de lo que el proyecto quiere hacer: se puede
publicar, distribuir APKs y aceptar contribuciones igual.

## Implementación

- Se conserva el `LICENSE` de Mihon íntegro.
- `NOTICE` con la atribución a Mihon y Aniyomi.
- El `README` declara el doble origen y los cambios respecto a upstream.
- Los archivos portados desde Aniyomi mantienen sus cabeceras de copyright y quedan
  registrados en `docs/PORTING_LOG.md`.
