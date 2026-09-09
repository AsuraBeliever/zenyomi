# Arquitectura de Zenyomi

## Punto de partida: qué separa realmente a los dos forks

Ambos forks descienden de Tachiyomi. Ancestro común:
`c6601c1f` — *Release v0.15.2*, 2024-01-08.

Desde entonces (medido 2026-09-08):

| | commits desde el ancestro | último commit | **última release** |
|---|---|---|---|
| Mihon | 1557 por delante de Aniyomi | 2026-09-08 | v0.20.4 — 2026-08-05 |
| Aniyomi | 1726 por delante de Mihon | 2026-09-05 | v0.18.1.2 — **2025-10-28** |

Comparten historia, así que git puede hacer `cherry-pick` y `diff` entre los dos
forks directamente. Eso es lo que hace viable el porte.

## Aniyomi está abandonado de facto

Aniyomi lleva **más de diez meses sin publicar una release** (v0.18.1.2, octubre 2025),
mientras Mihon publica cada pocas semanas. Su rama `main` sí recibe commits, pero ese
trabajo nunca llega a los usuarios: lo que la gente tiene instalado es de octubre de 2025.

Eso explica directamente los fallos de extensiones que sufre Aniyomi hoy. Su migración
a extension-lib 17 y a la tienda de extensiones nueva está escrita en `main` y **sin
publicar**, así que la app instalada se quedó anclada a una librería antigua mientras
el ecosistema de extensiones siguió avanzando.

Su rama `m_mihon` (último commit 2025-10-12) era el intento del propio equipo de Aniyomi
de rebasar sobre Mihon. También quedó abandonada. Es la razón por la que aquí se hace
al revés: partir de Mihon y traer el anime encima.

## La diferencia estructural que manda sobre todo lo demás

Aniyomi **generalizó el dominio** para que sirviera a anime y manga a la vez:

```
Mihon                          Aniyomi
domain/manga/       ────►      domain/entries/manga/  +  domain/entries/anime/
domain/chapter/     ────►      domain/items/chapter/  +  domain/items/episode/
```

Ese refactor toca todas las capas y es exactamente lo que rompe la absorción de
cambios de Mihon: cada commit de Mihon que toca `manga`/`chapter` entra en conflicto.

**Decisión de Zenyomi (ADR-0001): no adoptamos `entries`/`items`.**
Mihon se queda literalmente intacto y el anime entra al lado:

```
domain/manga/     ← Mihon, sin tocar
domain/chapter/   ← Mihon, sin tocar
domain/anime/     ← nuevo, portado de aniyomi:domain/entries/anime
domain/episode/   ← nuevo, portado de aniyomi:domain/items/episode
```

Ventajas: los merges desde Mihon siguen siendo casi limpios para siempre, y se
cumple la regla dura de no perder ninguna feature de Mihon.
Coste: algo de duplicación entre el lado manga y el lado anime. Se acepta.

## Base de datos

Aniyomi ya mantiene el anime en **una segunda base sqldelight independiente**:

```
data/src/main/sqldelight/tachiyomi/data/*.sq        manga  (Mihon, intacta)
data/src/main/sqldelightanime/dataanime/*.sq        anime  (portada de Aniyomi)
```

Tablas de anime: `animes`, `episodes`, `animehistory`, `anime_sync`,
`animesources`, `animes_categories`, `categories`, `anime_relations`,
`custom_buttons`, `extension_store` + vistas.

Esto es la mejor noticia del proyecto: **la BD de anime es puramente aditiva**.
No hay que migrar ni tocar el esquema de manga de Mihon.

## Extensiones

- El cargador, instalador y verificador de firmas es el de **Mihon** (`ext` engine).
- Se extiende para reconocer también APKs de extensiones de **anime**
  (metadata `tachiyomi.animeextension.*` de Aniyomi).
- Aniyomi migró en 2026-08 a "extension store" con formato nuevo (extension-lib 17);
  Mihon también tiene ya `extension_store.sq`. Se converge sobre el de Mihon.

## Reproductor de vídeo

Portado de Aniyomi. Dependencias nativas pesadas:

| Componente | Para qué |
|---|---|
| `aniyomi.mpv` (mpv-android) | motor de reproducción |
| FFmpeg-kit + `libffmpegkit_abidetect` | decodificación / filtros |
| `torrserver` | streaming por torrent |
| `mediasession`, `seeker`, `truetypeparser` | controles, barra de progreso, subtítulos |

Son artefactos maven publicados, no hay que compilar `.so` a mano.

**Comprobado el 2026-09-09:** los cinco resuelven (JitPack para mpv y FFmpeg, Maven
Central para el resto), empaquetan sus 24 librerías nativas y la app arranca sin
`UnsatisfiedLinkError`.

Coste en tamaño, medido sobre el APK de depuración:

| ABI | Sin player | Con player |
|---|---|---|
| arm64-v8a | 73 MB | 106 MB |
| armeabi-v7a | — | 95 MB |
| universal | 174 MB | 305 MB |

Las que más pesan son `libavcodec.so` (17 MB) y `libmpv.so` (5,5 MB). Los builds de
release aplican R8 y separación por ABI, así que la cifra que le llega al usuario es la
de su arquitectura, no la universal.

## Módulos Gradle

Mihon tiene módulos que Aniyomi no (`core:metro`, `telemetry`, `icons`,
`presentation-core` distinto, `baseline-profile`). Se conservan todos.
Se añade `i18n-anime` (equivalente a `i18n-aniyomi`) para las cadenas de anime,
para no contaminar el `i18n` de Mihon y facilitar los merges de upstream.

## Superficie del porte

De los 2039 archivos de Aniyomi, **553 tocan anime / player / episode / torrent**.
Ese es, en orden de magnitud, el trabajo a portar.
