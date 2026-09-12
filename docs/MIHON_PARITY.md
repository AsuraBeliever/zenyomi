# Auditoría: ninguna feature de Mihon perdida

La regla dura nº1 del charter dice que el anime se **añade** y nunca sustituye ni degrada nada
de manga. Esto es la comprobación de esa regla. Se repite antes de cada release grande.

**Revisión: 2026-09-12, contra `mihon/main`, en la v0.6.x.**
**Resultado: no se ha perdido nada.**

> Desde la revisión anterior se tocaron cuatro ficheros más de Mihon —`UpdatesScreen`,
> `HistoryScreen`, `UpdatesTab`, `HistoryTab`— para que esas dos pestañas puedan mostrar
> también la mitad de anime. El cambio es aditivo: un parámetro opcional que por defecto
> es `null` y deja la barra exactamente como la dibuja Mihon. Las únicas líneas eliminadas
> son dos `listOf(` que pasan a `listOfNotNull(` para admitir una acción nullable.
>
> En la v0.8.0 se tocaron además `SourcesScreen` y `ExtensionsScreen`: el cuerpo de sus
> listas se extrae a `LazyListScope.sourceItems` y `LazyListScope.extensionItems`, que las
> propias pantallas siguen llamando. Es un **movimiento** de código, no una pérdida: las
> filas se dibujan y se comportan igual. `ExtensionTrustDialog` deja de ser privada porque
> el diálogo no puede vivir dentro de un `LazyListScope` y sube a quien llama.
>
> **Una divergencia menos:** `label_extensions` vuelve a ser "Extensions". Se había
> renombrado a "Manga extensions" en la v0.5.4 cuando eran pestañas separadas; ahora esa
> pestaña contiene las dos. El único cambio que queda en el `strings.xml` de Mihon es
> `app_name`, que el checklist de forks **exige**.

---

## 1. Prueba mecánica: qué se ha tocado del código de Mihon

### Ficheros de Mihon eliminados

Once, y **ninguno es código**:

| Fichero | Por qué |
|---|---|
| `app/google-services.json` | Proyecto Firebase de Mihon. Eliminarlo lo **exige** el checklist de forks |
| `app/src/main/res/drawable/ic_mihon.xml` | Logo ajeno. Apache-2.0 §6 no concede derechos de marca |
| `.github/assets/logo.png` | Ídem |
| `fastlane/.../images/*.png` (6) | Icono, gráfico de portada y capturas de Mihon en la ficha de tienda |
| `fastlane/.../changelogs/25.txt` | Changelog de una versión de Mihon que no es nuestra |
| `.github/workflows/update_website.yml` | Publica en la web de Mihon; no tenemos web |

### Ficheros de Mihon modificados

52, y solo **13 tienen líneas eliminadas**. Revisadas una a una, **todas son marca**:

| Qué se quitó | Dónde |
|---|---|
| `R.drawable.ic_mihon` → `ic_zenyomi` | `LibraryUpdateNotifier`, `BackupNotifier`, `ExtensionInstallService`, `LogoHeader`, `themes.xml` |
| `app_name` "Mihon" → "Zenyomi" | `i18n/.../strings.xml` |
| `mihonapp/mihon` → `AsuraBeliever/zenyomi` | `AppUpdateChecker` |
| Color e iconos de lanzador | `colors.xml`, `ic_launcher_*.xml` |
| Textos de README, CONTRIBUTING, fastlane | documentación |

El resto de modificaciones son **puramente aditivas**: una interfaz más en la declaración de
`Anilist` y `MyAnimeList`, un valor por defecto en `ALSearchItem.chapters`, entradas nuevas en
`AppGraph` y una fila más en el índice de Ajustes.

```sh
# Reproducir la comprobación
git diff --diff-filter=D --name-only mihon/main..develop     # eliminados
git diff --diff-filter=M --numstat mihon/main..develop       # modificados, con líneas +/-
git diff --stat mihon/main..develop -- \
  'domain/src/main/java/tachiyomi/domain/manga' \
  'domain/src/main/java/tachiyomi/domain/chapter' \
  'app/src/main/java/eu/kanade/tachiyomi/ui/reader' \
  'app/src/main/java/eu/kanade/tachiyomi/ui/library'          # debe salir vacío
```

El dominio de manga, el de capítulos, el lector y la biblioteca de manga tienen **diff vacío**
contra Mihon. No se ha tocado ni una línea.

---

## 2. Prueba funcional: que además siga funcionando

Borrar nada no basta; algo puede estar roto sin estar borrado. Sobre el emulador, build de
depuración de la v0.5.2:

| Superficie | Resultado |
|---|---|
| Las 5 pestañas (Library, Updates, History, Browse, More) | abren, 0 crashes |
| Browse → Sources / Extensions / Migrate | abren |
| Añadir una tienda de extensiones de manga | **funciona**: se añadió el repo de Keiyoushi |
| Lista de extensiones de manga | **se puebla** con extensiones reales del repo |
| Ajustes → Appearance / Library / Reader / Downloads / Tracking / Browse | abren y se pueblan, 0 crashes |
| Ajustes → Data and storage / Security / Advanced / About | abren y se pueblan, 0 crashes |
| Ajustes → Tracking | los 11 trackers de Mihon siguen listados |

Que la tienda de extensiones de manga siga funcionando importa especialmente: el motor de
extensiones de anime se derivó del suyo y comparte `ExtensionStore` como modelo.

---

## 3. Lo que esta auditoría **no** cubre

- **Leer un manga de verdad.** No hay ninguno en la biblioteca del emulador ni fuente
  configurada. El lector abre desde su propia Activity, intacta respecto a Mihon (diff vacío),
  pero no se ha ejercitado aquí.
- **Restaurar un backup grande de Mihon.** El cliente ya lo hizo en su móvil con la v0.2 y
  funcionó; conviene repetirlo en cada release grande.

Ambas cosas necesitan a una persona con la app; van en [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md).
