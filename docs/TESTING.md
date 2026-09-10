# Testing en dispositivo real

Todo build se prueba en el celular del cliente por **wireless debugging** (ADB
sobre Wi-Fi). No se entrega nada sin haberlo instalado y abierto antes.

## Estado de la conexión

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
lo insertado. El emulador conserva ahora una entrada `Anime de prueba (fixture)` con tres
episodios.

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
