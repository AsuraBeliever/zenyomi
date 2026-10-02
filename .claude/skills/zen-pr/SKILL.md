---
name: zen-pr
description: Paso 5 de zen-flow. Empuja la rama de una tarea y abre (o actualiza) su PR contra develop en GitHub, con la evidencia de pruebas, y enlaza PR y tarea de Jira.
argument-hint: KAN-n
---

# zen-pr — subir el PR

Tarea: $ARGUMENTS

## 1. Antes de empujar

- Rama actual = la de la tarea, árbol limpio.
- `zen-test` terminó con todo ✅ y existe su `informe.md`. Si no, no se sigue.
- `git log develop..HEAD`: todos los commits llevan `Refs: KAN-n` y ninguno es "WIP".

## 2. Empujar

```sh
git push -u origin HEAD
```

Si el PR ya existe, por una ronda de revisión, basta con el push: el PR se actualiza solo.
Añade un comentario al PR con el nuevo informe de pruebas y lo que cambió en esta ronda.

## 3. Abrir el PR

```sh
gh pr create --base develop --head <rama> --title "<título>" --body-file <fichero>
```

- **Título:** como un Conventional Commit, en inglés, que es el que acabará en el merge:
  `fix(anime): count untrusted extensions in the anime header`.
- **Cuerpo:**

```markdown
## Qué cambia
<para el usuario de la app, en 1-3 frases>

## Por qué
<causa del bug o motivo de la mejora>

## Cómo
<decisiones técnicas relevantes; enlaces a ADR si los hay>

## Pruebas
<contenido de informe.md>

## Jira
[KAN-n](https://alansethmanjarrez.atlassian.net/browse/KAN-n)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

## 4. CI y Jira

- El workflow `Build & Test` corre solo en el PR. Espera sobre **el run**, no sobre el PR:
  `gh pr checks --watch` sale en el acto con «no checks reported» mientras el run está en
  cola. Toma el id del run **de ese commit**, que GitHub tarda unos segundos en crear:
  `gh run list --commit $(git rev-parse HEAD) -L 1 --json databaseId -q '.[0].databaseId'`
  y lanza `gh run watch <id> --exit-status` con `run_in_background`. Sigue con la revisión
  mientras tanto: `zen-merge` espera el resultado. Tras cada push hay que vigilar el run
  del último commit, no el anterior.
- Jira: transición `31` (*En revisión*) y comentario `PR: <url>`.
- Devuelve el número del PR.
