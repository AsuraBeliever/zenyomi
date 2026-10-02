# ADR-0008 — El torrent llega como complemento aparte, con TorrServer dentro

**Fecha:** 2026-10-01 · **Estado:** aceptada · **Tarea:** KAN-30 (épica KAN-29)

## Contexto

El cliente quiere ver anime que las fuentes entregan como torrent (KAN-14). Para
reproducir un torrent hace falta un motor que lo descargue por trozos, en orden, y lo
sirva como un stream que mpv pueda abrir. Aniyomi lo resolvió en `main` en julio de 2026
(#2346, #2361, sin release todavía) con **TorrServer**, metido en la app a través de
`io.github.secozzi:torrserver`.

Tres hechos medidos condicionan cómo traerlo aquí:

1. **Licencia.** TorrServer (`YouROK/TorrServer`) es **GPL-3.0**, y el envoltorio que usa
   Aniyomi también (lo dice su POM). Enlazado dentro del proceso de la app, el APK sería
   una obra combinada que habría que distribuir bajo GPL-3.0. Zenyomi es Apache-2.0
   (ADR-0002).
2. **Binario.** TorrServer publica ejecutables para Android (`TorrServer-android-arm64`,
   `-arm7`, `-amd64` y `-386`; versión MatriX.145.1, del 2026-09-30). Son PIE, compilados
   con el NDK r29 para Android 21 o superior, ya alineados a 16 KB, y solo dependen de
   `libc`, `libdl` y `liblog`. Lanzado a mano en el emulador (Android 17), arranca y su
   `/echo` contesta la versión.
3. **Tamaño.** Cada binario ocupa **66 MB**, unos 23 MB comprimido. Zenyomi guarda sus
   librerías nativas sin comprimir dentro del APK (`extractNativeLibs=false`, como Mihon),
   y Android solo deja ejecutar código que esté en el `nativeLibraryDir` de la app (W^X:
   nada que la app pueda escribir es ejecutable).

## Opciones

| | Descarga del APK | Instalado | Quien no usa torrent |
|---|---|---|---|
| A. TorrServer dentro, sin comprimir | 97 → 163 MB | 163 MB | carga 66 MB que no usa |
| B. Comprimir todas las librerías (`useLegacyPackaging`) | 97 → ~76 MB | ~217 MB | carga ~120 MB instalados, y la app se aparta de cómo empaqueta Mihon |
| C. **Complemento aparte, instalado a demanda** | 97 MB, sin cambios | +~90 MB solo si se activa | nada |
| D. Librería GPL de Aniyomi dentro del proceso | parecida a A | parecida a A | además, toda la app pasa a GPL-3.0 |

## Decisión

**C.** El torrent vive en un APK propio, **`app.zenyomi.torrent`** (el «complemento»),
que contiene el ejecutable de TorrServer y un servicio que lo arranca. Zenyomi lo
descarga e instala desde la propia app la primera vez que se activa el torrent, con el
mismo instalador que usan las extensiones. Después se comunican así:

- **Arranque:** Zenyomi se **enlaza** al servicio del complemento (`bindService` con un
  intent explícito) mientras lo necesita: reproduciendo o descargando un episodio torrent.
  Un servicio enlazado no sufre las restricciones de Android 12+ a lanzar servicios en
  primer plano desde otra app, y el enlace mantiene vivo el proceso.
- **Datos:** por HTTP en `127.0.0.1`, con la API de TorrServer: añadir el torrent, ver sus
  ficheros y pedir la URL de streaming que se le pasa a mpv.
- **Confianza:** antes de enlazarse, Zenyomi comprueba que el complemento instalado está
  firmado con **la clave del proyecto** (el mismo SHA-256 que la app). Un paquete con ese
  nombre y otra firma se ignora.
- **Visibilidad:** Zenyomi declara `app.zenyomi.torrent` en `<queries>`. Sin eso, desde
  Android 11 no puede ni ver que está instalado.

Dentro del complemento:

- El ejecutable va en `jniLibs` como `libtorrserver.so`, que es la única forma de que
  Android lo instale en el `nativeLibraryDir`, y el complemento lo lanza con
  `ProcessBuilder` en un puerto libre y con sus datos en `cacheDir`.
- El complemento sí comprime sus librerías (`extractNativeLibs=true`): pesa unos 23 MB en
  descarga, y lo que se instala solo lo paga quien lo usa.
- Se firma con la clave del proyecto y se publica como un asset más de cada release de
  Zenyomi (`zenyomi-torrent-<abi>-v<versión>.apk`).

## Licencia

El complemento es una obra aparte que contiene un programa GPL-3.0 y se distribuye bajo
**GPL-3.0**. Zenyomi no enlaza nada de TorrServer: se comunica con otro programa por un
socket, que la propia FSF pone como ejemplo de comunicación entre programas separados. Por
eso **Zenyomi sigue siendo Apache-2.0**.

Lo que obliga la GPL con el complemento, y se cumple:

- su `LICENSE` es GPL-3.0, y la pantalla «Acerca de» del complemento lo dice;
- se indica el código fuente exacto del binario: `YouROK/TorrServer`, tag `MatriX.145.1`;
- el código del propio complemento (el servicio que lanza el binario) se publica en este
  repositorio, en su módulo, también bajo GPL-3.0;
- `docs/FORK_COMPLIANCE.md` y `NOTICE` lo registran.

## Consecuencias

- La app principal no crece, y nada cambia para quien no activa el torrent.
- Activarlo cuesta una descarga de unos 23 MB y una confirmación de instalación de
  Android, una sola vez.
- Hay un APK más que firmar, publicar y mantener al día con TorrServer. Actualizar
  TorrServer es cambiar el binario y subir el `versionCode` del complemento; Zenyomi
  avisa cuando el instalado es más viejo que el que espera.
- Lo que Aniyomi hizo bien se porta igual: el parser de bencode con sus tests, la API de
  TorrServer, cómo se elige el fichero del episodio dentro de un torrent de varios y los
  trackers por defecto. Lo que no se porta es la librería GPL enlazada.

## Alternativas descartadas

- **A y B**: ver la tabla. A le cobra 66 MB a todo el mundo por una función opcional; B
  reduce la descarga pero casi duplica lo instalado, y cambia una decisión de empaquetado
  de Mihon que afecta a toda la app.
- **D**: es lo que hizo Aniyomi. Integración más directa, a cambio de relicenciar toda la
  app como GPL-3.0, cosa que el ADR-0002 no permite decidir de pasada.
- **Pedir al usuario que instale la app oficial de TorrServer.** No hace falta: el
  complemento ofrece lo mismo, se instala desde Zenyomi y se sabe qué versión hay.
- **Motor propio sobre libtorrent (BSD).** Sin problema de licencia, pero son semanas de
  trabajo y un servidor de streaming que mantener, para llegar a lo que TorrServer ya hace.
