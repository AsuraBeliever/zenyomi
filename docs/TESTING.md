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
