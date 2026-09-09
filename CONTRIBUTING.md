# Contribuir a Zenyomi

Zenyomi es un fork de [Mihon](https://github.com/mihonapp/mihon) que añade anime.
Antes de tocar nada, lee [`CLAUDE.md`](CLAUDE.md): ahí están las reglas del proyecto,
la estrategia de ramas y las convenciones de commits.

## Requisitos

- Android development, Kotlin y Jetpack Compose
- JDK 21 y el SDK de Android
- Familiaridad con la arquitectura de Mihon

## Antes de escribir código

Lee [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) y los
[ADRs](docs/adr). Hay dos reglas que condicionan casi todo:

1. **No se pierde ninguna feature de Mihon.** El anime se añade; nunca sustituye ni
   degrada nada del lado de manga.
2. **No se adopta la abstracción `entries`/`items` de Aniyomi.** El manga de Mihon
   queda intacto y el anime va en un árbol paralelo. Es lo que mantiene baratos los
   merges con upstream (ADR-0001).

Si portas código desde Aniyomi, anótalo en
[`docs/PORTING_LOG.md`](docs/PORTING_LOG.md) con el SHA de origen.

## Traducciones

Las cadenas base viven en `i18n/src/commonMain/moko-resources/base/`. Las
traducciones de Mihon llegan por Weblate al upstream; las cadenas propias de Zenyomi
se traducen aquí.

## Si forkeas Zenyomi

Aplica lo mismo que pide Mihon, y por las mismas razones: cambia el nombre, el icono,
el `applicationId` y el comprobador de actualizaciones, y usa tu propio proyecto de
Firebase si añades telemetría. Respeta la [LICENSE](LICENSE) y el [NOTICE](NOTICE):
Apache-2.0 obliga a conservar los avisos de copyright y no concede derechos sobre
marcas ajenas.
