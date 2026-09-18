# ADR-0006 — Las descargas se deciden por el contenido, no por el formato

**Fecha:** 2026-09-17 · **Estado:** aceptada

## Contexto

El descargador de anime preguntaba una sola cosa de lo que devolvía la URL de un
vídeo: «¿empieza por `#EXTM3U`?». Si sí, era HLS y se trataba como tal. Si no, se
daba por hecho que eran los bytes del vídeo y se escribían al disco.

Esa segunda mitad es una suposición abierta con un fallo silencioso. El día que una
fuente sirvió DASH (AnimeOnsen), los 8 KB del manifiesto MPD se guardaron como si
fueran el episodio, con su marca de descargado. Es exactamente el mismo fallo que
cerró la v0.15.0 para HLS, en la otra familia de streaming — y habría vuelto igual
con Smooth Streaming, con una página de login, y con cualquier formato que nadie
hubiera previsto todavía.

El cliente lo señaló con la pregunta correcta: *«¿no podemos hacerlo general, para
no tener que aplicar un fix por extensión?»*. Arreglar DASH añadiendo un `<MPD` al
olfateo habría sido precisamente el arreglo por formato que hay que evitar.

## Decisión

**1. La pregunta se invierte.** No «¿es HLS?», sino «¿esto son medios o son
instrucciones para encontrarlos?». Los contenedores son binarios; los manifiestos
son texto. Así que:

- bytes binarios → se escriben al disco;
- texto → se le entrega a ffmpeg, que habla HLS, DASH y Smooth Streaming;
- texto que es una página web o JSON → se rechaza con un motivo.

Lo que antes era el caso por defecto —guardar lo que no se reconocía— pasa a ser el
caso excepcional, y el nuevo caso por defecto es el que sabe interpretar formatos.

**2. Lo que hay dentro se le pregunta a ffprobe.** Qué calidades ofrece un manifiesto
y cuánto pesa cada una son preguntas que ffprobe responde para cualquier formato que
ffmpeg abra. Escribir un lector de MPD, y luego uno de Smooth Streaming, es la
rueda de la que este ADR baja al proyecto.

Elegir el stream no es sólo cosmético: ffmpeg descarta los streams que nadie mapea, y
un stream descartado es uno que el demuxer no llega a descargar. Sin `-map` explícito,
descargar 360p de un manifiesto con tres calidades se bajaría también las otras dos.

**3. Nada se llama episodio hasta que se comprueba que lo es.** Terminada la descarga,
ffprobe mira el fichero: tiene que ser un contenedor, con un stream de vídeo y una
duración. Si ffprobe lo identifica como `dash` o `hls`, es un manifiesto que se guardó
en vez de seguirse, y la descarga ha fallado.

Esta es la red que hace que lo anterior sea seguro: el siguiente formato que nadie ha
previsto dará una descarga fallida —visible— en lugar de una vacía —invisible—.

**4. Los lectores propios son optimización, no requisito.** El lector de m3u8 se queda
porque es lo que permite bajar los segmentos en paralelo (~3× más rápido). Todo lo
demás va por ffmpeg: más lento, pero correcto sin conocer el formato. Añadir DASH al
camino rápido, si algún día compensa, es un lector más, no un arreglo por extensión.

## Consecuencias

- Una fuente que cambie de formato de streaming deja de ser código nuevo.
- Las descargas DASH funcionan (verificado con AnimeOnsen: mkv real de 155 MB con un
  único stream de vídeo, su audio y 16 pistas de subtítulos).
- Las fuentes sin lector propio descargan más despacio, porque los segmentos los pide
  ffmpeg de uno en uno. Correcto pero mejorable, y es deuda conocida.
- La estimación de tamaño sale de bitrate × duración declarados, que pueden quedarse
  cortos. La interfaz ya es honesta con eso: cuando los bytes pasan del total, deja de
  mostrar el total.

## Añadido en la v0.18.0: DASH tiene lector

Escrito exactamente como dice el punto 4, y por eso se pudo escribir después sin tocar
nada de lo anterior: `DashManifest` lista los segmentos, `SegmentPrefetcher` los baja en
paralelo y los une, y ffmpeg recibe ficheros locales. Cubre `SegmentTemplate` por número
y por línea de tiempo, `SegmentList`, y la representación que es un solo fichero. Lo que
no reconoce —varios periodos, un manifiesto en directo, un segmento que se repite «hasta
que acabe»— lo declina, y entonces lo baja ffmpeg como antes.

Medido en el emulador con AnimeOnsen: de ~7 minutos a **52 segundos**, con el fichero
resultante igual byte a byte salvo metadatos de muxing. La estimación de tamaño mejoró de
paso, porque el manifiesto declara bitrates y duración: 150 MB estimados contra 155 reales,
donde ffprobe decía 83.
- `AnimeDownloadProvider` sigue teniendo un filtro por extensión de fichero para no
  listar playlists antiguas como episodios. Es histórico y ya no es la defensa: la
  defensa es la comprobación del punto 3.

## Alternativas descartadas

- **Añadir `<MPD` al olfateo.** Arregla AnimeOnsen y deja el siguiente formato igual de
  roto. Es el arreglo por extensión que el cliente pidió evitar.
- **Escribir un lector de MPD para el camino rápido.** Más trabajo, y sigue sin cubrir
  el formato número tres. Puede añadirse después *encima* de esta decisión, no en su
  lugar.
- **Fiarse del `Content-Type` o de la extensión de la URL.** Ya se sabía que no: las
  fuentes sirven playlists desde rutas acabadas en `.mp4` y etiquetadas como
  `application/octet-stream`, y no lo hacen por accidente.
