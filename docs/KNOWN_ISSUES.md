# Cosas pendientes de comprobar a mano

Lo que el entorno de desarrollo **no puede verificar** y necesita una persona con el
dispositivo delante, o una cuenta/servicio que aquí no hay. Se revisa antes de cada release.

Esto no es una lista de bugs conocidos: es la lista de lo que **no consta como probado**. Si
algo de aquí falla al usarlo, es información nueva y valiosa, no una sorpresa.

---

## 1. Gestos del player: el doble toque

**Estado:** implementado, sin verificar.
**Cómo probarlo:** abrir un episodio y tocar dos veces rápido en la mitad derecha (salta
adelante) y en la izquierda (salta atrás). El salto por defecto es de 10 s y se cambia en
*Ajustes → Player → Double tap jump*. Debe aparecer un `+10 s` / `-10 s` en el centro.

**Por qué no se puede automatizar aquí.** Se intentaron tres vías y las tres fallan:

| Vía | Por qué falla |
|---|---|
| `adb shell input motionevent` | Cada `input` arranca una JVM (~200-400 ms). El segundo toque llega fuera de la ventana de 300 ms del doble toque |
| Dos `input tap` concurrentes | Igual de lento, y además inyecta eventos inconsistentes (`Invalid DOWN event - pointers already down`) que dejan colgado el dispatcher de entrada del sistema |
| `sendevent` sobre `/dev/input` | El emulador es una imagen de producción: `adb root` lo rechaza y SELinux bloquea el acceso aunque el usuario `shell` esté en el grupo `input` |

El toque simple (pausa) **sí** está verificado: no necesita ventana temporal.

---

## 2. Vincular un tracker a una cuenta real — ✅ VERIFICADO (2026-09-15)

Ya no es una incógnita. El cliente facilitó una cuenta de pruebas y se probó de punta a punta
contra los servicios reales, comprobando el resultado **en sus servidores**, no en la app:

| Paso | MyAnimeList | AniList |
|---|---|---|
| Iniciar sesión (OAuth) | ✅ | ✅ |
| Buscar un anime | ✅ | ✅ |
| Vincular la entrada | ✅ | ✅ |
| Empujar progreso | ✅ | ✅ |

Comprobación independiente, consultando cada servicio desde fuera de la app:

```
MyAnimeList  num_watched_episodes: 1   status: 1 (watching)
AniList      progress: 2               status: CURRENT
```

Salieron tres defectos de la prueba, los tres ya corregidos: la hoja decía «Reading» en vez de
«Watching», el selector de progreso se titulaba «Chapters» en vez de «Episodes», y los dos
selectores se dibujaban sin superficie encima de la tarjeta de detrás.

**Nota de identidad, pendiente de decisión del cliente.** Las pantallas de autorización de
MyAnimeList y de AniList dicen «**Mihon** is requesting permission». Los identificadores de
cliente OAuth son los de Mihon, heredados del fork. Funciona, pero un usuario ve el nombre de
otro proyecto al conceder acceso a su cuenta. Corregirlo es registrar aplicaciones propias en
ambos servicios, lo cual requiere que el cliente cree esas apps. Mientras tanto queda anotado
aquí para que no se descubra por sorpresa.

### Kitsu — ✅ VERIFICADO (2026-09-15)

El cliente facilitó una cuenta y se probó igual que las otras dos: conduciendo la app en el
emulador y comprobando cada paso **en los servidores de Kitsu**, no en la pantalla.

| Paso | Resultado |
|---|---|
| Iniciar sesión (correo y contraseña, sin OAuth) | ✅ Ajustes muestra el nombre de la cuenta |
| Leer el sistema de puntuación de la cuenta | ✅ la cuenta usa *simple*, y el selector ofrece caritas |
| Buscar un anime desde la app | ✅ «Sousou no Frieren, 28 episodios» y sus secuelas |
| Vincular | ✅ entrada creada, estado *Plan to watch* |
| Empujar progreso | ✅ 3 / 28, y el estado pasa solo a *Watching* |
| Empujar puntuación | ✅ 😊 → `rating: 14` en la escala 2-20 de Kitsu |
| Marcar como privado | ✅ `private: true` |
| Desvincular | ✅ la entrada desaparece de Kitsu |

Y lo que de verdad importaba comprobar: la entrada aparece en la biblioteca **de anime**
(`mediaType: ANIME`) y la de manga se queda vacía.

```
anime library: [{"id":"108338122","progress":3,"status":"CURRENT","rating":14,"private":true}]
manga library: []
```

**La prueba encontró un fallo**, ya corregido: borrar una entrada que ya no existe devuelve un
500 cuyo cuerpo trae `"data":{}` —un objeto vacío, no `null`—, y la deserialización reventaba
antes de llegar a mirar el error, de modo que el perdón previsto para ese caso nunca se
ejecutaba. Está contado en `PORTING_LOG.md`.

---

## 3. Una descarga de vídeo HTTP completada

**Estado:** toda la maquinaria alrededor verificada; el bucle que copia los bytes, no.
**Cómo probarlo:** instalar una extensión de anime que funcione, abrir un episodio y darle al
icono de descarga. Debería aparecer una notificación de progreso, y al terminar el icono pasa
a una marca que sirve de botón de borrado.

**Qué está verificado:** encolar, que la cola sobreviva a que Android mate el proceso, el
worker en primer plano, la notificación de error, el desencolado y el borrado del fichero.

**Qué no:** una transferencia que termine.

El motivo que había aquí — "el emulador no tiene ninguna fuente de anime accesible por red" —
ya no vale: desde el 2026-09-12 hay tres extensiones que resuelven y reproducen en el
emulador (KickAssAnime, AnimeOnsen, TioAnime). Queda pendiente por tiempo, no por falta de
material: es cuestión de encolar un episodio de una de ellas y ver terminar la barra.

---

## 4. Leer un manga y restaurar un backup grande

**Estado:** el código de Mihon está intacto (diff vacío contra `mihon/main` en lector,
biblioteca y dominio de manga), pero no se ha ejercitado en el emulador.
**Por qué no se puede aquí:** no hay manga en la biblioteca del emulador ni fuente
configurada, y montar una biblioteca real de prueba es más trabajo que valor aporta.
**Cómo probarlo:** restaurar tu backup de Mihon, abrir un manga y leer un capítulo. La
auditoría completa de paridad está en [`MIHON_PARITY.md`](MIHON_PARITY.md).

---

## 5. Qué esperar de las extensiones de anime

El ecosistema de Aniyomi está en mal estado y se degrada solo. **Que una fuente falle no
significa que la app esté rota**, y desde la 0.5.6 la app lo dice con palabras en vez de
enseñar la excepción de Java que lanzó la extensión.

El estado medido de cada fuente, cómo se mide y qué se marca dentro de la app está en
[`EXTENSIONS_STATUS.md`](EXTENSIONS_STATUS.md). Resumen de la pasada del 2026-09-11: de 21
fuentes instaladas, 4 llegan a reproducir vídeo, 4 cargan catálogo pero no vídeo, y el
resto no responde.

Cuando una falle, el camino sigue siendo: **Reintentar**, y si persiste **Abrir en WebView**
para resolver un posible desafío de Cloudflare.
