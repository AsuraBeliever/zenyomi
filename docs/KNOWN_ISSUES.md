# Cosas pendientes de comprobar a mano

Lo que el entorno de desarrollo **no puede verificar** y necesita una persona con el
dispositivo delante, o una cuenta/servicio que aquí no hay. Se revisa antes de cada release.

Esto no es una lista de bugs conocidos: es la lista de lo que **no consta como probado**. Si
algo de aquí falla al usarlo, es información nueva y valiosa, no una sorpresa.

---

## 1. Gestos del player: el doble toque — ✅ VERIFICADO POR EL CLIENTE (2026-09-16)

Funciona. El cliente lo probó en el Galaxy S25 Ultra: el doble toque en la mitad derecha
salta adelante y en la izquierda atrás.

De la prueba salió una pega de diseño, ya corregida: el `+10 s` / `-10 s` aparecía en el
centro de la imagen, encima del botón de reproducir y sin decir por qué lado se había
tocado. Ahora se dibuja del lado del que viene el salto, y lo mismo hacen los dos botones
de salto de los controles.

**Por qué siguió sin poder automatizarse aquí.** Se intentaron tres vías y las tres fallan:

| Vía | Por qué falla |
|---|---|
| `adb shell input motionevent` | Cada `input` arranca una JVM (~200-400 ms). El segundo toque llega fuera de la ventana de 300 ms del doble toque |
| Dos `input tap` concurrentes | Igual de lento, y además inyecta eventos inconsistentes (`Invalid DOWN event - pointers already down`) que dejan colgado el dispatcher de entrada del sistema |
| `sendevent` sobre `/dev/input` | El emulador es una imagen de producción: `adb root` lo rechaza y SELinux bloquea el acceso aunque el usuario `shell` esté en el grupo `input` |

El toque simple (pausa) **sí** se verifica por adb: no necesita ventana temporal. Y el
sitio donde sale el aviso del salto sí se comprueba aquí, porque los botones de los
controles pasan por el mismo camino que el doble toque.

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

**Nota de identidad — decidido: se queda así (2026-09-16).** Las pantallas de autorización de
MyAnimeList y de AniList dicen «**Mihon** is requesting permission», porque los identificadores
de cliente OAuth son los de Mihon, heredados del fork. Funciona; lo único raro es ver el nombre
de otro proyecto al conceder acceso. El cliente lo ha visto y le da igual, así que no se
registran aplicaciones propias. Queda anotado para que no se descubra por sorpresa, no como
trabajo pendiente.

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

## 3. Una descarga de vídeo completada — ✅ VERIFICADO EN EL DISPOSITIVO (2026-09-16)

Verificado de punta a punta en el Galaxy S25 Ultra, y **la prueba encontró que no
funcionaba**. El cliente reportó que un episodio descargado no abría sin conexión y decía
«the host is probably down or the link has expired».

La causa: las fuentes no sirven un fichero de vídeo, sirven un m3u8 — unos kilobytes de
texto que nombran unos cientos de segmentos que siguen en internet. El descargador pedía esa
URL como si fuera un mp4, así que guardaba **la lista**. El fichero que había en el móvil del
cliente eran 27 KB con 284 enlaces. Terminaba en un segundo, ponía la palomita y no dejaba ni
un byte del episodio.

Corregido con ffmpeg, que ya estaba en el build porque el reproductor enlaza contra él. De
paso salieron otros tres fallos, todos corregidos y verificados:

| Qué | Cómo se comprobó |
|---|---|
| El episodio se descarga entero | 360 MB para un episodio de 23:41, reproducido desde el fichero local |
| Audio y subtítulos viajan con él | La fuente los entrega aparte; el mkv lleva 2 audios (Japanese, English) y 8 subtítulos, español incluido |
| No toca la red al reproducir | `mpv [fd] Opening fd://240` y cero peticiones HTTP en el log durante la reproducción |
| Una descarga a medias no cuenta como terminada | Se escribe como `.part` y se renombra al acabar; visto crecer y renombrarse a 428 MB |

Ya estaban verificados encolar, que la cola sobreviva a que Android mate el proceso, el
worker en primer plano, la notificación de error, el desencolado y el borrado del fichero.

---

## 4. Leer un manga — ✅ VERIFICADO POR EL CLIENTE (2026-09-16)

El cliente lee manga en el dispositivo sin problemas. Era lo que más importaba de toda
esta lista: la regla dura del charter es que el anime no degrade nada de Mihon, y el
lector es la parte de Mihon que más se usa.

Aquí el código sigue estando intacto (diff vacío contra `mihon/main` en lector, biblioteca
y dominio de manga), y esa es la comprobación que se repite antes de cada release. La
auditoría completa de paridad está en [`MIHON_PARITY.md`](MIHON_PARITY.md).

Lo que sigue sin ejercitarse es **restaurar un backup grande de Mihon**: el backup y la
restauración se prueban aquí con entradas de prueba, no con una biblioteca real de cientos
de títulos.

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
