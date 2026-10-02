---
name: zen-merge
description: Paso 7 de zen-flow. Mergea en develop un PR aprobado por zen-review y con el CI en verde. Borra la rama, actualiza develop en local y deja la tarea de Jira al día.
argument-hint: <nº de PR>
---

# zen-merge — merge en develop

PR: $ARGUMENTS

## 1. Puertas (todas, o no se mergea)

```sh
gh pr view <nº> --json state,mergeable,baseRefName,reviews,headRefName
gh pr checks <nº>
```

1. `baseRefName` es `develop`. **Nunca se mergea un PR contra `main`**: a `main` solo se
   llega con una release.
2. El último comentario de revisión de `zen-review` dice **APROBADO**, y es posterior al
   último push de la rama.
3. Todos los checks en verde, **sobre el último commit de la rama**. Si siguen corriendo,
   espera con `gh run watch <id> --exit-status` en segundo plano (ver `zen-pr`). Si alguno falla, lee el log
   (`gh run view <id> --log-failed`) y vuelve a `zen-implement`.
   - Hay un fallo conocido que no es nuestro: JitPack con `Could not find
     flexible-adapter`. Relanza el job (`gh run rerun <id> --failed`) una vez.
4. `mergeable` es `MERGEABLE`. Si hay conflicto con `develop`, rebasa la rama sobre
   `develop` (todavía no es historia publicada en `develop`), vuelve a probar y se repite
   la revisión.

## 2. Merge

Commit de merge, como el resto de la historia de `develop`:

```sh
gh pr merge <nº> --merge --delete-branch \
  --subject "<tipo>: <resumen del PR en inglés> (#<nº>)" \
  --body "Refs: KAN-n"
git checkout develop && git pull --ff-only origin develop
git branch -d <rama> 2>/dev/null || true
```

## 3. Jira

La tarea **se queda en *En revisión***, porque *Finalizada* llega con la release (`CLAUDE.md`
§11). Para que se distinga de un PR abierto:

- Etiqueta `en-develop` (`editJiraIssue`, añadiéndola a las que ya tenga).
- Comentario: `Mergeado en develop: <sha del merge> (PR #<nº>). Sale en la próxima release.`

## 4. Comprobación final

`git log --oneline -3 develop` muestra el merge, y `gh run list --branch develop -L 1`
muestra el CI de `develop` arrancado. Si ese CI falla, es lo primero que se arregla.
