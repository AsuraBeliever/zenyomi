---
name: zen-test
description: Paso 4 de zen-flow. Prueba un cambio de Zenyomi por partida doble. Backend con spotless, tests de unidad y migraciones; visual instalando en el emulador, navegando con adb, capturando pantallas y revisándolas, buscando crashes en logcat y pasando la regresión de manga. Deja la evidencia en Jira.
argument-hint: KAN-n
---

# zen-test — backend y visual

Tarea: $ARGUMENTS

Lee el «Hecho cuando» de la tarea: cada criterio tiene que acabar con una evidencia.
Procedimiento completo y fixtures: `docs/TESTING.md`. **Solo en el emulador**: el celular
del cliente no se toca salvo que él lo pida.

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
OUT=<scratchpad>/zen-test/KAN-n     # capturas y logs; no van al repo
```

## A. Backend

Todo en verde o no se sigue:

1. `./gradlew spotlessCheck`
2. Tests de los módulos tocados: `git diff --name-only develop...HEAD` → por cada módulo,
   `./gradlew :<módulo>:testDebugUnitTest` (o `:<módulo>:test` si es JVM puro). Si se tocó
   `app`, `:app:testDebugUnitTest`.
3. Si cambió algún `.sq` o `.sqm`: `./gradlew verifySqlDelightMigration`.
4. Si el cambio toca reflexión, JNI, `proguard-rules`, la carga de extensiones o el player
   nativo, compila también release (`./gradlew :app:assembleRelease`). R8 solo actúa ahí,
   y ya rompió el player dos veces (ver `docs/TESTING.md`).

Un test que falla se arregla, volviendo a `zen-implement`. No se desactiva ni se salta.

## B. Visual, en el emulador

### Arrancar

```sh
adb -s emulator-5554 get-state 2>/dev/null || {
  ANDROID_AVD_HOME=$HOME/.config/.android/avd emulator -avd Pixel_10_Pro_XL -no-snapshot-save -no-boot-anim &
  adb -s emulator-5554 wait-for-device
  until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
}
```

Lanza el emulador con `run_in_background`, porque es un proceso largo.

### Instalar y abrir

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell monkey -p app.zenyomi.dev -c android.intent.category.LAUNCHER 1
```

Si falla con «signatures do not match», sigue la receta de `docs/TESTING.md`, que conserva
los datos. No desinstales a ciegas.

### Navegar y capturar

Para cada criterio del «Hecho cuando», lleva la app al estado que lo demuestra y captura:

```sh
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml && adb -s emulator-5554 pull /sdcard/ui.xml $OUT/ui.xml
# bounds="[x1,y1][x2,y2]" del nodo por text/content-desc → centro → tap
adb -s emulator-5554 shell input tap <x> <y>
adb -s emulator-5554 exec-out screencap -p > $OUT/<n>-<qué>.png
```

**Abre cada captura con Read y mírala de verdad**: textos, que no haya nada cortado ni
solapado, que el tema sea coherente y que el estado sea el esperado. Que una captura exista
no prueba nada.

Para el player, usa los vídeos con código de tiempo grabado de `docs/TESTING.md`. La
posición se comprueba leyendo el fotograma, no fiándose de la barra.

Lo que adb no puede probar, como el doble toque, se declara como **no verificado** en el
informe. Nunca se da por bueno.

### Crashes

```sh
adb -s emulator-5554 logcat -d > $OUT/logcat.txt
grep -cE 'Fatal signal|FATAL EXCEPTION|NoSuchMethodError|Abort message' $OUT/logcat.txt   # debe dar 0
```

### Regresión de manga (siempre)

Es la regla dura 1, así que se pasa aunque el cambio sea solo de anime. Abre la biblioteca
de manga y comprueba que carga con portadas, abre un manga y que la lista de capítulos sale,
y abre un capítulo y que se lee. Una captura de cada.

Se hace con la fixture de manga de la fuente local (`Documents/local/` del emulador, ver
`docs/TESTING.md`). Si la biblioteca de manga está vacía y no hay fixture, la regresión
**no está pasada**: el informe lo dice así, y se monta la fixture antes de mergear nada
que toque código compartido de manga.

## C. Informe

Comentario en Jira (`addCommentToJiraIssue`) y el mismo texto guardado en
`$OUT/informe.md`, que `zen-pr` copia al PR:

```markdown
**Pruebas — <sha corto>**

Backend: spotlessCheck ✅ · tests <módulos> ✅ (<n> tests, <n> nuevos) · migraciones ✅/n.a. · release ✅/n.a.

Emulador (Pixel 10 Pro XL, Android 17):
1. <criterio 1> → ✅ <lo que se vio, con el dato concreto: tiempo, texto, contador…>
2. <criterio 2> → ✅ …
Regresión manga: biblioteca ✅ · capítulos ✅ · lectura ✅
Crashes en logcat: 0
No verificable por adb: <lo que sea, o «nada»>
```

Si algo sale ❌, no hay informe final: vuelve a `zen-implement` con lo que falló.
