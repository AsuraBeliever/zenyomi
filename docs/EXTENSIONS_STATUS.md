# Estado de las extensiones de anime

El ecosistema de Aniyomi se degrada solo: los sitios cambian y las extensiones no lo hacen.
Esto **se mide**, no se opina. La medición la produce un arnés que vive en los builds de debug.

**Última pasada: 2026-09-15** (antes de la v0.14.1), emulador Pixel 10 Pro XL, 24 fuentes instaladas.

---

## Cómo se mide

```sh
export PATH=$PATH:$HOME/Android/Sdk/platform-tools
./gradlew :app:installDebug
adb shell am start -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.AnimeSourceProbeActivity
# ...espera a que la pantalla diga "Done"; unos 6 minutos para 24 fuentes
adb exec-out run-as app.zenyomi.dev cat files/source-probe.tsv
```

Cada fuente pasa por los tres pasos que hace una persona: pedir el catálogo, abrir la
primera entrada y pedir sus episodios, y resolver el vídeo del primer episodio. Una fuente
cuyo catálogo carga puede seguir siendo inservible, y ese es justo el caso que interesa.

**Desde el 2026-09-15 se anotan también los idiomas de las pistas**, no solo cuántas hay.
«Tiene 18 subtítulos» no responde a «¿está en español?», que es lo que decide si una fuente
le sirve a quien la usa.

Y hay un segundo modo que mide **amplitud de catálogo**, buscando una batería de títulos:

```sh
adb shell am start -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.AnimeSourceProbeActivity \
  -e coverage "'One Piece|Sousou no Frieren|Cowboy Bebop|Monster|Mushishi|Dandadan|...'"
adb exec-out run-as app.zenyomi.dev cat files/source-coverage.tsv
```

Opciones: `-e only <texto>` prueba solo las fuentes cuyo nombre coincida; `-e list true`
lista fuentes e ids sin tocar la red. Las trazas completas van a logcat con el tag
`AnimeSourceProbe`.

**La actividad se queda en pantalla a propósito.** La primera versión terminaba al instante
y medía desde segundo plano, donde Android le corta la red al proceso: las 21 fuentes
dieron "Unable to resolve host", que se lee exactamente igual que un sitio muerto. Un arnés
capaz de dar una respuesta equivocada con seguridad es peor que no tener arnés.

---

## Resultado de la pasada del 2026-09-15

### Las que reproducen vídeo

| Fuente | Idioma | Catálogo | Subtítulos seleccionables | Audio |
|---|---|---|---|---|
| **KickAssAnime** | English | 10/10 títulos | **English, Spanish**, French, German, Italian, Portuguese, Russian, Arabic | **Japonés + Inglés** |
| **AnimeOnsen** | Multi | 7/10 títulos | **English, Español, Español (Spain)**, Русский, Português (BR), Italiano, Français, Deutsch, العربية | — |
| Jkanime | Español | 10/10 títulos | ninguno seleccionable (subtítulo incrustado) | — |
| TioAnime | Español | 10/10 títulos | ninguno | — |
| Latanime | Español | 8/10 títulos | ninguno seleccionable (subtítulo incrustado) | — |
| TioHentai | Español | 1/10 títulos | ninguno | hentai, no catálogo general |

### Las que cargan catálogo pero no reproducen

| Fuente | Idioma | Catálogo | Detalle |
|---|---|---|---|
| Anichi | English | 10/10 | el episodio no resuelve a ningún vídeo |
| AniWave (Unoriginal) | English | 10/10 | ídem |
| AnimeKai (Unoriginal) | English | 10/10 | ídem |
| AllAnime | English | 10/10 | ídem |
| AnimeKhor | English | 1/10 | ídem |

**Estas son la trampa**: catálogo enorme, cero vídeos. Buscar en ellas encuentra de todo y
no sirve para ver nada.

### Las que no responden

| Fuente | Idioma | Detalle |
|---|---|---|
| 9AnimeTV, AniWatchtv, Kaido, AnimeFLV | en / es | HTTP 522 |
| AnimePahe | English | HTTP 403 (Cloudflare) |
| AnimeLatinoHD | Español | no pasa Cloudflare |
| AnimeFenix | Español | `animefenix2.tv` no existe |
| Animetsu | Multi | su API devuelve HTML donde la extensión espera JSON |
| AniZone | Multi | NPE dentro de su propio parser |
| Miruro.tv | English | catálogo vacío, sin error |
| Jellyfin (×3) | Multi | "Select library in the extension settings" — hay que configurarlas |

### Amplitud de catálogo, título a título

Búsquedas sobre diez títulos: populares, clásicos, de nicho, una película y uno reciente.
El número es **cuántos resultados devolvió la búsqueda**, no una garantía de que esté la
serie correcta: «Monster» devuelve treinta cosas en varias fuentes y casi todas son ruido.
Un cero sí es concluyente; un número alto solo dice que la búsqueda encontró algo plausible.

| Fuente | One Piece | Frieren | Cowboy Bebop | Monster | Mushishi | Dandadan | Ping Pong | Kimi no Na wa | Yuru Camp | Shoujo Shuumatsu |
|---|---|---|---|---|---|---|---|---|---|---|
| KickAssAnime | 36 | 3 | 4 | 36 | 7 | 2 | 1 | 1 | 7 | 1 |
| Jkanime | 22 | 2 | 2 | 30 | 5 | 2 | 1 | 1 | 5 | 1 |
| TioAnime | 20 | 2 | 2 | 16 | 4 | 2 | 1 | 1 | 4 | 1 |
| Latanime | 12 | 2 | 2 | 17 | 2 | 4 | — | 2 | 4 | — |
| AnimeOnsen | 10 | 2 | 1 | 7 | — | 2 | — | 18 | — | 7 |

---

## Qué usar, según lo medido

- **Subtítulos elegibles en inglés y español, en la misma fuente: KickAssAnime y AnimeOnsen.**
  Son las dos únicas que entregan pistas de subtítulos separadas, y las dos traen inglés y
  español. KickAssAnime además trae **audio japonés e inglés**, así que cubre también el doblaje.
- **KickAssAnime es la primera opción**: es la única que junta catálogo amplio (10/10),
  subtítulos elegibles en los dos idiomas y doble audio.
- **AnimeOnsen** tiene los subtítulos mejor surtidos pero **catálogo más estrecho** (7/10):
  falló Mushishi, Ping Pong y Yuru Camp.
- Las fuentes en español —**Jkanime, TioAnime, Latanime**— tienen buen catálogo y funcionan,
  pero su subtítulo va **incrustado en el vídeo**: se ve en español y no hay nada que elegir.
  Sirven si quieres español y no te importa no poder cambiar.
- **No pierdas el tiempo con Anichi, AniWave, AnimeKai ni AllAnime.** Encuentran todo y no
  reproducen nada.
