# 0005 — Reutilizar los componentes de Mihon en las pantallas de anime

**Fecha:** 2026-09-14
**Estado:** aceptado

## Contexto

El charter dice dos cosas que aquí entran en tensión:

- **Regla 2.** Mihon es la referencia de *cómo se hace*. Una app donde abrir un manga y
  abrir un anime se sienten como dos aplicaciones distintas ha fallado esa regla, por
  bien que funcione cada pantalla por separado.
- **Regla 8.** Donde Aniyomi tocó código compartido de manga, no se porta: se deja el de
  Mihon y se adapta el lado anime. El objetivo real de esa regla es que absorber cambios
  de upstream siga siendo barato.

El cliente pidió que la ficha y la biblioteca de anime tuvieran el mismo diseño que las
de manga. Las de anime estaban escritas a mano. El comentario que encabezaba
`AnimeLibraryContent` explicaba por qué:

> *"...reusing Mihon's library grid, whose components are internal to its own package;
> sharing them would mean widening their visibility and touching files that need to keep
> merging cleanly from upstream."*

La mitad de ese razonamiento era falsa: `internal` en Kotlin es visible en **todo el
módulo de Gradle**, y tanto `eu.kanade.presentation.library.components` como la UI de
anime viven en `:app`. Nunca hizo falta ampliar visibilidad.

## Decisión

**Se reutilizan los componentes de Mihon siempre que estén parametrizados con primitivas,
y solo se duplica lo que estaba atado al tipo `Manga`.**

Reutilizados tal cual, sin tocar nada: `MangaActionRow`, `ExpandableMangaDescription`,
`MangaChapterListItem`, `ChapterDownloadIndicator`, `LazyLibraryGrid`, `MangaListItem`,
`MangaCompactGridItem`, `MangaComfortableGridItem`, las insignias y `GlobalSearchItem`.

Duplicados, porque su firma exigía un `Manga`: la caja de información de la ficha, la
cabecera de episodios, la barra de la ficha y la hoja de filtro.

**Y una excepción deliberada a la regla 8:** en
`app/src/main/java/eu/kanade/presentation/library/components/CommonMangaItem.kt` el
parámetro `coverData` pasa de `MangaCover` a `Any`. Tres líneas.

## Por qué esa excepción

`coverData` no se usa para nada más que entregárselo a Coil como modelo de la portada.
Coil elige el *fetcher* por el tipo en tiempo de ejecución, así que un `AnimeCover`
encuentra `AnimeCoverFetcher` solo y se lleva las cabeceras de su propia fuente.

La alternativa era copiar las 400 líneas de `CommonMangaItem.kt` para cambiar tres
anotaciones de tipo. Eso es peor **según el propio objetivo de la regla 8**:

| | Reaplicar en un merge | Riesgo de divergencia |
|---|---|---|
| Ampliar el tipo (lo hecho) | 3 líneas | ninguno: es el mismo código |
| Copiar el fichero | 0 líneas | 400 líneas separándose en cada release de Mihon |

Duplicar el layout es exactamente lo que produce la divergencia que esta decisión
existe para eliminar.

## Consecuencias

- Las dos bibliotecas y las dos fichas no pueden separarse visualmente sin que alguien
  lo haga a propósito: comparten el código que dibuja los píxeles.
- Un merge con upstream que toque `CommonMangaItem.kt` puede pedir reaplicar el
  ensanchado. El fichero lleva un comentario en la cabecera explicando por qué está así,
  para que quien resuelva el conflicto no lo revierta por error.
- La regla 8 se mantiene como norma. Esta es una excepción acotada y registrada, no una
  puerta abierta: el criterio para repetirla es que el cambio en el fichero de Mihon sea
  semánticamente neutro para el manga y estrictamente menor que la duplicación que evita.

## Alternativas descartadas

- **Copiar `CommonMangaItem.kt`.** Ver la tabla.
- **Registrar un mapper de Coil para que un `AnimeCover` se disfrace de `MangaCover`.**
  Haría que la portada de un anime se pidiera a través del gestor de fuentes de manga,
  que no conoce esa fuente ni sus cabeceras.
- **Generalizar el dominio a `entries`/`items`, como hizo Aniyomi.** Prohibido por la
  regla 8 y por la ADR 0001, y desproporcionado para un parámetro.
