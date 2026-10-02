---
name: zen-branch
description: Paso 2 de zen-flow. Crea la rama de trabajo de una tarea de Jira desde develop actualizado y pasa la tarea a En curso.
argument-hint: KAN-n
---

# zen-branch — rama de trabajo

Tarea: $ARGUMENTS

## 1. Precondiciones

```sh
git status --porcelain          # debe estar vacío
git fetch origin
git rev-list --left-right --count origin/develop...develop
```

- **Árbol sucio:** para y averigua de quién es. Nunca hagas `stash`, `reset` ni `checkout --`
  sobre trabajo que no sabes de dónde sale.
- **`develop` local por detrás** de `origin/develop`: `git checkout develop && git pull --ff-only`.
- **`develop` local por delante:** son commits sin publicar. Para y díselo al cliente: el
  PR los arrastraría y no se puede revisar lo que no es suyo.

## 2. Nombre

`<tipo>/kan-<n>-<slug>`, con el tipo según `CLAUDE.md` §5: `feat`, `fix`, `port`, `chore`.
El slug, en inglés, con 3-5 palabras en kebab-case. Ejemplo: `fix/kan-9-count-untrusted-anime-extensions`.

Si la rama ya existe (en local o en origin), es que el trabajo se retoma: haz checkout de
ella y no crees otra.

```sh
git checkout -b <rama> develop
```

## 3. Jira

- `transitionJiraIssue` con id `21` (*En curso*).
- Comentario: `Rama: <rama>`.
