# Cumplimiento como fork

Zenyomi deriva de Mihon y Aniyomi, ambos Apache-2.0. Este documento audita el
cumplimiento y se revisa **antes de cada release**.

## Checklist que pide Mihon

Mihon publica sus requisitos para forks en su `CONTRIBUTING.md`, sección *Forks*.
Estado a 2026-09-16 (revisado antes de la v0.13.0, la v0.13.1, la v0.14.0, la v0.14.1, la v0.14.2,
la v0.14.3, la v0.14.4, la v0.15.0 y la v0.16.0):

| Requisito de Mihon | Estado | Cómo se cumple |
|---|---|---|
| Respetar la LICENSE | ✅ | Apache-2.0 conservada íntegra + `NOTICE` con atribución |
| Cambiar el nombre de la app | ✅ | `app_name` = Zenyomi |
| Cambiar el icono de la app | ⚠️ | Propio hasta la v0.13.0 (índigo, página + play). Desde la v0.13.1 es una ilustración de terceros por decisión del cliente — ver abajo |
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

## Observación abierta: credenciales de API heredadas

Los identificadores de cliente de MyAnimeList, AniList y **Kitsu** son los de Mihon,
heredados del fork. No es un incumplimiento de licencia ni de marca —no hay activo de
marca ajeno en el repo—, pero son credenciales de otro proyecto ante servicios de
terceros. En MyAnimeList y AniList además se ve: su pantalla de autorización dice
«Mihon». En Kitsu no, porque se entra con correo y contraseña y no hay pantalla de
consentimiento; la credencial sigue siendo suya.

Corregirlo pide registrar aplicaciones propias en cada servicio, lo cual requiere que
el cliente las cree. Anotado aquí para que cada auditoría lo encuentre ya discutido en
vez de volver a descubrirlo.

## Riesgo asumido: el icono de la v0.13.1 es material de terceros

**Decisión del cliente, 2026-09-15.** El icono de lanzador y la pantalla de arranque usan una
ilustración de **Ryuk**, personaje de *Death Note*, tomada de wallpapers.com. Es obra con
derechos de sus titulares (Tsugumi Ohba / Takeshi Obata / Shueisha / Madhouse), y wallpapers.com
no es una fuente con licencia: es un redistribuidor.

Esto **contradice la regla de la sección «Cómo auditar»** de este mismo documento —«un icono,
logo o captura ajenos» sería un incumplimiento—, así que queda anotado aquí en vez de dejar que
una auditoría futura lo encuentre y no sepa si fue un descuido. No lo fue.

Lo que se le explicó al cliente antes de decidir, y que sigue siendo cierto:

- Ser open source y no tener ánimo de lucro **no exime**. La infracción no depende del lucro, y
  el uso legítimo es un análisis de varios factores donde lo no comercial suma pero usar la
  imagen entera como identidad de la app no transforma nada.
- El riesgo realista no es una demanda: es una **retirada por DMCA** de la release o del repo, y
  un aviso en GitHub va contra la cuenta, no solo contra el proyecto. Shueisha es de las
  editoriales más activas justo con aplicaciones de manga.
- Cierra la puerta a F-Droid o cualquier tienda, que rechazan material de terceros.
- Contrapeso honesto: hay muchos forks de hobby que hacen exactamente esto y no les pasa nada.

El cliente lo asumió con conocimiento de causa. Si algún día hay que revertirlo, el icono
original que se diseñó como alternativa —manzana con el triángulo de reproducción calado— está
en el historial, en el commit anterior a este.

**El resto de la auditoría sigue limpia**: nombre, `applicationId`, comprobador de
actualizaciones, Firebase, `LICENSE` y `NOTICE` intactos, y ningún activo de Mihon ni de Aniyomi
en el repo. Lo de arriba es lo único ajeno, y es deliberado.

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
