---
name: zen-flow
description: Flujo completo de un cambio en Zenyomi, desde que el cliente lo pide hasta que está mergeado en develop. Usar SIEMPRE que el cliente reporte un bug, pida una mejora o una feature, o pida cualquier cambio en la app o el proyecto ("no funciona X", "quiero que Y", "cambia Z", "falla al…"). Encadena zen-ticket → zen-branch → zen-implement → zen-test → zen-pr → zen-review → zen-merge.
argument-hint: <descripción del bug, mejora o cambio>
---

# zen-flow — del reporte al merge

El cliente (Alan) describe algo en lenguaje de producto. Este flujo lo convierte en una
tarea de Jira, una rama, código, pruebas, un PR revisado y un merge en `develop`, **sin
que el cliente tenga que tocar nada**. Las reglas de `CLAUDE.md` mandan sobre todo lo que
dice aquí.

Petición: $ARGUMENTS

## Pasos

Ejecuta cada paso con la herramienta Skill, en orden. Cada uno recibe la clave de Jira
(`KAN-n`) que devuelve el primero.

| # | Skill | Sale con |
|---|---|---|
| 1 | `zen-ticket` | tarea en Jira, clave `KAN-n` |
| 2 | `zen-branch KAN-n` | rama `<tipo>/kan-n-<slug>` desde `develop`, tarea *En curso* |
| 3 | `zen-implement KAN-n` | commits en la rama, compilando y con spotless |
| 4 | `zen-test KAN-n` | pruebas de backend + visuales en el emulador, informe |
| 5 | `zen-pr KAN-n` | PR abierto contra `develop`, CI lanzado |
| 6 | `zen-review <nº PR>` | veredicto APROBADO o CAMBIOS, publicado en el PR |
| 7 | `zen-merge <nº PR>` | PR mergeado en `develop`, rama borrada, Jira al día |

**Bucle de revisión.** Si `zen-review` pide cambios, vuelve al paso 3 con los hallazgos,
después al 4, empuja la rama y repite el 6. Máximo **3 rondas**: a la tercera sin aprobar,
para y explícale al cliente qué bloquea, en lenguaje de producto.

**Si falla una prueba**, se arregla dentro del paso 3/4, no se salta. Nunca se abre PR con
pruebas en rojo ni se mergea con el CI en rojo.

## Cuándo parar y preguntar al cliente

Solo en estos casos. Todo lo demás lo decides tú como responsable técnico.

- La petición admite dos comportamientos de producto razonables y distintos. Pregunta con
  `AskUserQuestion`, con opciones en lenguaje de producto.
- El cambio **quitaría o degradaría** algo de Mihon (regla dura 1). No se hace; se le
  explica y se propone otra vía.
- El cambio exige migrar datos del usuario (esquema de BD) o algo irreversible.
- Varias peticiones en un mensaje: una tarea y un flujo por cada una, en secuencia.

## Al terminar

Informe breve al cliente, en español y sin jerga:

- qué se cambió, visto desde la app,
- cómo se comprobó, con lo que se vio en el emulador,
- enlaces: tarea de Jira y PR,
- que queda en `develop` y saldrá en la próxima release.

No publiques release: eso es `docs/RELEASING.md`, y va aparte.
