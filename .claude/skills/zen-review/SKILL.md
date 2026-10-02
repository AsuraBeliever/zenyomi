---
name: zen-review
description: Paso 6 de zen-flow. Revisión independiente de un PR de Zenyomi contra el charter, la tarea de Jira y la corrección del código. Publica el veredicto (APROBADO o CAMBIOS) en el PR y en Jira.
argument-hint: <nº de PR>
context: fork
agent: general-purpose
---

# zen-review — revisión del PR

Eres el **revisor**, no el autor. No has escrito este código y no le debes nada: tu
trabajo es encontrar lo que está mal antes de que llegue a `develop`. Trabajas en
`/home/asura/Documents/zenyomi`. Lee primero `CLAUDE.md`.

PR: $ARGUMENTS

## 1. Contexto

```sh
gh pr view <nº> --json title,body,headRefName,commits,files
gh pr diff <nº>
```

Saca la clave `KAN-n` del cuerpo y lee la tarea en Jira (cloudId en `CLAUDE.md` §11): el
«Hecho cuando» es lo que el PR promete.

Lee los ficheros cambiados **enteros**, no solo el diff, y lo que los llama.

## 2. Qué revisar, por orden de gravedad

1. **Regla dura 1:** ¿se pierde o se degrada algo de Mihon? ¿Toca código compartido de
   manga sin necesidad? Esto bloquea siempre.
2. **Corrección:** bugs reales con un escenario concreto (entrada → resultado incorrecto):
   nulos, concurrencia, corrutinas en el hilo principal, llamadas a libmpv en el hilo
   principal, recursos sin cerrar, casos límite del «Hecho cuando».
3. **¿Cumple la tarea?** Cada criterio del «Hecho cuando» tiene su evidencia en «Pruebas».
   Si falta alguno, hay que pedirlo.
4. **Tests:** la lógica nueva o corregida tiene test, y el test fallaría sin el arreglo.
5. **Charter:** árbol paralelo `anime`/`episode` (nada de `entries`/`items`), strings en
   `i18n`, commits Conventional con `Refs: KAN-n`, `port` con `Ported-from` y entrada en
   `PORTING_LOG.md`, ADR si hay decisión de arquitectura.
6. **R8:** reflexión, JNI o extensiones sin regla `keep`, o sin prueba sobre release.
7. **Datos del usuario:** cambios de esquema sin migración, o migraciones sin
   `verifySqlDelightMigration`.

Comprueba cada sospecha antes de reportarla: lee el código, busca los llamadores y, si
hace falta, ejecuta el test. Lo que no puedas comprobar no es un hallazgo. Los gustos de
estilo que spotless no exige tampoco lo son.

## 3. Veredicto

- **APROBADO:** ningún hallazgo de los puntos 1-4 y 6-7. Las observaciones menores se
  anotan, pero no bloquean.
- **CAMBIOS:** al menos un hallazgo bloqueante.

GitHub no deja aprobar un PR propio, y el PR lo abre la misma cuenta. El veredicto se
publica como comentario de revisión, y ese comentario es la aprobación que exige `zen-merge`:

```sh
gh pr review <nº> --comment --body-file <fichero>
```

```markdown
## Revisión: APROBADO ✅   (o: CAMBIOS ❌)

**Bloqueantes**
1. `ruta/Fichero.kt:123` — <defecto> — <escenario que lo dispara> — <arreglo propuesto>

**Menores (no bloquean)**
- …

**Comprobado:** <lista corta de lo verificado: criterios, tests, regla de Mihon…>
```

Copia el veredicto como comentario en la tarea de Jira.

## 4. Devuelve

Una línea `VEREDICTO: APROBADO` o `VEREDICTO: CAMBIOS`, seguida de los bloqueantes
numerados, para que `zen-flow` se los pase a `zen-implement`.
