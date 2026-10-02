---
name: zen-ticket
description: Paso 1 de zen-flow. Convierte un bug, mejora o cambio que pide el cliente en una tarea de Jira bien formada (tipo, épica, etiquetas, criterio de hecho) en el espacio KAN. Devuelve la clave KAN-n.
argument-hint: <descripción del cliente>
---

# zen-ticket — crear la tarea en Jira

Constantes en `CLAUDE.md` §11 (cloudId, clave `KAN`, transiciones, etiquetas).

Petición: $ARGUMENTS

## 1. No duplicar

Antes de crear, busca si ya existe:

```
project = KAN AND statusCategory != Done AND text ~ "<2-3 palabras clave>"
```

Si existe una tarea que cubre lo mismo, **úsala**: añade un comentario con lo que el
cliente acaba de decir y devuelve esa clave.

## 2. Entender antes de escribir

Investiga lo justo para que la tarea sea concreta: dónde vive el código afectado, cómo se
reproduce, qué hace Mihon/Aniyomi en ese punto. Si es un bug, intenta reproducirlo o al
menos localizar la causa probable. No implementes nada todavía.

## 3. Clasificar

| Campo | Regla |
|---|---|
| Tipo | `Historia` si el cliente lo nota en la app. `Tarea` si no (build, CI, docs, refactor) |
| Etiquetas | `bug` si es un fallo, más los ámbitos de commit que toque (`anime`, `player`, `reader`…) |
| Épica (`parent`) | la épica abierta de la próxima release (`Release vX.Y.Z`). Si no hay, la de la fase (`KAN-5` Fase 4, `KAN-6` Fase 5) o `KAN-7` Mantenimiento |
| Responsable | sin asignar. Solo se asigna al cliente si necesita **su** decisión (`decision-cliente`) |

Para encontrar la épica de release abierta:
`project = KAN AND issuetype = Epic AND statusCategory != Done ORDER BY created DESC`.

## 4. Redactar (Markdown)

Resumen corto en español, con lo que el usuario nota, no con el nombre de la clase.

```markdown
**Qué pasa / qué se pide:** <palabras del cliente, aclaradas>

**Qué debería pasar:** <comportamiento esperado>

**Cómo se reproduce:** <pasos, si es un bug>

**Hecho cuando:**
1. <criterio comprobable en el emulador>
2. <criterio comprobable por test>

**Notas técnicas:** <ficheros/módulos implicados, causa probable, referencia a Mihon/Aniyomi>
```

El «Hecho cuando» es el contrato que `zen-test` y `zen-review` van a comprobar. Escríbelo
para que se pueda verificar, no como intención.

No uses casillas `- [ ]`: el Markdown de Jira las escapa. Usa listas numeradas.

## 5. Crear y devolver

`createJiraIssue` con `projectKey: KAN`, `parent`, `additional_fields.labels`. Devuelve la
clave y el enlace (`https://alansethmanjarrez.atlassian.net/browse/KAN-n`).
