# Estado de las extensiones de anime

El ecosistema de Aniyomi se degrada solo: los sitios cambian y las extensiones no lo hacen.
Esto **se mide**, no se opina. La medición la produce un arnés que vive en los builds de debug.

**Última pasada: 2026-09-12**, emulador Pixel 10 Pro XL, 21 fuentes instaladas.

---

## Cómo se mide

```sh
export PATH=$PATH:$HOME/Android/Sdk/platform-tools
./gradlew :app:installDebug
adb shell am start -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.AnimeSourceProbeActivity
# ...espera a que la pantalla diga "Done"; unos 6 minutos para 21 fuentes
adb exec-out run-as app.zenyomi.dev cat files/source-probe.tsv
```

Cada fuente pasa por los tres pasos que hace una persona: pedir el catálogo, abrir la
primera entrada y pedir sus episodios, y resolver el vídeo del primer episodio. Una fuente
cuyo catálogo carga puede seguir siendo inservible, y ese es justo el caso que interesa.

Opciones: `-e only <texto>` prueba solo las fuentes cuyo nombre coincida; `-e list true`
lista fuentes e ids sin tocar la red. Las trazas completas van a logcat con el tag
`AnimeSourceProbe`.

**La actividad se queda en pantalla a propósito.** La primera versión terminaba al instante
y medía desde segundo plano, donde Android le corta la red al proceso: las 21 fuentes
dieron "Unable to resolve host", que se lee exactamente igual que un sitio muerto. Un arnés
capaz de dar una respuesta equivocada con seguridad es peor que no tener arnés.

---

## Resultado de la pasada del 2026-09-12

| Fuente | Idioma | Hasta dónde llega | Detalle |
|---|---|---|---|
| AnimeOnsen | Multi | ✅ vídeo | 30 entradas, 1 vídeo |
| Jkanime | Español | ✅ vídeo | 30 entradas, 16 vídeos |
| Latanime | Español | ✅ vídeo | 30 entradas, 4 vídeos |
| TioAnime | Español | ✅ vídeo | 20 entradas, 3 vídeos |
| TioHentai | Español | ✅ vídeo | 20 entradas, 3 vídeos |
| AllAnime | English | ⚠️ catálogo | 26 entradas; el episodio no resuelve a ningún vídeo |
| AnimeKhor | English | ⚠️ catálogo | 20 entradas; ídem |
| KickAssAnime | English | ⚠️ catálogo | 24 entradas; ídem |
| MonosChinos | Español | ⚠️ catálogo | 30 entradas; el vídeo tardó más de 45 s |
| AnimeFenix | Español | 🔴 nada | `animefenix2.tv` ya no existe (el sitio vive en `animefenix.tv`) |
| Animetsu | Multi | 🔴 nada | su API devuelve HTML donde la extensión espera JSON |
| AniZone | Multi | 🔴 nada | hoy agota el tiempo; el 11-sep dio un NPE dentro de su propio parser |
| AnimeLatinoHD | Español | 🔴 nada | no consigue pasar Cloudflare |
| AnimePahe | English | 🔴 nada | no consigue pasar Cloudflare |
| 9AnimeTV | English | 🔴 nada | HTTP 522 |
| AniWatchtv | English | 🔴 nada | HTTP 522 |
| AnimeFLV | Español | 🔴 nada | HTTP 522 |
| Kaido | English | 🔴 nada | HTTP 522 |
| Jellyfin (×3) | Multi | ⚙️ configurar | "Select library in the extension settings" |

**Lo que cambió en un día.** Jkanime y Latanime pasaron de agotar el tiempo a reproducir;
KickAssAnime pasó de reproducir a no resolver vídeo; AnimeLatinoHD pasó de un 404 a un
bloqueo de Cloudflare. Es exactamente el motivo por el que esto se mide antes de cada
release en vez de mantenerse a mano.

---

## Qué se marca dentro de la app

`app/src/main/assets/anime-source-health.json` lleva la lista que la app enseña bajo cada
fuente en *Explorar → Anime sources*, con la fecha de la comprobación.

**Solo entran los fallos que son de la fuente**, es decir los que no dependen de la red de
quien mire:

- el dominio ya no existe
- el sitio devuelve 404 en el endpoint que la extensión pide
- la extensión ya no sabe leer la página (NPE, JSON que llega como HTML)

**Quedan fuera a propósito** los tiempos de espera, las conexiones rechazadas, los 5xx y los
403 de Cloudflare: vuelven, y marcarlos acertaría menos veces de las que fallaría. Una lista
en la que no se puede confiar es peor que ninguna lista.

Con ese criterio, la pasada del 2026-09-12 marca tres: AnimeFenix, Animetsu y AniZone.
Cada una se confirmó además desde una segunda red antes de escribirla.

**AnimeLatinoHD sale de la lista.** El 11-sep daba un 404 y se marcó como endpoint muerto;
hoy da un bloqueo de Cloudflare, que el criterio excluye a propósito porque vuelve. El
criterio se corrigió solo, que es para lo que está.

**AniZone se queda con matiz.** Hoy agota el tiempo antes de llegar a parsear, así que esta
pasada no reproduce el fallo; pero el sitio responde 200 en medio segundo desde otra red y
el 11-sep el NPE se capturó dentro de su propio `popularAnimeParse`. Un sitio vivo que la
extensión no sabe leer sigue siendo "desactualizada".

**Se regenera antes de cada release.** Los ids salen de `-e list true`.

---

## Lo que no se arregla desde aquí

Que una extensión esté rota es trabajo de quien la mantiene. Nosotros podemos:

1. **Decirlo bien**, que es lo que hace `AnimeSourceError`: un sitio caído, un bloqueo de
   Cloudflare y una extensión obsoleta piden cosas distintas del usuario, y antes los tres
   salían como el mismo volcado de Java.
2. **Marcarlo**, que es lo que hace este documento y el asset.
3. **Arreglar una concreta** si el cliente la usa de verdad: el código de las extensiones es
   Apache-2.0 y se puede compilar y firmar con nuestra clave. AnimeFenix, por ejemplo, es
   cambiar un dominio. Mantener las 260 no lo es.

---

## Cobertura en inglés (medida el 2026-09-12)

"¿Cuál tiene más anime subtitulado al inglés?" no se responde mirando la portada de una
fuente: su página de populares no dice si tiene el título que buscas. El arnés tiene un modo
que pregunta por una lista de títulos a cada fuente y cuenta cuántos encuentra:

```sh
adb shell am start -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.AnimeSourceProbeActivity \
  -e coverage "'Frieren|Cowboy Bebop|Mushishi|Dandadan|Monster'"
adb exec-out run-as app.zenyomi.dev cat files/source-coverage.tsv
```

Las comillas simples dentro de las dobles no son decorativas: sin ellas la shell del
dispositivo parte la cadena por el `|` e intenta ejecutar cada trozo.

| Fuente | Títulos hallados | Resultados por título | ¿Reproduce hoy? |
|---|---|---|---|
| AniWave (Unoriginal) | **5/5** | 4, 4, 5, 3, 30 | no resuelve vídeo |
| Anichi | 5/5 | 3, 4, 5, 2, 30 | no resuelve vídeo |
| AnimeKai (Unoriginal) | 5/5 | 3, 4, 5, 2, 30 | no resuelve vídeo |
| AnimePahe | 4/5 | —, 3, 6, 2, 8 | catálogo da 403 |
| AnimeOnsen | 4/5 | 2, 1, 0, 2, 7 | **sí**, 1 vídeo |
| KickAssAnime | 0/5 (caída al medir) | — | **sí**, 3 vídeos |
| AnimeKhor | 1/5 | 0, 0, 0, 0, 10 | no resuelve vídeo |
| Miruro.tv | 0/5 | 0 en todo | catálogo vacío |
| AllAnime, 9AnimeTV, AniWatchtv, Kaido, AniZone, Animetsu | 0/5 | sitio caído al medir | no |

**Dos avisos sobre estos números.**

"Monster" es una palabra corriente y casa con decenas de títulos: ese 30 mide el buscador, no
el catálogo. Un muestreo mejor usaría títulos sin palabras genéricas.

Anichi y AnimeKai devolvieron **exactamente** los mismos conteos en los cinco títulos pese a
vivir en dominios distintos (`anichi.to` y `animekaitv.to`). Instalar las dos probablemente no
da dos catálogos independientes.

**La tensión que sale del dato:** las tres de mayor cobertura llegan al catálogo y a la lista
de episodios pero no resuelven vídeo, y las dos que sí reproducen tienen catálogos más
pequeños. El arnés resuelve el primer episodio de la primera entrada de populares, así que un
"no resuelve vídeo" no demuestra que la fuente nunca reproduzca — puede ser esa entrada, o un
desafío de Cloudflare que la app sí pasa por WebView y el arnés no.


---

## Qué reproduce de verdad (medido el 2026-09-12)

La tabla de arriba mide *cobertura*: cuántos títulos encuentra cada fuente. Esto mide lo
otro, que es lo que le importa a quien quiere ver un capítulo: **abrir el vídeo en el
player**. El arnés tiene un modo que lleva una fuente por el mismo camino que un toque —
mismo interactor, mismo `PlaybackRequest`, misma Activity del player:

```sh
adb shell "am start -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.AnimeSourceProbeActivity \
  -e play 1 -e only 'KickAssAnime'"
```

Las comillas dobles envolviendo el comando entero no son decorativas: sin ellas la shell del
dispositivo parte el nombre de la fuente por el espacio.

| Fuente | Resuelve | Reproduce | Notas |
|---|---|---|---|
| KickAssAnime | 3 vídeos | ✅ | HLS + 8 subtítulos externos y 2 audios; se ven en pantalla |
| AnimeOnsen | 1 vídeo | ✅ | DASH + 9 subtítulos externos; el selector de pistas los lista |
| TioAnime | 3 vídeos | ✅ | HLS con subtítulos incrustados en el vídeo |
| Jkanime | 16 vídeos | ⚠️ | abre y sabe la duración, pero el primer mirror vivo corta los paquetes |
| Latanime | 4 vídeos | ⚠️ | el mirror elegido responde 404; ahora se dice en pantalla |
| Anichi, AniWave, AnimeKai | 0 vídeos | ❌ | ver abajo |
| AllAnime, AnimeKhor | 0 vídeos | ❌ | catálogo y episodios sí, vídeo no |

### Por qué Anichi (y AniWave, y AnimeKai) no reproducen

Las tres son la misma base de código y el mismo backend. Siguiendo el tráfico de Anichi con
un episodio real, la extensión llega hasta el final sin error:

```
anichi.to/ajax/server/list?servers=…        200
mapper.nekostream.site/api/mal/58567/13/…   200
anichi.to/ajax/server?get=…                 200   (×6, los servidores HD-1, HD-2, Vidstream-2)
megaplay.buzz/stream/s-2/135936/sub         200
megaplay.buzz/stream/getSources?id=…        200
megaplay.buzz/stream/getSourcesNew?id=…     200
```

…y devuelve **cero vídeos**. La última respuesta explica por qué: donde antes venía la lista
de fuentes en claro, hoy viene cifrada.

```json
{"tracks":[…],"intro":{…},"outro":{…},"server":4,
 "enc":"wdeBruh3qqn_i5wUNnyaPcXqidp1UWP84FfPHzGyKXBiGlRpu4FRQjbs…"}
```

La extensión no sabe descifrar ese campo, así que se queda sin nada que devolver y se lo
traga en silencio. **No es un fallo nuestro y no se puede arreglar desde la app**: quien
tiene que ponerse al día es la extensión. Su repositorio de origen (`yuzono/aniyomi-extensions`)
está retirado por DMCA desde el 2026-02-05, así que tampoco se puede recompilar con un
parche. Las tres quedan marcadas como `outdated` en `anime-source-health.json`, que es lo que
hace que la app lo diga en vez de no hacer nada al tocar el episodio.

---

## Idiomas que entrega cada fuente (medido el 2026-09-12)

Cuántos títulos tiene una fuente no dice nada sobre en qué idioma los sirve. Esto es lo
segundo, que es lo que decide si se puede ver. El arnés lo saca del propio `Video`:

```sh
adb shell "am start -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.AnimeSourceProbeActivity \
  -e videos 1 -e title 'Dandadan'"
```

| Fuente | Audio | Subtítulos |
|---|---|---|
| KickAssAnime | **Japonés + Inglés** | EN, FR, DE, IT, **ES**, PT, RU, AR |
| AnimeOnsen | Japonés | 18: EN, **Español**, **Español (Spain)**, PT-BR, FR, DE, IT, PL, RU… |
| TioAnime, Jkanime, Latanime | uno solo | ninguno aparte; el subtítulo va quemado en el vídeo |

**El doblaje en español no existe como pista en ninguna.** Los sitios hispanos lo modelan como
una *entrada distinta*: en Latanime, "Dandadan" y "Dandadan CR Castellano" son dos animes
separados. Reunir JA/EN/ES de audio en un mismo reproductor exigiría una fuente propia que
combine varias, o una biblioteca propia con ficheros `.mkv` multipista. Decidido el 2026-09-12
no construir ninguna de las dos por ahora.

**Qué hace falta para aprovechar lo que sí hay:** poner los idiomas en *Ajustes → Player →
Preferred audio / subtitle languages*. Acepta nombres o códigos de dos o tres letras
(`es,en` y `ja,es,en` valen), y el player los cruza con las etiquetas de la fuente, que
raramente son códigos.
