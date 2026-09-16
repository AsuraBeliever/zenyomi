# Zenyomi fuera del móvil — plan de escritorio y sincronización

**Escrito:** 2026-09-16
**Estado:** propuesta, pendiente de decisión del cliente
**Decide:** Alan. Cuando elija forma, se escribe el ADR correspondiente en `docs/adr/`.

Llevar la app al PC y mantener los datos iguales en las dos máquinas, sin cuentas y sin nube.
Esto es lo que hacen otros proyectos que ya lo han resuelto, lo que este repositorio ya tiene
hecho sin saberlo, y en qué orden conviene atacarlo para no descubrir tarde lo que se puede
saber en unos días.

Restricciones que pone el cliente, tal cual: sin cuentas ni logins, sin base de datos en la
nube, todo local en cada máquina, y un servicio aparte solo para el sync.

---

## 1. Lo que hay que construir son dos cosas, no una

«Zenyomi en el PC» y «los datos iguales en los dos sitios» parecen la misma tarea y no lo son.
Tienen tamaños distintos, riesgos distintos y — esto es lo importante — se pueden hacer por
separado y en el orden que más convenga.

El sync hace falta digas lo que digas del escritorio, cuesta semanas y se puede empezar mañana.
El escritorio cuesta meses y su tamaño depende de una incógnita técnica que todavía no está
resuelta. De ahí sale el plan de la sección 4.

---

## 2. Cómo lo resuelven los que ya lo han resuelto

Siete proyectos de código abierto que atacan exactamente estos dos problemas. Ninguno los ataca
los dos a la vez, y eso también dice algo.

| Proyecto | Qué resuelve | Cómo | Qué nos sirve |
|---|---|---|---|
| **Suwayomi-Server** | Tachiyomi en el escritorio | Convierte el APK de la extensión a JAR con `dex2jar`, parchea el bytecode con ASM y le entrega un `Context` de Android falso. Para Cloudflare, un Chromium embebido | **Nuestro problema de extensiones, ya resuelto** — pero solo para manga |
| **SyncYomi** | Progreso de lectura entre móviles | Servicio en Go con SQLite embebida, API REST, autohospedado. Sin cuenta: una clave de API que pegas en cada dispositivo | La forma de servicio que se pide, en nuestro mismo dominio |
| **Kotatsu sync server** | Lo mismo, para otro lector | Kotlin y Ktor, autohospedable, pero con **login de verdad**: correo, contraseña y JWT | La forma sí, el login no. Nos enseña qué evitar |
| **Logseq (modo local)** | Sincronizar sin cuenta | Una sola clave precompartida sustituye a todo el sistema de identidad. Emparejar un dispositivo es escanear un QR | **El patrón exacto de «sin cuentas»**, ya en producción |
| **Syncthing** | Ficheros entre máquinas | Igual a igual, identidades de dispositivo, sin servidor central. Descubrimiento local si no quieres salir de tu red | Para las descargas, que son gigas. No para la base de datos |
| **Anki** | Décadas haciendo esto | SQLite local y un servidor de sync *dentro del propio binario*. El servidor nunca manda: es un buzón | La disciplina. Local-first de verdad, no «offline a ratos» |
| **Obsidian LiveSync** | Notas entre dispositivos | Cifrado de extremo a extremo, y una variante que va directa entre dispositivos por WebRTC | Referencia por si algún día el servicio sale de la red local |

Fuentes al final del documento.

---

## 3. Inventario: lo que ya tenemos y lo que nos falta

Antes de estimar nada conviene mirar qué hay en el repositorio. Hay una sorpresa buena y una
mala, y las dos cambian el plan.

### A favor

**El esquema ya está preparado para sincronizar.** Las tablas `mangas`, `chapters`, `animes` y
`episodes` llevan tres columnas — `last_modified_at`, `version` e `is_syncing` — con triggers
que las mantienen solas. Cada cambio local sube un contador; `is_syncing` existe para marcar
«esto lo estoy escribiendo yo desde fuera, no lo cuentes como cambio del usuario». Es herencia
de Tachiyomi y **nunca se ha usado para nada**. La parte más aburrida y más fácil de equivocar
del sync está hecha.

```
data/src/main/sqldelight/tachiyomi/data/mangas.sq       update_manga_version
data/src/main/sqldelight/tachiyomi/data/chapters.sq     update_chapter_and_manga_version
data/src/main/sqldelightanime/dataanime/animes.sq       update_anime_version
data/src/main/sqldelightanime/dataanime/episodes.sq     update_episode_and_anime_version
```

**El backup ya define qué se sincroniza.** El `.tachibk` lleva manga, anime, categorías,
historial, tracking y preferencias, y ya está probado de ida y vuelta. Un sync es ese mismo
contenido, troceado y con fechas.

**El build ya sabe de multiplataforma.** Existe un convention plugin de Kotlin Multiplatform
(`gradle/build-logic/src/main/kotlin/PluginKotlinMultiplatform.kt`) e `i18n` ya lo usa. Añadir
un objetivo de escritorio es tocar un fichero, no inventar el andamiaje.

**mpv es el mismo en las dos máquinas.** El reproductor del móvil usa libmpv, que es exactamente
la librería que usa cualquier reproductor de escritorio de Linux. El trabajo del player no se
tira.

### En contra

**Media app es Android puro.** De los 1.155 ficheros Kotlin del proyecto, **576 importan
Android**. El módulo `app` — toda la interfaz, el lector, los ajustes — son 730 él solo. Eso no
se «porta»: se reescribe o se rodea.

**Suwayomi no soporta extensiones de anime.** Es la sorpresa mala. La parte de manga está
resuelta por otros y es reutilizable; la de anime es una petición abierta que nadie ha
implementado. **Esa mitad sería nuestra.**

**Mihon descartó el escritorio.** La petición de port a Windows y Linux (`mihonapp/mihon#167`)
está cerrada como *not planned*. No hay upstream al que seguir ni del que heredar arreglos:
sería código solo nuestro, y hay que mantenerlo cada vez que Mihon se mueva.

**El ecosistema de anime ya está mal.** De 24 fuentes medidas, 4 llegan a reproducir vídeo (ver
`EXTENSIONS_STATUS.md`). Un escritorio que navegue fuentes hereda ese problema; no lo arregla.
Lo que ya está descargado, en cambio, funciona para siempre.

---

## 4. El plan: tres fases, en este orden y por este motivo

El orden no es cronológico por capricho: cada fase existe para que la siguiente se pueda
dimensionar con datos en vez de con suposiciones.

### Fase 1 — La prueba que decide el tamaño de todo (días)

Coger el APK de una extensión de anime que sabemos que funciona — KickAssAnime o AnimeOnsen, las
dos reproducen hoy — montarlo sobre la capa de compatibilidad de Suwayomi en un proyecto Kotlin
pelado, y pedirle que resuelva la URL de vídeo de un episodio. Sin interfaz, sin base de datos:
un `main()` que imprima una URL y salga.

**Por qué esto va primero.** Es la única incógnita que cambia el tamaño del proyecto por un
orden de magnitud, y se resuelve en días.

- **Si la extensión resuelve el vídeo:** el escritorio puede navegar fuentes y descargar por su
  cuenta. Forma B de la fase 3.
- **Si no:** el escritorio es biblioteca y reproducción de lo ya descargado — que sigue siendo
  útil — y nos hemos ahorrado meses de descubrirlo por el camino caro. Forma C.

**Nota de licencia, que aquí importa.** La capa de compatibilidad de Suwayomi (`AndroidCompat`,
originalmente de TachiWeb-Server) es Apache-2.0, igual que nosotros: se puede usar sin más. El
resto del proyecto es MPL-2.0, que es copyleft por fichero: podemos depender de él, pero
cualquier fichero que copiemos sigue siendo MPL y hay que anotarlo en `FORK_COMPLIANCE.md` antes
de publicar nada. Ver la regla 4 del charter y `docs/adr/0002-licencia.md`.

### Fase 2 — El sync (semanas)

Hace falta diga lo que diga la fase 1, y da valor por sí solo desde el primer día: es también
una copia de seguridad continua, que hoy no existe. Arranca en cuanto la prueba esté lanzada.

#### Qué hay que añadir a la base de datos

**Identidad estable.** El `_id` de cada fila es un autoincremento local: el mismo anime tiene
números distintos en cada máquina. La clave de verdad es el par fuente + URL, y hay que empezar
a tratarla como tal.

**Lápidas.** Hoy un borrado no deja rastro. Si borras un anime en el móvil, el PC se lo vuelve a
mandar y reaparece. Hace falta una tabla que recuerde qué se borró y cuándo.

#### Las reglas de fusión

Esto no es un detalle técnico: es cómo se va a comportar la app cuando los dos dispositivos
digan cosas distintas, y conviene que lo juzgue el cliente.

1. **Progreso: gana el más avanzado, no el más reciente.** Si el móvil va por el episodio 12 y
   el PC por el 3, lo correcto es 12 — aunque el PC haya escrito después. Un sync que «respeta
   la última escritura» te borra media temporada.
2. **Favorito, categorías, notas y banderas: gana el más reciente.** Aquí sí es una opinión que
   cambia el usuario, y la última vale.
3. **Borrado: gana si es posterior al último cambio.** Borrar algo y que reaparezca solo es de
   las cosas que más desconfianza generan.

Al aplicar lo que llega de fuera se pone `is_syncing = 1`, para que los triggers no cuenten esa
escritura como cambio local. La columna existe exactamente para esto.

#### El servicio

Kotlin y Ktor — el mismo lenguaje que todo lo demás, una sola cadena de herramientas — con
SQLite embebida, en un contenedor y un puerto. Sin cuentas: una clave precompartida, la misma en
los dos dispositivos. Emparejar es pegar dirección y clave, o escanear un QR que lleve las dos
cosas.

Corre en el PC o donde se quiera. **Nunca es la fuente de verdad**: si está apagado, las dos
apps siguen funcionando enteras y sincronizan cuando vuelva. Dentro de la red local, texto plano
basta; si algún día sale fuera, va detrás de una VPN — no nos inventamos criptografía.

#### Lo que no se sincroniza

Vídeos y capítulos descargados, los APK de las extensiones y las portadas en caché. Son gigas y
se pueden volver a obtener. Si algún día se quieren los ficheros en las dos máquinas, eso es
Syncthing sobre la carpeta de descargas, no este servicio.

### Fase 3 — El escritorio (meses)

Tres formas posibles. La fase 1 tacha una de ellas y la decisión se toma con la respuesta en la
mano, no ahora.

| Forma | Qué es | Qué da | Coste |
|---|---|---|---|
| **A · Port completo** | Zenyomi entero en el PC: biblioteca, fuentes, lector, player, descargas y ajustes, con Compose Multiplatform | Todo, en las dos máquinas, idéntico | **El mayor.** Reescribir los 730 ficheros de interfaz, y sustituir notificaciones, tareas en segundo plano, acceso a ficheros y WebView. Más grande que todo el porte de anime |
| **B · Núcleo y interfaz fina** | Un proceso sin interfaz en el PC que carga extensiones y descarga, más una interfaz de escritorio encima. El modelo de Suwayomi | Navegar fuentes, descargar y ver en el PC. El móvil sigue entero y autónomo | **Alto.** No hay que portar la interfaz de Android, pero sí escribir una nueva y resolver las extensiones de anime en la JVM |
| **C · Biblioteca y reproducción** | Una app de escritorio que no carga extensiones: enseña la biblioteca sincronizada, reproduce y lee lo que ya está descargado, y devuelve el progreso | Descargas en el móvil, ves en el PC, el progreso cuadra. Funciona aunque la prueba de la fase 1 salga mal | **El menor.** Y es un subconjunto estricto de B: nada de lo que se haga aquí se tira si luego se sigue |

**Recomendación: C primero, B como destino.** C es alcanzable, se apoya entero en el sync de la
fase 2 y no depende de que las extensiones corran en el PC. Si la prueba de la fase 1 sale bien,
B se construye encima sin tirar nada. La forma A solo tiene sentido si algún día el objetivo es
que el móvil desaparezca, y no parece el caso.

---

## 5. Lo que sigue teniendo que decidir un humano

| # | Pregunta | Por qué importa | Recomendación |
|---|---|---|---|
| 1 | ¿El escritorio tiene que navegar fuentes, o basta con ver lo descargado? | Es la que más cambia el presupuesto. Si vale con descargar en el móvil y ver en el PC, el escritorio es cuestión de semanas en vez de meses | Empezar por «ver lo descargado» y ampliar si la prueba lo permite |
| 2 | ¿Solo Linux, o también Windows? | Mantener las dos multiplica el trabajo de empaquetado y de CI, sobre todo con mpv y sus librerías nativas de por medio | Linux primero; Windows solo si se va a usar |
| 3 | ¿Dónde vive el servicio de sync? | Si vive en el PC, el móvil solo sincroniza cuando el PC está encendido — que para dos dispositivos es razonable. Un NAS lo tiene siempre disponible | En el PC, en un contenedor. Mover el servicio después es trivial |
| 4 | ¿Sincronizar también fuera de la red local? | Poder sincronizar desde la calle pide una VPN o exponer un puerto, y ahí sí conviene cifrado de extremo a extremo | Red local primero; VPN después si hace falta |

---

## 6. Nada de esto toca lo que ya funciona

La regla 1 del charter sigue igual: no se pierde nada de Mihon. El sync se añade sobre columnas
que ya existen y que hoy no lee nadie, y el escritorio es una aplicación nueva **al lado** de la
de Android, no en lugar de ella. Si mañana se para todo esto, la app del móvil se queda
exactamente como está hoy.

---

## Fuentes

- [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) — Tachiyomi para escritorio, y su capa de compatibilidad con Android
- [Arquitectura de Suwayomi-Server](https://deepwiki.com/Suwayomi/Suwayomi-Server/2-architecture) — cómo convierte y ejecuta los APK
- [Petición de extensiones de Aniyomi](https://github.com/Suwayomi/Tachidesk-Server/issues/387) — abierta, sin implementar
- [SyncYomi](https://github.com/syncyomi/syncyomi) — servicio de sync autohospedado con clave de API
- [Kotatsu sync server](https://github.com/KotatsuApp/kotatsu-syncserver) — el mismo problema, resuelto con login
- [Modo de sync local de Logseq](https://github.com/logseq/logseq/pull/12919) — clave precompartida y emparejamiento por QR
- [Mihon #167](https://github.com/mihonapp/mihon/issues/167) — port a escritorio, cerrado como *not planned*
- [libmpv desde Java](https://github.com/mpv-player/mpv-examples/tree/master/libmpv/java) — ejemplo oficial de mpv en la JVM
