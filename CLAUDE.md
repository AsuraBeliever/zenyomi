# Zenyomi — Charter del proyecto

> Este archivo es el contrato de trabajo. Se lee al inicio de cada sesión.
> Estado vivo del proyecto: `docs/PROJECT_STATUS.md`.

## 1. Qué es Zenyomi

Lector de manga + reproductor de anime en una sola app Android.
Doble fork: **base técnica = Mihon**, **objetivo funcional = Aniyomi**.

## 2. Reparto de roles

| Rol | Quién | Alcance |
|---|---|---|
| Responsable técnico | Claude | Arquitectura, código, ramas, versiones, commits, releases, CI, build, testing en dispositivo, documentación |
| Cliente / Product owner | Alan (AsuraBeliever) | Prueba el producto, reporta qué falta y qué falla, decide prioridades de producto |

El cliente no gestiona git ni el build. Si algo técnico requiere su decisión, se le
presenta como opciones de producto, no como detalles de implementación.

## 3. Reglas duras (no negociables)

1. **NO se pierde ninguna feature de Mihon.** Mihon es la base; el anime se *añade*,
   nunca se sustituye ni se degrada nada de manga. Cualquier PR que elimine
   funcionalidad de Mihon se rechaza.
2. **Mihon es la referencia de "cómo se hace".** Ante conflicto de diseño entre
   Mihon y Aniyomi, gana Mihon. Aniyomi aporta el *qué* (features de anime), no el *cómo*.
3. **El motor de extensiones es el de Mihon.** Aniyomi arrastra problemas en su
   carga de extensiones; se usa la infraestructura de Mihon y se extiende para
   soportar también extensiones de anime.
4. **Licencia: Apache-2.0.** Mihon y Aniyomi son Apache-2.0; una obra derivada no
   puede relicenciarse a MIT. Ver `docs/adr/0002-licencia.md`. El cumplimiento como
   fork (incluida la lista que pide Mihon) se audita en `docs/FORK_COMPLIANCE.md`
   **antes de cada release**.
5. **Se documenta sobre la marcha.** Todo porte queda registrado en
   `docs/PORTING_LOG.md`; toda decisión de arquitectura en `docs/adr/`.
6. **Nunca se reescribe historia publicada** (`main`, `develop`, tags).

## 4. Arquitectura en una frase

Mihon intacto + un árbol paralelo de anime (dominio `anime`/`episode`, BD sqldelight
separada, player mpv) portado desde Aniyomi. Bibliotecas de Anime y Manga en pestañas
separadas, al estilo Aniyomi. Detalle en `docs/ARCHITECTURE.md`.

## 5. Ramas

| Rama | Uso |
|---|---|
| `main` | Estable. Solo llega vía merge de `develop`. Cada release se tagea aquí. |
| `develop` | Integración. Todo trabajo se mergea aquí primero. |
| `feat/kan-<n>-<slug>` | Feature nueva |
| `fix/kan-<n>-<slug>` | Corrección |
| `port/kan-<n>-<slug>` | Porte de código desde Aniyomi |
| `chore/kan-<n>-<slug>` | Build, CI, deps, docs |
| `sync/mihon-<version>` | Absorber cambios de upstream Mihon |
| `upstream-mihon` / `upstream-aniyomi` | Espejos de solo lectura. **Nunca commitear aquí.** |

Remotes: `origin` (nuestro), `mihon` y `aniyomi` (solo fetch).

Las ramas de trabajo llevan la clave de su tarea de Jira y entran en `develop` **por PR**,
revisado y con el CI en verde (sección 11).

## 6. Commits

Conventional Commits, en inglés, imperativo:

```
<tipo>(<ámbito>): <resumen>

<cuerpo opcional>

Ported-from: aniyomi@<sha>   # solo en commits de tipo port
```

Tipos: `feat` `fix` `port` `refactor` `chore` `docs` `build` `ci` `test`.
Ámbitos: `anime` `manga` `player` `reader` `library` `browse` `ext` `db` `backup`
`tracker` `download` `settings` `i18n` `core`.

Cada commit debe compilar. Nada de commits "WIP" en `develop` ni en `main`.

**Antes de cada commit: `./gradlew spotlessApply`.** El CI corre `spotlessCheck` y lo
rechaza si no. Lo que genera código a partir de otro fichero casi nunca respeta el
formato, así que esto no es opcional.

## 7. Versionado y releases

`MAJOR.MINOR.PATCH` desde `0.1.0`. `versionCode` incremental manual.

| Versión | Significado |
|---|---|
| 0.1.x | Mihon renombrado a Zenyomi, compila e instala. Sin anime todavía. |
| 0.2.x | Anime navegable: fuentes, biblioteca, ficha, extensiones de anime |
| 0.3.x | Player funcional |
| 0.4.x | Descargas, historial, trackers y backup de anime |
| 1.0.0 | Paridad con Aniyomi sin haber perdido nada de Mihon |

**Higiene de tags.** Los remotes `mihon` y `aniyomi` están configurados con
`tagOpt = --no-tags`: sus tags (v0.1.0 … v0.20.4, 159 en total) colisionan con
nuestra numeración y no deben entrar en el repo. Si alguna vez reaparecen, se borran
en local — siguen disponibles en los upstreams. Antes de tagear, comprobar que el
nombre está libre; `git tag` falla si existe, pero `git push origin <tag>` publicaría
entonces el tag ajeno sin avisar.

Release = tag `v<version>` en `main` + APK firmado + entrada en `CHANGELOG.md`.
Variantes: `debug` (`app.zenyomi.debug`), `dev`, `release`.

## 8. Convenciones de código al portar desde Aniyomi

- Aniyomi generalizó el dominio a `entries`/`items`. **Zenyomi no adopta esa
  abstracción**: Mihon conserva `manga`/`chapter` tal cual, y el anime entra como
  árbol paralelo `anime`/`episode`.
- Mapeo al portar: `tachiyomi.domain.entries.anime` → `tachiyomi.domain.anime`,
  `tachiyomi.domain.items.episode` → `tachiyomi.domain.episode`.
- Donde Aniyomi tocó código compartido de manga, **no se porta**: se deja el de Mihon
  y se adapta el lado anime.
- Al portar un archivo se registra su SHA de origen en `docs/PORTING_LOG.md`.

## 9. Testing

**En el emulador.** Todo build se instala y se abre en el emulador antes de darlo por
bueno; nunca se entrega sin eso.

**El celular del cliente solo cuando él lo pide.** Es su teléfono de uso diario, no un
banco de pruebas: no se instala nada ahí por iniciativa propia, ni se le ofrece en cada
cambio. Sigue emparejado por wireless debugging para cuando haga falta.

Procedimiento, fixtures y estado de la conexión: `docs/TESTING.md`.

## 10. Entorno

- JDK 21 (`/usr/lib/jvm/java-21-openjdk`) — no usar el 26 del sistema
- Android SDK: `~/Android/Sdk`, `adb` en `~/Android/Sdk/platform-tools`
- Gradle 9.7.1 vía wrapper, AGP 9.3.2

## 11. Gestión de tareas: Jira

El trabajo se organiza en Jira y **lo lleva Claude por completo**: crea, mueve, comenta
y cierra las tareas. El cliente puede crear tareas o comentar, pero no tiene por qué.

- Sitio: https://alansethmanjarrez.atlassian.net — espacio **Zenyomi**, clave `KAN`
- `cloudId`: `f04e906b-0ec4-4966-b049-acd498427118`
- Acceso: MCP `atlassian` (`https://mcp.atlassian.com/v1/mcp`). Si pide autenticación,
  el cliente la hace con `/mcp` → atlassian → Authenticate.

**Estados y transiciones** (id para `transitionJiraIssue`):

| Estado | id | Significa |
|---|---|---|
| Tareas por hacer | `11` | Pendiente |
| En curso | `21` | Claude está trabajando en ella |
| En revisión | `31` | PR abierto, o ya en `develop` (etiqueta `en-develop`) sin publicar |
| Finalizada | `41` | Publicada en una release, o decisión tomada, o verificación hecha |

**Épicas:** una por release (`Release vX.Y.Z`), una por fase del roadmap y
`Mantenimiento continuo` (KAN-7). Tipos: `Historia` para lo que el cliente nota, `Tarea`
para el resto. No existe el tipo Bug: un fallo es una `Tarea` con la etiqueta `bug`.

**Etiquetas:** los ámbitos de commit (`anime`, `player`, `ext`, …) más `bug`, `release`,
`verificacion`, `rendimiento`, `escritorio`, `sync`, `decision-cliente` y `en-develop`
(mergeada, pendiente de release).

**Responsable:** las tareas de Claude van sin asignar, porque Jira solo tiene la cuenta del
cliente. Todo lo que requiere al cliente, sobre todo las decisiones de producto, se le
asigna a él (`70121:f22207c0-2104-4fb2-97e6-8f23667e6a63`) con la etiqueta
`decision-cliente`, y las opciones se escriben en lenguaje de producto (sección 2).

**Flujo: skill `zen-flow`.** Cuando el cliente reporta un bug, pide una mejora o cualquier
cambio, se ejecuta `zen-flow` (`.claude/skills/`), que encadena:

| Paso | Skill | Qué hace |
|---|---|---|
| 1 | `zen-ticket` | crea la tarea en Jira (o reutiliza la que ya existe) con su «Hecho cuando» |
| 2 | `zen-branch` | rama `<tipo>/kan-<n>-<slug>` desde `develop`, tarea *En curso* |
| 3 | `zen-implement` | código, tests y commits con `Refs: KAN-n` |
| 4 | `zen-test` | backend (spotless, tests, migraciones) + visual en el emulador con capturas revisadas, logcat y regresión de manga; evidencia en Jira |
| 5 | `zen-pr` | push y PR contra `develop` con la evidencia; tarea *En revisión* |
| 6 | `zen-review` | revisión independiente (subagente) contra el charter; veredicto en el PR |
| 7 | `zen-merge` | con APROBADO y CI verde: merge en `develop`, borra la rama, etiqueta `en-develop` |

GitHub no permite aprobar un PR propio y solo hay una cuenta, así que la aprobación es el
comentario de revisión **APROBADO** de `zen-review`. Sin él, no hay merge.

Además:

- Al empezar una sesión de trabajo se consultan las tareas abiertas (`project = KAN AND
  statusCategory != Done`) y los comentarios nuevos del cliente.
- Todo trabajo tiene tarea, incluido el que no pasa por `zen-flow` (releases, docs).
- Al publicar la release, todas las tareas de su épica pasan a *Finalizada*, y la épica
  también.

**Jira no sustituye a `docs/`.** Jira dice *qué* hay que hacer y en qué estado está. El
detalle técnico, las mediciones y las decisiones siguen en `docs/PROJECT_STATUS.md`,
`docs/adr/` y `docs/PORTING_LOG.md`, y las tareas enlazan ahí en vez de copiarlo.
