# Lo que el anime todavía no hace como el manga

> **Cerrado el 2026-09-15.** Los trece puntos están hechos y comprobados en el dispositivo.
> Se conserva como registro de qué faltaba, por qué, y cómo se cerró — y como recordatorio de
> que el patrón (componentes de Mihon alimentados con valores inertes) puede repetirse.

**Auditoría del 2026-09-15.** El documento hermano, [`MIHON_PARITY.md`](MIHON_PARITY.md),
comprueba lo contrario: que no hayamos *roto* nada de Mihon. Este mide lo que al lado de anime
le falta para llegar a donde ya está el de manga.

Origen: el cliente reportó que en la ficha de un anime no se pueden seleccionar episodios para
marcarlos como vistos, ni «hacia abajo», como sí se hace con los capítulos. Es cierto, y al
mirarlo aparecieron más cosas de la misma familia.

## Cómo se midió

Comparando la superficie de `eu.kanade.presentation.manga.MangaScreen` —la lista de *callbacks*
que Mihon ofrece— contra lo que la ficha de anime pasa realmente, y **comprobándolo en el
dispositivo**, no solo leyendo código:

```
manga, pulsación larga sobre un capítulo →  Cancel · Select all · Select inverse ·
                                            Bookmark chapter · Mark as read ·
                                            Mark previous as read · Delete
anime, pulsación larga sobre un episodio →  no ocurre nada
```

El patrón se repite en todas ellas: **la interfaz de anime reutiliza los componentes de Mihon
pero les pasa valores inertes**. `selected = false` fijo, `onLongClick = {}` vacío,
`chapterSwipeStartAction = Disabled`. Se ve igual porque *es* el mismo componente; lo que falta
está detrás, en el modelo de vista.

## La ficha de un anime

| # | Qué falta | Qué hace el manga | Dónde | Peso |
|---|---|---|---|---|
| 1 | **Selección de episodios** | Pulsación larga entra en modo selección | `AnimeDetailsScreen.kt:346,354` — `selected = false`, `onLongClick = {}` | 🔴 |
| 2 | **Barra de acciones en lote** | Marcar visto/no visto, marcador, descargar, borrar | falta el `bottomBar` con `SharedMangaBottomActionMenu` | 🔴 |
| 3 | **Marcar anteriores como vistos** | `onMarkPreviousAsReadClicked` | no existe | 🔴 |
| 4 | **Seleccionar todo / invertir** | En la barra superior, en modo selección | `AnimeToolbar` no tiene `actionModeCounter`, `onSelectAll` ni `onInvertSelection` | 🔴 |
| 5 | **Deslizar una fila** | Configurable: marcar leído o marcador | `AnimeDetailsScreen.kt:352` — `Disabled` en los dos sentidos | 🟠 |
| 6 | **Refrescar tirando** | `PullRefresh` envuelve la lista | la ficha de anime no lo envuelve | 🟠 |
| 7 | **Compartir** | `onClickShare` en la barra | `AnimeDetailsScreen.kt` pasa `onClickShare = null` | 🟠 |
| 8 | **Portada a pantalla completa** | `onCoverClicked` → pantalla propia, con compartir y guardar | no hay equivalente de `MangaCoverViewModel` | 🟠 |
| 9 | **Progreso dentro del episodio** | La fila dice «Página 12» de un capítulo a medias | `readProgress = null`, aunque `Episode.lastSecondSeen` **sí existe** | 🟡 |
| 10 | **Notas** | `onClickEditNotes` | `onEditNotes = {}` vacío | 🟡 |
| 11 | **Intervalo de actualización editable** | `onEditFetchIntervalClicked` | `onEditIntervalClicked = null` | 🟡 |
| 12 | **Diseño de dos paneles en tablet** | `TwoPanelBox`, ficha a la izquierda y lista a la derecha | la ficha de anime tiene un solo diseño | 🟡 |

## La biblioteca de anime

| # | Qué falta | Dónde | Peso |
|---|---|---|---|
| 13 | **Selección múltiple y acciones en lote** (mover a categoría, marcar visto, descargar, borrar de la biblioteca) | `AnimeLibraryContent.kt` — `isSelected = false` y `onLongClick = {}` en los cuatro modos de vista; falta el equivalente de `LibraryBottomActionMenu` | 🔴 |

## Lo que sí está a la par

No todo está desparejado, y conviene no tocarlo:

- **Novedades de anime** — tiene selección y su barra de acciones en lote. ✅
- **Historial** — no tiene selección, pero es que **el de Mihon tampoco**. Están iguales. ✅
- **Hoja de tracking** — arreglada el 2026-09-15; abre como hoja sobre la ficha, igual que la de
  manga, con el mismo componente.  ✅
- **Explorar, catálogo, búsqueda global, filtros y orden** — ya se llevaron a paridad en su día.

## Orden sugerido

1, 2, 3 y 4 son **una sola pieza**: el modo selección. Hacerlos juntos es casi todo el valor de
esta lista, y es exactamente lo que el cliente pidió. El 13 es esa misma pieza en la biblioteca.

Después 5, 6, 7 y 8, que son independientes entre sí y pequeños.

9, 10, 11 y 12 al final: son detalles, y el 12 solo afecta a tablets.
