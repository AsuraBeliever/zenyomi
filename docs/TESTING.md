# Testing

Todo build se instala y se abre en el **emulador** antes de darlo por bueno. No se
entrega nada sin eso.

El celular del cliente **solo se toca cuando él lo pide** (2026-09-18): es su teléfono
de uso diario. Sigue emparejado por wireless debugging para cuando lo pida, y el
procedimiento de reconexión está más abajo, pero no se instala ahí por iniciativa
propia.

## Emulador

`emulator-5554` — Pixel 10 Pro XL (AVD), Android 17, x86_64. Tiene ya la fixture de
anime y los clips de FFmpeg que se describen más abajo.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
adb -s emulator-5554 shell am start -n app.zenyomi.dev/eu.kanade.tachiyomi.ui.main.MainActivity
```

## Estado de la conexión del celular (solo si lo pide)

| Campo | Valor |
|---|---|
| Dispositivo | Samsung Galaxy S25 Ultra (SM-S938B) |
| Android | 16 (SDK 36), arm64-v8a |
| guid ADB | `adb-RZGL52RZP5R-9uFDFn` |
| IP habitual | `192.168.1.132` |
| Emparejado | **sí**, desde 2026-09-08 |
| Última verificación | 2026-09-08 — Zenyomi 0.1.0 instalada y abierta |

Ya conviven en el dispositivo `app.mihon` y `xyz.jmir.tachiyomi.mi` (Aniyomi) junto a
`app.zenyomi.dev`, así que se puede comparar comportamiento contra ambos originales.

## Reconectar (uso diario)

El emparejamiento persiste entre reinicios; **el puerto de conexión cambia**. No hay
que pedirle nada al cliente: el puerto se descubre solo por mDNS.

```sh
export PATH=$PATH:$HOME/Android/Sdk/platform-tools
adb devices -l          # normalmente ya aparece conectado
adb mdns services       # si no, lista el ip:puerto actual
```

Solo hace falta repetir el emparejamiento con código si se revocan las autorizaciones
de depuración en el teléfono.

## Emparejamiento (primera vez)

En el celular: *Ajustes → Opciones de desarrollador → Depuración inalámbrica*.
Ambos equipos en la **misma red Wi-Fi**.

1. Dentro de *Depuración inalámbrica*, tocar **"Vincular dispositivo con código
   de vinculación"**. Aparecen un código de 6 dígitos y una `IP:puerto`
   (el puerto de vinculación, aleatorio).
2. En el PC:
   ```
   adb pair <IP>:<puerto-vinculacion>     # pide el código de 6 dígitos
   ```
3. Volver a la pantalla principal de *Depuración inalámbrica* y leer la
   `IP:puerto` **de conexión** (distinta a la de vinculación).
4. ```
   adb connect <IP>:<puerto-conexion>
   adb devices -l
   ```

El emparejamiento persiste; el **puerto de conexión cambia** al reiniciar el
teléfono o reconectar el Wi-Fi. Reconectar entonces solo requiere el paso 4.

## Si el APK de debug no instala encima («signatures do not match»)

Pasa cuando la clave de depuración del equipo no es la que firmó lo que hay instalado.
No hay más salida que desinstalar, y eso se lleva la biblioteca del emulador **y dos
permisos que hay que volver a dar a mano**:

```sh
adb shell "run-as app.zenyomi.dev tar cf - -C /data/data/app.zenyomi.dev databases shared_prefs files" > data.tar
adb uninstall app.zenyomi.dev && ./gradlew :app:installDebug
adb push data.tar /data/local/tmp/ && adb shell "run-as app.zenyomi.dev sh -c 'cd /data/data/app.zenyomi.dev && rm -rf databases shared_prefs && tar xf /data/local/tmp/data.tar'"
adb shell appops set --uid app.zenyomi.dev MANAGE_EXTERNAL_STORAGE allow
```

El otro permiso es el de la carpeta de almacenamiento, que es un permiso SAF y muere con
la instalación: **Más → Datos y almacenamiento → Ubicación de almacenamiento**, elegir
`Documents` y *Permitir*. Hasta que no se hace, la fuente local dice «No results found»
sin un solo error en el log, y hay que **reiniciar la app** para que la relea.

## Ciclo de trabajo por build

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

./gradlew :app:installDebug     # compila e instala en el dispositivo conectado
adb shell monkey -p app.zenyomi.dev -c android.intent.category.LAUNCHER 1
adb logcat -c && adb logcat --pid=$(adb shell pidof -s app.zenyomi.dev)
```

## Fixture de anime en el emulador

Las tres extensiones del repositorio archivado de Aniyomi (Google Drive, GoogleDriveIndex,
Jellyfin) son fuentes autoalojadas: sin un servidor configurado no devuelven contenido, así
que no sirven para probar pantallas que necesitan una entrada real.

Para eso se inserta una entrada a mano en la base de anime del emulador:

```sh
adb -s emulator-5554 shell am force-stop app.zenyomi.dev
adb -s emulator-5554 shell "run-as app.zenyomi.dev cat databases/anime.db" > anime.db
sqlite3 anime.db   # INSERT en animes y episodes; ver PRAGMA table_info para las NOT NULL
adb -s emulator-5554 push anime.db /data/local/tmp/anime.db
adb -s emulator-5554 shell "run-as app.zenyomi.dev sh -c 'cat /data/local/tmp/anime.db > databases/anime.db; rm -f databases/anime.db-wal databases/anime.db-shm'"
```

Hay que borrar el `-wal` y el `-shm` al restituir, o SQLite reaplica el diario y descarta
lo insertado.

**Y al leer hay que traerse los tres ficheros, no solo el `.db`.** Con WAL activado lo
que la app acaba de escribir vive en `anime.db-wal` hasta el siguiente checkpoint, así
que un `cat databases/anime.db` a secas devuelve una foto vieja: se puede comprobar un
cambio, no verlo, y concluir que el código no funciona cuando sí. Lo mismo al preparar
un caso de prueba — `PRAGMA wal_checkpoint(TRUNCATE)` antes de hacer push.

```sh
for f in anime.db anime.db-wal anime.db-shm; do
  adb -s emulator-5554 shell "run-as app.zenyomi.dev cat databases/$f" > live.${f#anime.}
done
sqlite3 live.db "SELECT ..."   # sqlite3 reaplica el wal al abrir
``` El emulador conserva ahora una entrada `Anime de prueba (fixture)` con tres
episodios.

## Fixture de manga

La regresión de manga de cada cambio (regla dura 1, paso `zen-test`) se hace sobre
**«Manga de prueba (fixture)»**, de la fuente local: tres capítulos en CBZ de seis páginas
cada uno, con portada y `details.json`. Cada página lleva grabados su capítulo y su número
(«Capítulo 2» y, debajo, «4 / 6»), así que una captura dice en qué página está el lector sin fiarse
de la app.

```sh
scripts/fixtures/manga-local.sh            # genera y sube a Documents/local/ del emulador
```

El script borra y vuelve a crear la carpeta, así que se puede relanzar siempre. Después,
si el manga no está ya en la biblioteca: **Browse → Sources → Manga → Local source →
Manga de prueba (fixture) → Add to library**.

El lector de Mihon va por defecto **de derecha a izquierda**, como un manga: para pasar a
la página siguiente por adb, el gesto es de izquierda a derecha
(`input swipe 200 1500 1100 1500 200`). El contrario vuelve a la página anterior, y
desde la primera página, al capítulo anterior.

La carpeta `MangaDePrueba` que hay al lado es anterior: un capítulo suelto de dos páginas
sin metadatos. No se usa para la regresión.

## Backup grande de Mihon

`LargeMihonBackupTest` genera un backup de Mihon del tamaño de una biblioteca real, con las
mismas clases de backup de la app: 600 mangas, 54 738 capítulos, 10 categorías, historial,
tracking de MyAnimeList, 50 entradas leídas fuera de la biblioteca, y tres fuentes, una de
ellas instalada (Weeb Central) y dos que no. Usa una semilla fija, así que el fichero sale
igual siempre.

```sh
./gradlew :app:testDebugUnitTest --tests 'eu.kanade.tachiyomi.data.backup.LargeMihonBackupTest'
adb push app/build/fixtures/mihon-large.tachibk /sdcard/Documents/
adb shell content call --method scan_volume --uri content://media --arg external_primary
```

En la app: **More → Settings → Data and storage → Restore backup**. Si el selector de
archivos no lo enseña, busca «mihon-large» con su lupa. Después se comparan los recuentos de
`tachiyomi.db` (con sus `-wal` y `-shm`, ver arriba) con `app/build/fixtures/mihon-large.expected.txt`,
que el test escribe al lado del backup. Para contar solo lo restaurado se filtra por
`url LIKE '/fixture/%'`.

## Vídeos de prueba generados con FFmpeg

Para probar el reproductor sin depender de ninguna fuente, se generan clips locales y se
dejan en `Documents/localanime/` del emulador, donde los recoge la fuente local:

```sh
# clip simple de 10 s con codigo de tiempo grabado en la imagen
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=24:duration=10 \
       -f lavfi -i sine=frequency=440:duration=10 \
       -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest test.mp4

# clip con 2 pistas de audio y 2 de subtitulos, para probar el selector
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=24:duration=10 \
       -f lavfi -i sine=frequency=440:duration=10 \
       -f lavfi -i sine=frequency=880:duration=10 -i es.srt -i en.srt \
       -map 0:v -map 1:a -map 2:a -map 3 -map 4 \
       -c:v libx264 -pix_fmt yuv420p -c:a aac -c:s mov_text \
       -metadata:s:a:0 language=jpn -metadata:s:a:1 language=spa \
       -metadata:s:s:0 language=spa -metadata:s:s:1 language=eng multi.mp4
```

El código de tiempo grabado en la imagen permite comprobar que la posición que muestra la
barra coincide con el fotograma real, sin fiarse solo de lo que reporta la app.

## Fixtures para los botones de omitir y el final del episodio

En `Documents/localanime/` del emulador, además de los clips sueltos:

| Carpeta | Qué prueba |
|---|---|
| `SerieConCapitulos/Ep01.mkv` | 90 s con capítulos **«Avance» (0–8), «Opening» (8–38), «Episodio»**. El botón solo debe aparecer entre 0:08 y 0:38 y aterrizar en 0:38 |
| `SerieAniSkip/Ep01.mp4` y `Ep02.mp4` | 200 s sin capítulos, con un tracker de **MyAnimeList falso apuntando a Jujutsu Kaisen (40748)** en `anime_sync`. AniSkip responde opening 54–145 y ending 170–260, así que sirve para el botón exacto, para el salto automático y para la cuenta atrás de los créditos |
| `SerieDePrueba/Ep01–Ep05` | Clips de 10 s (y Ep04 de 10 min) **sin tracker** y con un título que no es de ningún anime: es el caso del salto fijo de 85 s y de la cadena de episodios |
| `Dandadan/Ep01.mp4` y `Ep02.mp4` | 250 s **sin tracker**, con el nombre de un anime real. Prueba la identificación por título: AniSkip contesta por el id 57334 y el opening del Ep02 va de 2:01 a 3:28, así que el botón **no** sale al empezar y sí en ese tramo. El código de tiempo grabado en la imagen dice dónde aterriza el salto |
| `SerieFinal/Ep01.mkv` y `Ep02.mkv` | 120 s con capítulos **«Episodio» (0–50), «Ending» (50–85), «Escena final» (85–120)**. El botón de omitir el ending solo debe aparecer entre 0:50 y 1:20 y aterrizar en el 1:20.000; y la tarjeta de siguiente episodio no debe salir con los créditos, sino en el último medio minuto, ya sobre la escena final |
| `SerieAniSkip/Ep03.mp4` | 24 min sin capítulos, bajo el mismo tracker falso de Jujutsu Kaisen. AniSkip contesta opening 191–282 y **ending 1274–1364 sobre una copia de 1435 s**, así que prueba la ruta de AniSkip para el ending con deriva entre copias: botón durante los créditos y aterrizaje en el 22:34.000 (1364 − 5 − 5) |
| `Mairimashita! Iruma-kun 4th Season/Ep01.mp4` | Un título que la fuente escribe con «4th Season» y AniList con «4». Prueba el emparejado por palabras con la temporada canonizada: debe resolver al id 60310. **Ojo:** `/sdcard` no admite `:` en un nombre, así que los títulos con dos puntos no se pueden montar como fixture local; ese camino se comprueba contra la API |

| `TorrentBigBuckBunny/Big Buck Bunny.torrent` | El `.torrent` de WebTorrent (*Big Buck Bunny*, Blender, CC BY 3.0): tres ficheros, y el vídeo es el **segundo**, detrás de un `.srt` de 140 bytes. Prueba la reproducción por torrent y que se elige el episodio y no el primero de la lista. Necesita el complemento instalado (`./gradlew :torrent:installDebug`) y red: lo sirven decenas de seeders y la web semilla de webtorrent.io. Se baja con `curl -L https://webtorrent.io/torrents/big-buck-bunny.torrent` |

Para el camino del **magnet**, que es como entregan el vídeo las extensiones, la sonda de debug
abre el reproductor con cualquier url:

```sh
adb shell "am start -S -n app.zenyomi.dev/eu.kanade.tachiyomi.debug.AnimeSourceProbeActivity \
  --es playurl 'magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Big+Buck+Bunny&ws=https%3A%2F%2Fwebtorrent.io%2Ftorrents%2F'"
```

`-S` hace falta si la sonda ya estaba abierta: sin él, Android trae la ventana vieja al frente y
el intent nuevo no llega.

El tracker falso se inserta a mano; la app no lo distingue de uno real:

```sh
sqlite3 anime.db "INSERT INTO anime_sync (anime_id, sync_id, remote_id, library_id, title,
  last_episode_seen, total_episodes, status, score, remote_url, start_date, finish_date)
  VALUES (<animeId>, 1, 40748, NULL, 'Jujutsu Kaisen', 0, 0, 1, 0, '', 0, 0);"
```

`sync_id` 1 es MyAnimeList y 2 AniList, los ids que reparte `TrackerManager`.

## Manejar la app por adb

`scripts/zen-ui.sh` reúne lo necesario para navegar sin tocar el emulador: abrir la app,
tocar por texto o por `content-desc`, leer la pantalla, capturar y contar crashes. Se carga
con `export OUT=<carpeta>; source scripts/zen-ui.sh`, y lo usa la skill `zen-test`. La
cabecera del script explica cada función y las trampas conocidas están en la skill.

## Lo que adb no puede probar

`adb shell input tap` tarda entre 100 y 300 ms por evento, por encima de la ventana de
detección de un doble toque, así que dos taps seguidos llegan siempre como dos toques
simples. Encadenarlos en una sola invocación o usar `input swipe` con duración 1 tampoco
lo consigue.

Consecuencia: el salto por doble toque del reproductor queda sin verificar de forma
automática y hay que probarlo a mano. Lo que sí se comprueba por adb es que la capa de
gestos recibe eventos y está por encima de la superficie de vídeo: un toque simple
alterna la pausa, y eso aparece en el log como `Set property: pause=...`.

## Checklist de humo (cada release)

Manga (regresión — **nada de esto puede romperse nunca**):
- [ ] Abre sin crash
- [ ] Biblioteca de manga carga y muestra portadas
- [ ] Instalar extensión de manga y buscar
- [ ] Abrir capítulo, leer, marcar leído
- [ ] Descargar capítulo
- [ ] Backup y restauración
- [ ] Tracker sincroniza

Anime (a partir de v0.2.0):
- [ ] Biblioteca de anime carga
- [ ] Instalar extensión de anime y buscar
- [ ] Ficha de anime + lista de episodios
- [ ] Reproducir episodio (v0.3.0+)
- [ ] Descargar episodio (v0.4.0+)

## Recolección de fallos

Los crashes van a `docs/logs/` como `YYYY-MM-DD-<slug>.log` y se abre una entrada
en `docs/PROJECT_STATUS.md`.

## Qué hay que probar en una build de **release**, no solo en debug

R8 solo corre en release, así que hay una clase entera de fallos que el debug no puede
enseñar: todo lo que se invoca por **reflexión o desde código nativo**. Ya ha pasado dos veces.

| Cuándo | Qué se rompió | Por qué |
|---|---|---|
| v0.3.0 | Las extensiones de anime no cargaban | R8 finaliza métodos que nadie sobrescribe *dentro* del APK; las extensiones los sobrescriben desde fuera |
| v0.3.0–v0.4.0 | El player mataba la app al primer fotograma | `libmpv` llama a `MPVLib.eventProperty` por JNI; ningún Kotlin la llama, así que R8 la borró |

El segundo se publicó dos veces porque la prueba de humo de release se quedaba en *arranca y
carga una extensión*. **No basta.** Antes de publicar, sobre el APK de release firmado:

1. Abrir la app y comprobar que no hay crash.
2. Cargar la lista de extensiones de anime (cubre las reglas de `source-api`).
3. **Reproducir un vídeo de verdad hasta pasar del primer fotograma** (cubre el puente JNI de mpv).
4. Entrar y salir de picture-in-picture.
5. `adb logcat -d | grep -cE 'Fatal signal|FATAL EXCEPTION|NoSuchMethodError'` debe dar 0.

Un crash **nativo** no aparece como `FATAL EXCEPTION`: el proceso muere y solo queda un
`Abort message:` en el logcat. Buscar únicamente `FATAL EXCEPTION` los deja pasar.
