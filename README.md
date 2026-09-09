# Zenyomi

Lector de manga y reproductor de anime en una sola app Android.

Zenyomi es un **doble fork**: toma [Mihon](https://github.com/mihonapp/mihon) como
base técnica y porta encima las funciones de anime de
[Aniyomi](https://github.com/aniyomiorg/aniyomi). Ambos descienden de Tachiyomi.

## Por qué existe

Aniyomi ya juntaba anime y manga, pero lleva más de diez meses sin publicar una
release (v0.18.1.2, octubre de 2025) y se quedó atrás respecto a Mihon, que sigue
publicando cada pocas semanas. El resultado son extensiones que fallan y correcciones
que nunca llegan.

Zenyomi le da la vuelta al planteamiento: parte del código vivo de Mihon y trae el
anime encima, en un árbol paralelo que deja el lado de manga intacto. Así seguir a
Mihon sigue siendo barato, y **ninguna función de Mihon se pierde por el camino**.

## Estado

En desarrollo temprano. Consulta el estado real en
[`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) y el plan en
[`docs/ROADMAP.md`](docs/ROADMAP.md).

| Fase | Versión | Alcance |
|---|---|---|
| 0 | v0.1.0 | Base de Mihon compilando como Zenyomi |
| 1 | v0.2.0 | Fuentes, biblioteca y fichas de anime |
| 2 | v0.3.0 | Reproductor de vídeo |
| 3 | v0.4.0 | Descargas, historial, trackers y backup de anime |
| 4 | v1.0.0 | Paridad con Aniyomi sin perder nada de Mihon |

## Compilar

Requiere JDK 21 y el SDK de Android.

```sh
./gradlew :app:assembleDebug
```

## Documentación

| Documento | Contenido |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Charter: roles, reglas, ramas, convenciones |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Cómo se combinan los dos forks |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Fases y entregables |
| [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) | Estado vivo, bloqueos |
| [`docs/PORTING_LOG.md`](docs/PORTING_LOG.md) | Procedencia del código portado |
| [`docs/adr/`](docs/adr) | Decisiones de arquitectura |
| [`docs/FORK_COMPLIANCE.md`](docs/FORK_COMPLIANCE.md) | Auditoría de licencia y marcas |

## Licencia

Apache License 2.0 — ver [`LICENSE`](LICENSE) y [`NOTICE`](NOTICE).

Zenyomi es una obra derivada de Mihon y Aniyomi, ambos Apache-2.0. Los nombres,
logos y marcas de Mihon, Aniyomi y Tachiyomi pertenecen a sus respectivos
propietarios y no se distribuyen con este proyecto; Apache-2.0 no concede derechos
de marca (sección 6). El nombre y el icono de Zenyomi son propios.

Este proyecto no aloja ni distribuye contenido. Las fuentes de contenido provienen
de extensiones de terceros ajenas al proyecto.
