# Cumplimiento como fork

Zenyomi deriva de Mihon y Aniyomi, ambos Apache-2.0. Este documento audita el
cumplimiento y se revisa **antes de cada release**.

## Checklist que pide Mihon

Mihon publica sus requisitos para forks en su `CONTRIBUTING.md`, sección *Forks*.
Estado a 2026-09-12 (revisado antes de la v0.8.1):

| Requisito de Mihon | Estado | Cómo se cumple |
|---|---|---|
| Respetar la LICENSE | ✅ | Apache-2.0 conservada íntegra + `NOTICE` con atribución |
| Cambiar el nombre de la app | ✅ | `app_name` = Zenyomi |
| Cambiar el icono de la app | ✅ | Icono de lanzador **y de splash** originales (índigo, página + play) |
| Cambiar o desactivar el comprobador de actualizaciones | ✅ | `AppUpdateChecker` apunta a `AsuraBeliever/zenyomi` |
| Cambiar el `applicationId` | ✅ | `app.zenyomi` (`.dev` en debug) |
| Usar tu propio proyecto de Firebase | ✅ | `google-services.json` de Mihon eliminado; telemetría desactivada en CI |

## Obligaciones de Apache-2.0

| Sección | Obligación | Cómo se cumple |
|---|---|---|
| 4(a) | Entregar copia de la licencia | `LICENSE` en la raíz |
| 4(b) | Avisar de forma destacada que se modificaron los archivos | `NOTICE` y `README`; historia de git completa |
| 4(c) | Conservar avisos de copyright, patentes y atribución | `NOTICE` acredita a Mihon, Aniyomi y Tachiyomi |
| 4(d) | Mantener el contenido del `NOTICE` original | Incluido |
| **6** | **No concede derechos de marca** | Nombre, logo y assets de marca propios; ningún activo de marca ajeno en el repo |

La sección 6 es la que suele pasarse por alto: la licencia cubre el **código**, no las
**marcas**. Por eso no basta con conservar el `LICENSE` — hay que sustituir logos,
nombre y metadatos de tienda.

## Cómo auditar

**Dos coincidencias son esperadas y no son incumplimientos**, así que la auditoría las
excluye en vez de volver a discutirlas cada release:

- `Theme.Tachiyomi.*` y compañía en `res/values/` son **identificadores internos de estilo**,
  no marca visible para el usuario. Renombrarlos no aporta nada legalmente y encarecería
  cada merge con Mihon.
- `fastlane/` menciona a Mihon **como atribución** ("built on Mihon"), que es precisamente
  lo que la licencia obliga a conservar. El `title.txt` es Zenyomi.

Lo que sí sería un incumplimiento: un icono, logo o captura ajenos, o metadatos de tienda
que se hagan pasar por Mihon o Aniyomi.

```sh
# marca ajena visible para el usuario (excluye identificadores internos de estilo)
grep -rli "mihon\|aniyomi" app/src/main/res/ | grep -v themes.xml
grep -rli "mihon" fastlane/
find . -name "google-services.json" -not -path "*/build/*"
```

Menciones legítimas y esperadas: `LICENSE`, `NOTICE`, `README`, `CHANGELOG`,
`CONTRIBUTING`, `docs/` — ahí Mihon y Aniyomi **deben** aparecer, es la atribución.
También los espacios de nombres internos de Kotlin (`eu.kanade.tachiyomi`,
`mihon.app.*`), que se conservan a propósito para no romper los merges con upstream.

## Incidencias corregidas

- **2026-09-08.** El rebranding de v0.1.0 cambió el icono del lanzador pero no el de
  la pantalla de arranque, que siguió mostrando el logo de Mihon. Además el repo
  llevaba el `google-services.json` de Mihon (`project_id: mihonapp`) y ambos
  workflows compilaban con `-Pinclude-telemetry`, así que los builds de release
  habrían enviado reportes de crash a la analítica de Mihon. Corregido en
  `fix/mihon-branding-leftovers`.
- **2026-09-08 (bis).** El icono del lanzador seguía siendo el de Mihon en los builds
  de debug: `app/src/debug/res/drawable/` sobrescribe el icono por variante y solo se
  había cambiado el de `main/`. Se detectó mirando la pantalla del dispositivo, no el
  código. Además siete archivos Kotlin referenciaban `R.drawable.ic_mihon` para el
  icono de las notificaciones y la cabecera de *Más*.

  **Lección:** los recursos por variante (`src/<buildType>/res/`) son un punto ciego.
  La auditoría de marca debe mirar `app/src/*/res/`, no solo `app/src/main/res/`, y
  verificarse contra el APK compilado:
  `aapt2 dump resources <apk> | grep -c ic_mihon` debe dar 0.
