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
