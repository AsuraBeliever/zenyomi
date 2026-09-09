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
5. Probar en dispositivo con la checklist de humo de `docs/TESTING.md`.
6. Mergear `develop` en `main`.
7. Tagear `v<version>` y empujar el tag: el workflow `Release` compila, firma y crea
   la release en GitHub.

## Verificar una release antes de anunciarla

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

Las builds de debug usan el applicationId `app.zenyomi.dev`, así que conviven con las
de release (`app.zenyomi`) sin desinstalar nada. Entre releases, la actualización es
limpia mientras no cambie la clave.
