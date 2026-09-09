# Registro de portes desde Aniyomi

Toda pieza de código traída desde Aniyomi se anota aquí, con el SHA de origen, para
poder auditar la procedencia y volver a comparar cuando Aniyomi cambie.

Referencia de origen principal: `aniyomi/main` @ `4b5b90a37` (2026-09-05).
Referencia de contraste: `v0.18.1.2` (2025-10-28, última release publicada). Ver ADR-0003.

## Formato

| Fecha | Área | Origen (Aniyomi) | Destino (Zenyomi) | SHA origen | Commit | Notas |
|---|---|---|---|---|---|---|

## Entradas

_(vacío — la fase 1 aún no ha empezado)_

## Renombrados sistemáticos al portar

| Aniyomi | Zenyomi |
|---|---|
| `tachiyomi.domain.entries.anime` | `tachiyomi.domain.anime` |
| `tachiyomi.domain.items.episode` | `tachiyomi.domain.episode` |
| `tachiyomi.domain.entries.manga` | *(no se porta — se usa el de Mihon)* |
| `tachiyomi.domain.items.chapter` | *(no se porta — se usa el de Mihon)* |
| `i18n-aniyomi` / `AYMR` | `i18n-anime` / `ANMR` |
