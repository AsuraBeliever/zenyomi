---
name: zen-implement
description: Paso 3 de zen-flow. Implementa una tarea de Jira en su rama siguiendo el charter de Zenyomi (Mihon intacto, árbol paralelo de anime, Conventional Commits, spotless). También se usa para aplicar los cambios que pide zen-review.
argument-hint: KAN-n [hallazgos de la revisión]
---

# zen-implement — el código

Tarea y contexto: $ARGUMENTS

Lee la tarea (`getJiraIssue`): el «Hecho cuando» es el contrato.

## Reglas que no se negocian (de `CLAUDE.md`)

1. **No se quita ni se degrada nada de Mihon.** Si el arreglo pasa por tocar código
   compartido de manga, el cambio es mínimo y la regresión de manga se prueba en `zen-test`.
2. **Mihon es la referencia de cómo se hace.** Antes de inventar, mira cómo lo resuelve
   Mihon en el lado manga y replícalo en el lado anime.
3. **El anime es un árbol paralelo** (`anime`/`episode`). Nada de `entries`/`items`.
4. **Si portas código de Aniyomi**, el commit es de tipo `port`, lleva `Ported-from:
   aniyomi@<sha>` y se registra en `docs/PORTING_LOG.md`.
5. **Toda decisión de arquitectura** va en un ADR nuevo en `docs/adr/`.

## Cómo

1. Localiza la causa o el punto de entrada. Si es un bug, primero escribe el test que lo
   reproduce y míralo fallar.
2. Implementa el cambio más pequeño que cumpla el «Hecho cuando». Imita el estilo del
   fichero que tocas: nombres, comentarios, idioma.
3. **Tests de unidad** para toda lógica nueva o corregida, junto a los existentes del módulo
   (`src/test/`). Un cambio solo de UI puede no llevarlos; uno de lógica, siempre.
4. Strings visibles: en `i18n` (o `i18n-anime`), nunca en el código.
5. Si cambia el estado de una feature, actualiza su fila en `docs/PROJECT_STATUS.md` con
   la marca **Sin publicar:**, como en las anteriores.

## Compilar y formatear

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew spotlessApply
./gradlew :app:compileDebugKotlin     # o el módulo tocado
```

## Commits

Conventional Commits en inglés y en imperativo (`CLAUDE.md` §6). Cada commit compila. Pie
con la tarea y la atribución:

```
fix(anime): count untrusted extensions in the anime header

<por qué, no qué>

Refs: KAN-9
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

Varios commits si son pasos lógicos distintos (test + arreglo + docs). Nada de "WIP".
