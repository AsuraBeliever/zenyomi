# Registro de portes desde Aniyomi

Toda pieza de código traída desde Aniyomi se anota aquí, con el SHA de origen, para
poder auditar la procedencia y volver a comparar cuando Aniyomi cambie.

Referencia de origen principal: `aniyomi/main` @ `4b5b90a37` (2026-09-05).
Referencia de contraste: `v0.18.1.2` (2025-10-28, última release publicada). Ver ADR-0003.

## Formato

| Fecha | Área | Origen (Aniyomi) | Destino (Zenyomi) | SHA origen | Commit | Notas |
|---|---|---|---|---|---|---|

## Entradas

| Fecha | Área | Origen (Aniyomi) | Destino (Zenyomi) | SHA origen | Notas |
|---|---|---|---|---|---|
| 2026-09-08 | db | `data/src/main/sqldelightanime/dataanime/*.sq` (10 tablas) | igual ruta | `4b5b90a37` | Sin cambios en el SQL |
| 2026-09-08 | db | `data/src/main/sqldelightanime/view/*.sq` (8 vistas) | igual ruta | `4b5b90a37` | Sin cambios |
| 2026-09-08 | db | `data/src/main/sqldelightanime/migrations/113–138.sqm` | **no portado** | `4b5b90a37` | Ver ADR-0004 |
| 2026-09-08 | source-api | `animesource/model/AnimeUpdateStrategy.kt` | `source-api/src/main/kotlin/…` | `4b5b90a37` | Reescrito; ruta `src/main/kotlin` de Mihon en vez de `src/commonMain` |
| 2026-09-08 | source-api | `animesource/model/FetchType.kt` | `source-api/src/main/kotlin/…` | `4b5b90a37` | Reescrito |

Configuración añadida (no portada literalmente): la segunda base sqldelight se declara
en `data/build.gradle.kts` como `AnimeDatabase`, paquete `tachiyomi.data.anime`. Aniyomi
usa el paquete `tachiyomi.mi.data` (herencia de su applicationId `xyz.jmir.tachiyomi.mi`)
y un dialecto distinto; Zenyomi usa el dialecto y el `generateAsync` de Mihon, según la
regla de que Mihon manda en el *cómo*.

## Renombrados sistemáticos al portar

| Aniyomi | Zenyomi |
|---|---|
| `tachiyomi.domain.entries.anime` | `tachiyomi.domain.anime` |
| `tachiyomi.domain.items.episode` | `tachiyomi.domain.episode` |
| `tachiyomi.domain.entries.manga` | *(no se porta — se usa el de Mihon)* |
| `tachiyomi.domain.items.chapter` | *(no se porta — se usa el de Mihon)* |
| `i18n-aniyomi` / `AYMR` | `i18n-anime` / `ANMR` |
