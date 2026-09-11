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

## 2. Vincular un tracker a una cuenta real

**Estado:** implementado para MyAnimeList y AniList, sin verificar de punta a punta.
**Cómo probarlo:** *Ajustes → Tracking → MyAnimeList*, iniciar sesión. Luego, en un anime de
la biblioteca, el icono de tracking (arriba a la derecha) → buscar → elegir. Al ver un
episodio, el progreso debería subir a la cuenta.

**Por qué no se puede aquí.** No hay credenciales de MAL ni de AniList en el entorno, y crear
una cuenta a nombre del cliente no es algo que deba hacerse por su cuenta.

**Lo que sí está comprobado contra el servicio real:** los nombres de campo del modelo de MAL
coinciden exactamente con lo que devuelve hoy `api.myanimelist.net/v2/anime` — ni falta ni
sobra ninguno. Es la parte que más suele romperse.

**AniList está caída, y no es cosa nuestra.** `graphql.anilist.co` responde `403 - The AniList
API has been temporarily disabled due to severe stability issues` incluso a una consulta
trivial sin autenticar. Afecta igual al tracking de manga de Mihon. Por eso se añadió
MyAnimeList en la misma tanda.

---

## 3. Una descarga de vídeo HTTP completada

**Estado:** toda la maquinaria alrededor verificada; el bucle que copia los bytes, no.
**Cómo probarlo:** instalar una extensión de anime que funcione, abrir un episodio y darle al
icono de descarga. Debería aparecer una notificación de progreso, y al terminar el icono pasa
a una marca que sirve de botón de borrado.

**Qué está verificado:** encolar, que la cola sobreviva a que Android mate el proceso, el
worker en primer plano, la notificación de error, el desencolado y el borrado del fichero.

**Qué no:** una transferencia que termine. El emulador no tiene ninguna fuente de anime
accesible por red — la única extensión instalada (Jellyfin) apunta a un servidor que no
existe—, así que el camino solo se ejercita hasta que la fuente falla al resolver el vídeo.

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

El ecosistema de Aniyomi está en mal estado: el índice oficial se ha quedado en 3 extensiones
y las de la comunidad envejecen sin mantenimiento. **Que una fuente falle no significa que la
app esté rota.**

Comprobado en el emulador el 2026-09-10 con el repo `aniyomi-revived-anime-extensions`:

| Fuente | Resultado |
|---|---|
| AnimeOnsen, AllAnime, Jkanime, Latanime | **cargan catálogo** |
| AnimeFLV | `HTTP 522` — el servidor del sitio no responde |
| Animetsu | devuelve HTML donde la extensión espera JSON: el sitio cambió |
| AnimeLatinoHD | pasa Cloudflare vía WebView, luego `HTTP 404`: endpoint desaparecido |

Cuando una falle, el camino es: **Reintentar**, y si persiste **Abrir en WebView** para
resolver un posible desafío.

### Si aun así falla, ¿se puede arreglar?

**Sí.** El código fuente de las extensiones está publicado y es Apache-2.0: el repo trae 61
extensiones en español con su Kotlin completo. Arreglar una es editar su fuente, compilar el
APK y firmarlo con nuestra propia clave, publicándolo en un repositorio nuestro.

Ejemplo real, AnimeLatinoHD: pide `GET /animes/populares` y ese endpoint devuelve 404 hoy.
Arreglarlo es averiguar la ruta actual del sitio y cambiar esa línea.

Lo que **no** es viable es mantener las 260. Cada sitio cambia por su cuenta y eso es trabajo
continuo de una comunidad entera. Lo razonable, si hace falta, es adoptar solo las que el
cliente use de verdad. Es un frente de trabajo aparte, no parte de la app.

