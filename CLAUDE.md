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
| `feat/<slug>` | Feature nueva |
| `fix/<slug>` | Corrección |
| `port/<slug>` | Porte de código desde Aniyomi |
| `chore/<slug>` | Build, CI, deps, docs |
| `sync/mihon-<version>` | Absorber cambios de upstream Mihon |
| `upstream-mihon` / `upstream-aniyomi` | Espejos de solo lectura. **Nunca commitear aquí.** |

Remotes: `origin` (nuestro), `mihon` y `aniyomi` (solo fetch).

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

Dispositivo real del cliente vía **wireless debugging (ADB over Wi-Fi)**.
Procedimiento y estado de la conexión: `docs/TESTING.md`.
Nunca se entrega un build al cliente sin haberlo instalado y abierto antes.

## 10. Entorno

- JDK 21 (`/usr/lib/jvm/java-21-openjdk`) — no usar el 26 del sistema
- Android SDK: `~/Android/Sdk`, `adb` en `~/Android/Sdk/platform-tools`
- Gradle 9.7.1 vía wrapper, AGP 9.3.2
