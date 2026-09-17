# Publicar una release

## La clave de firma

Android identifica una app por `applicationId` **más la clave con la que se firmó**.
Si la clave cambia, la actualización no se instala encima: hay que desinstalar, y el
usuario pierde su biblioteca.

| | |
|---|---|
| Keystore | `~/.zenyomi-signing/zenyomi-release.jks` |
| Alias | `zenyomi` |
| Algoritmo | RSA 4096, válido hasta 2054 |
| Credenciales | `~/.zenyomi-signing/credentials.txt` (permisos 600) |
| Copia en el proyecto | `signing-key-backup/` — **gitignored**, ver `LEEME.txt` dentro |
| SHA-256 | `24:5E:D5:4C:43:F4:46:90:89:91:4E:FB:5B:CD:47:08:BF:19:20:1A:C0:ED:23:50:4B:EB:F3:FF:8D:04:5D:BF` |

> **Si se pierden el `.jks` y su contraseña, el proyecto no puede volver a publicar
> una actualización instalable.** La única salida sería cambiar de `applicationId`,
> y todos los usuarios perderían sus datos. Debe existir una copia fuera de este equipo.

Ni el keystore, ni `keystore.properties`, ni `signing-key-backup/` entran en git: el
`.gitignore` bloquea además `*.jks` y `*.keystore`. Como el repositorio es **público**,
esto se comprueba antes de cualquier push que toque el `.gitignore`:

```sh
git check-ignore -v signing-key-backup/zenyomi-release.jks   # debe responder
git add -A && git status --porcelain | grep -i jks           # no debe devolver nada
```

Filtrar la clave es tan grave como perderla: permitiría a cualquiera firmar un APK que
los teléfonos aceptarían como actualización legítima de Zenyomi. En CI la clave
viaja como secretos del repositorio: `SIGNING_KEY` (el `.jks` en base64),
`KEY_STORE_PASSWORD`, `ALIAS` y `KEY_PASSWORD`.

Nota: `app/build.gradle.kts` firma el build type `release` con el `signingConfig`
llamado `debug`. Es la convención heredada de Mihon, no un error: ese config se
alimenta del keystore real, no de la clave de depuración de Android.

## Proceso

1. Cerrar el trabajo en `develop` y comprobar que el CI está verde.
2. Subir `versionCode` y `versionName` en `app/build.gradle.kts`.
3. Añadir la entrada en `CHANGELOG.md` y en `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
4. **Auditar el cumplimiento como fork** — ver `docs/FORK_COMPLIANCE.md`.
5. **Pasar el arnés de extensiones** y regenerar `anime-source-health.json` — ver
   `docs/EXTENSIONS_STATUS.md`. Lo que la app marca como roto lleva fecha, y una fecha
   vieja engaña más que no poner nada.
6. Probar en dispositivo con la checklist de humo de `docs/TESTING.md`.
7. Mergear `develop` en `main`.
8. Tagear `v<version>` y empujar el tag: el workflow `Release` compila, firma y crea
   la release en GitHub.

## Cuando el workflow `Release` falla y el código está bien

Ha pasado dos veces, y las dos eran del entorno, no nuestras. Antes de tocar nada,
**compilar en local**: si `./gradlew assembleRelease` y `assembleFoss` pasan, el problema
está fuera.

| Síntoma | Qué era | Qué hacer |
|---|---|---|
| `:app:packageFoss FAILED` sin causa en el log | El runner empaquetando 5 APK, uno de 277 MB | Relanzar el job |
| `Could not find flexible-adapter-<rev>.jar` | JitPack sirviendo el POM a medias. El artefacto es un **`.aar`**, no un `.jar`: si Gradle pide un `.jar` es que recibió un POM sin `packaging` y asumió el valor por defecto | Comprobar que JitPack sirve las dos cosas y relanzar |

```sh
B=https://www.jitpack.io/com/github/arkon/FlexibleAdapter/flexible-adapter/<rev>/flexible-adapter-<rev>
curl -s -o /dev/null -w "pom %{http_code}\n" $B.pom
curl -s -o /dev/null -w "aar %{http_code}\n" $B.aar   # 200 en ambos = relanzar y listo
gh run rerun <run-id> --failed
```

El build local pasa aunque JitPack esté caído porque la dependencia ya está en
`~/.gradle/caches`; el CI corre con `cache-disabled: true` y resuelve de cero cada vez.
Esa diferencia es la que hace que parezca un fallo nuestro sin serlo.

**Actualización 2026-09-15 (v0.13.0): hicieron falta cuatro intentos.** El
`Could not find flexible-adapter-<rev>.jar` volvió, y relanzar una vez no bastó. El
tercer intento dio además un síntoma distinto —`Could not find
com.github.arkon.FlexibleAdapter:flexible-adapter:c8013533`, el módulo entero— lo que
confirma que es JitPack sirviendo mal y no un POM concreto: mientras tanto,
`curl` desde aquí devolvía 200 para el `.pom` **y** el `.aar`, y el `.pom` traía su
`<packaging>aar</packaging>`.

Antes de relanzar por tercera vez se comprobó que la culpa era de fuera, que es lo
que dice el apartado de arriba: `./gradlew :app:mergeFossNativeLibs
--refresh-dependencies` pasa en local. `--refresh-dependencies` re-descarga los
metadatos, así que la prueba no se apoya en el POM que ya estuviera en caché.

**Solo le pega al job FOSS**, y no es casualidad: es el único con
`cache-disabled: true`, así que resuelve de cero cada vez. `Build` compiló a la
primera en las cuatro tentativas. Si vuelve a pasar: relanzar, y darle unos minutos
entre intentos.

**Desde el 2026-09-15 esto se arregla solo, y hay dos cambios detrás.**

1. **El job FOSS vuelve a usar la caché de dependencias.** El `cache-disabled: true` venía
   de Mihon, no era una decisión nuestra, y era justo lo que hacía que ese job resolviera
   las once dependencias de JitPack de cero en cada ejecución. Es el único job que ha
   fallado nunca por esto: `Build`, que sí cachea, no ha fallado una sola vez. Un tag no es
   la rama por defecto, así que `setup-gradle` deja la caché en solo lectura y no hay
   carrera de escritura con el `Build` que corre en paralelo.

2. **Las dos compilaciones reintentan solas**, vía `.github/scripts/gradle-with-retry.sh`:
   tres intentos con un minuto de espera. El reintento está **condicionado al propio error
   de resolución** (`Could not find/resolve/GET/HEAD`, `Failed to transform`); cualquier
   otro fallo sale a la primera. Reintentar un error de compilación tres veces convertiría
   un rojo de dos minutos en uno de diez y enseñaría a todo el mundo a ignorarlo.

Lo que queda para la persona: si aun así agota los tres intentos, el propio log lo dice y
apunta a JitPack. Entonces sí, esperar y relanzar.

> Nota honesta: la lógica de reintento está probada —éxito, fallo de resolución
> persistente, error de compilación y fallo-que-se-recupera— pero **el arreglo entero no se
> habrá demostrado hasta la siguiente release de verdad**, porque JitPack no se puede
> romper a voluntad.

**Comprobado en la v0.13.1.** Primera release con el arreglo puesto, y salió a la primera:
el log del job FOSS dice `Entries: 9 restored (1481Mb), 0 saved` y `Cache is read-only`,
que es exactamente el mecanismo descrito arriba — las dependencias salieron de la caché en
vez de pedírselas a JitPack, y sin carrera de escritura con el `Build` de al lado.

Lo que **sigue sin demostrarse** es el reintento: haría falta que JitPack fallara con la
caché fría, y eso no se puede provocar.

## `Headers Timeout Error` subiendo los APK — arreglado, y dos trampas por el camino

Pasó en la v0.17.0 y costó cuatro intentos. Las dos compilaciones pasaron siempre; lo que
fallaba era **la subida**: seis APK, unos 800 MB, que el action subía **en paralelo** en un
solo step, y GitHub cortaba la conexión a los dieciséis minutos con
`##[error]Headers Timeout Error`.

**Trampa 1: `--failed` es el reintento equivocado.** `gh run rerun <id> --failed` relanza solo
el job de release, en un runner nuevo que no tiene los APK que compilaron los otros dos. El
action no encuentra ningún fichero, lo dice en voz baja —`does not include a valid file`— y
aun así **finaliza la release**: la saca de borrador y la publica **vacía**, en verde.

**Trampa 2: reintentar no es acumulativo, es destructivo.** El action borra los assets de la
release antes de volver a subirlos, así que un intento que se corta deja **menos** ficheros que
el anterior. En la v0.17.0 se vio ir de 5 assets a 3, perdiendo por el camino el `arm64-v8a`
que ya estaba bien subido.

**El arreglo.** El action ya no recibe `files`: solo crea la release y sus notas. Los APK los
sube un step aparte con `gh release upload --clobber`, **uno a uno y con tres intentos cada
uno**. Un fichero flojo cuesta ahora otro intento de ese fichero, no la release entera, y
`--clobber` hace que relanzar el workflow reemplace en vez de chocar.

```sh
gh run rerun <run-id>          # el workflow entero, nunca --failed
gh release view v<version> --json assets --jq '.assets | length'   # debe dar 6
```

Comprobar el número de assets antes de dar una release por buena no es opcional: que el job
diga `success` no significa que haya subido nada.

## Verificar una release antes de anunciarla

Sobre el APK **que publicó el CI**, no sobre el que compilaste tú. Son binarios
distintos: el del CI se firma con los secretos del repositorio, y si esa clave no
fuese la misma, la actualización no se instalaría encima y el usuario perdería su
biblioteca. El `SHA-256` de abajo es lo que lo demuestra.

```sh
gh release download v<version> -p "zenyomi-arm64-v8a-v<version>.apk" -D /tmp
```


```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
APK=app/build/outputs/apk/release/app-arm64-v8a-release.apk

# firmada con nuestra clave (el SHA-256 debe coincidir con la tabla de arriba)
$ANDROID_HOME/build-tools/*/apksigner verify --print-certs "$APK"

# identidad correcta
$ANDROID_HOME/build-tools/*/aapt2 dump badging "$APK" | head -2

# sin marca ajena
$ANDROID_HOME/build-tools/*/aapt2 dump resources "$APK" | grep -c ic_mihon   # debe dar 0
```

## Historial de versiones

| Versión | versionCode | Firma | Nota |
|---|---|---|---|
| 0.1.0 | 1 | debug (desechable) | Línea base, sin publicar |
| 0.1.1 | 2 | debug (desechable) | Retirada la marca de Mihon; sin publicar |
| 0.1.2 | 3 | **clave del proyecto** | Primera release publicada |
| 0.13.0 | 23 | **clave del proyecto** | Kitsu con anime. Cuatro intentos por JitPack |
| 0.13.1 | 24 | **clave del proyecto** | Icono nuevo. **Un solo intento**: el job FOSS restauró 9 entradas de caché (1481 MB) |
| 0.14.0 | 25 | **clave del proyecto** | Paridad anime↔manga. Primera migración de la BD de anime. Un solo intento |
| 0.14.1 | 26 | **clave del proyecto** | Reapertura instantánea + datos de extensiones frescos. Un solo intento |
| 0.14.2 | 27 | **clave del proyecto** | Se elimina una vuelta de red por episodio. Reflexión: verificada contra R8. Un solo intento |
| 0.14.3 | 28 | **clave del proyecto** | Descarga de anime: «el siguiente» contaba desde el final. Un solo intento. **La verificación del APK publicado encontró un segundo fallo en el mismo arreglo** |
| 0.14.4 | 29 | — | **Nunca se publicó.** Preparada en `develop` y verde en CI, pero no llegó a `main` ni a tag; su contenido sale en la 0.15.0 |
| 0.15.0 | 30 | **clave del proyecto** | Las descargas de anime guardaban el m3u8 en vez del vídeo. Incluye lo de la 0.14.4 |
| 0.16.0 | 31 | **clave del proyecto** | Velocidad y tamaño en las descargas, y elegir calidad. Prueba de humo hecha en el dispositivo del cliente |
| 0.17.0 | 32 | **clave del proyecto** | Descargas ~3× más rápidas, el botón responde al instante, y el anime aparece en la cola de descargas. Probada en el emulador por indicación del cliente |

Las builds de debug usan el applicationId `app.zenyomi.dev`, así que conviven con las
de release (`app.zenyomi`) sin desinstalar nada. Entre releases, la actualización es
limpia mientras no cambie la clave.
