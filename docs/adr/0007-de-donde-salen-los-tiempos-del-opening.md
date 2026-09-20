# ADR-0007 — De dónde salen los tiempos del opening

**Fecha:** 2026-09-18 · **Estado:** aceptada
**Revisada:** 2026-09-19 — cómo se identifica el anime ante AniSkip, y el botón deja de
ofrecerse cuando nadie sabe dónde está el opening

## Contexto

Ver una temporada es ver el mismo minuto y medio de opening en cada episodio. El
botón para saltarlo tiene que saber **dónde empieza y dónde acaba**, y hay dos
maneras de averiguarlo:

1. **Los capítulos del propio fichero.** Un mkv bien hecho trae capítulos y uno se
   llama «Opening». Eso es una respuesta exacta sobre *este* fichero, gratis, y la
   da mpv al abrirlo. No la trae casi ningún stream.
2. **AniSkip.** Una base de datos comunitaria con los segundos exactos del opening y
   del ending de cada episodio de cada anime. Está indexada por el id de
   MyAnimeList, así que lo primero es ponerle un id de MAL al anime que se está
   viendo.

Hubo una tercera hasta la v0.19.3: **una cantidad fija** de 85 segundos, la duración
de un opening de televisión, para cuando ninguna de las dos contesta. No necesitaba
nada —ni red, ni identificar el anime, ni que la fuente colaborase— y por eso el botón
existía siempre. Ahí estaba el problema: *ofrecer* el botón es afirmar que el opening
está en pantalla, y sin datos eso no se sabe. En un anime cuyo opening empieza a los
tres minutos, el botón salía en el primer fotograma y saltar significaba caer en mitad
del episodio.

## Decisión

**El anime se identifica por tres vías, en ese orden: su tracker de MyAnimeList, su
tracker de AniList —que conoce el id de MAL de lo que tiene y lo dice sin cuenta— y,
si no tiene ninguno, su título buscado en el catálogo de AniList.** La tercera es la
que importa en la práctica: casi nadie vincula un anime *antes* de verlo, y sin ella
el botón era una cantidad fija para toda la biblioteca.

Un título es una conjetura donde un id es un hecho, así que solo se cree cuando
**coincide exactamente con una única entrada**. La comparación es una igualdad, no un
parecido, y lo que se iguala son las palabras del nombre **en orden**, contra todos los
nombres que AniList tiene de esa entrada (romaji, inglés, nativo y sinónimos). Una
palabra de más o de menos es otro anime: «One Piece» no contesta por «One Piece Film:
Red».

Lo que sí se normaliza antes de comparar es aquello en lo que dos catálogos discrepan
sin querer decir nada distinto, medido sobre 105 títulos reales de una biblioteca:

- **Mayúsculas, puntuación y las etiquetas de la fuente** —`(Dub)`, `[1080p]`—.
- **Cómo se escribe la temporada.** «4th Season», «Season 4», «S4» y un «4» a secas son
  la cuarta; los ordinales pasan a dígito, un número romano final también, y la palabra
  «season» se cae porque es puntuación entre el nombre y el número. **El número no se
  cae nunca**: eso es lo que haría que una temporada contestara por la primera.
- **Dónde caen los espacios y los guiones.** El romaji se parte a gusto de cada
  catálogo: «Bouken-roku» y «Boukenroku», «Hai Settei» y «Haisettei», «Gin Tama» y
  «Gintama». Se comparan también las letras seguidas, sin separadores y en orden.

Dos entradas que coinciden no son respuesta —no hay nada que elegir entre una serie y
su recopilatorio— salvo por dos desempates, en este orden y ningún otro:

1. **Llamarse exactamente así, carácter por carácter.** Las temporadas de Gintama son
   «Gintama», «Gintama'», «Gintama°» y «Gintama.», y ese apóstrofo es toda la
   diferencia, justo lo primero que tira una comparación de palabras.
2. **Ser la serie y no lo que la rodea.** Un special, un OVA o un recopilatorio llevan
   de sinónimo el nombre de la serie: «Assassination Classroom» es a la vez el anime y
   una cosa de diez minutos de una convención de 2013. Una fuente que lista episodios
   se refiere al anime. Solo se llega aquí entre entradas que ya coinciden en el nombre.

Empatadas después de los dos —dos series con el mismo nombre— no hay respuesta, que
cuesta un botón y nunca cuesta episodio.

**Y si la búsqueda no devuelve nada, se pregunta una segunda vez con el principio del
título.** La parte no estricta de todo esto es el buscador de AniList, que se rinde ante
los títulos de novela ligera: cuarenta caracteres de subtítulo tras los dos puntos y
contesta vacío. El nombre está delante, así que se pregunta por él —hasta el primer
corte fuerte, seis palabras como mucho— y lo que venga se compara igual contra el
título entero. Una pregunta más corta, no un listón más bajo.

De 105 títulos reales resuelven **90 (85%)**, frente a 77 (73%) con la igualdad literal
anterior. Y sobre 485 animes cuyo id se conocía de antemano —población más limpia, de
nombres de catálogo— aciertan **476 (98%)** frente a 459, **sin que ninguno resuelva al
anime equivocado**, que es el fallo que costaría episodio y la razón de que cada
desempate esté acotado. Lo que queda sin resolver es casi todo anime que AniList no tiene,
o títulos donde la fuente pone una palabra que el catálogo no. No encontrar nada es un
resultado correcto: no hay botón.

**Las dos conviven, en ese orden de confianza: capítulos → AniSkip.** El fichero manda
sobre la base de datos porque habla de la copia que se está viendo.

**El botón solo existe mientras el opening está en pantalla, y por tanto solo cuando
alguien sabe dónde está.** Sin capítulos y sin respuesta de AniSkip no se ofrece nada.
Es menos botón, y es el botón correcto: el que aparece dice «el opening está sonando y
esto lo salta», y sin datos ninguna de las dos cosas es verdad. Un botón que aparece en
el segundo cero de un episodio que abre con una escena miente sobre lo que hace, y
pulsarlo se lleva por delante lo que el espectador quería ver.

**El salto aterriza cinco segundos antes del final del opening, siempre.** El final de
un opening no es un fotograma, es un relevo —los últimos compases sobre el primer plano
de la escena—, y caer en el segundo exacto es fiarse de que ese segundo sea exacto,
cosa que aquí no lo es nunca. Cinco segundos de música son un momento; cinco segundos
de episodio son una escena que empieza sin ti y que no recuperas sin rebobinar. Así que
el botón cae justo antes y el episodio sigue desde ahí.

**Y esos cinco segundos se cuentan desde donde el opening acaba de verdad, no desde
donde una copia ajena dice que acaba.** Los tiempos de AniSkip se midieron sobre *otro*
fichero: un stream que trae unos segundos de logo que la copia cronometrada no traía, o
un montaje cortado distinto, desplaza toda la respuesta —el intervalo dura lo que debe,
pero está corrido—. La respuesta trae la duración del episodio sobre el que se midió, y
lo que esa duración se aleje de la nuestra es la única medida que hay de lo lejos que
están las dos copias: se resta también. Con un tope, porque pasado cierto punto la
diferencia está en otra parte del episodio —un avance que el stream no trae, unos
créditos cortados de otra forma— y no dice nada del opening. Un capítulo del propio
fichero no lleva esa corrección: ese habla de esta copia.

**El salto automático es opcional y viene apagado.** Actúa sobre un intervalo que ha
enviado otra persona; cuando está mal, se lleva minuto y medio de episodio por
delante. Se activa en Ajustes → Reproductor y, cuando salta, lo dice en pantalla.

**Cuando AniSkip no sabe, no pasa nada.** Un 404 es la respuesta normal para un
episodio que nadie ha cronometrado, y significa que en ese episodio no hay botón.

## Consecuencias

**Salen dos datos del dispositivo.** Consultar AniSkip manda el id de MyAnimeList del
anime y el número del episodio a `api.aniskip.com`, un tercero que no somos
nosotros. Y cuando el anime no está vinculado, su **título** va antes a
`graphql.anilist.co` para averiguar ese id. No va ninguna cuenta, ningún
identificador de usuario y nada más de la biblioteca: ambas peticiones son
indistinguibles de las de cualquier otra persona viendo ese episodio o buscando ese
anime. Aun así es una salida de datos que el usuario no ha pedido explícitamente,
así que las dos cuelgan del mismo interruptor en Ajustes —«Activar AniSkip»— y
están documentadas aquí.

**El id encontrado por título se recuerda mientras la app vive**, y también se
recuerda el *no haberlo encontrado*: un título que no casa con nada no va a empezar a
casar en el episodio siguiente, y sin esa memoria se buscaría en cada uno. Una
búsqueda que falla —sin red, servicio caído— no se recuerda, para que un minuto malo
no deje sin botón el resto de la sesión.

**La duración del episodio viaja con la consulta**, porque una serie con varios
montajes tiene tiempos para cada uno y así se elige el correcto. Si esa duración no
casa con ninguno, AniSkip contesta 404 en vez de aproximar, y entonces se vuelve a
preguntar sin ella: los tiempos del episodio son mejor respuesta que ninguna, y un
montaje unos segundos más largo que el cronometrado es el caso corriente.

**Dependemos de un servicio que no controlamos, y ahora más que antes.** Si AniSkip
desaparece, el botón solo sale en los ficheros que traen capítulos, que son pocos, y
para todo lo demás desaparece. Es el precio de no inventar: lo que se pierde es una
estimación, no un dato. Nada más se rompe.

**Hay episodios sin botón, y eso es lo esperado.** Un anime que AniSkip no tiene, un
título que no casa con ninguna entrada, un episodio numerado con un medio, el
interruptor apagado o un momento sin red: en todos ellos no aparece nada y el opening
se salta con la barra, como en cualquier reproductor. Es preferible a un botón que
aparece cuando no toca.

## Alternativas descartadas

- **Solo la cantidad fija** (lo que hace Aniyomi sin AniSkip). Es lo que teníamos en
  la v0.19.0 y deja el salto a ojo cuando hay un dato exacto disponible y gratis.
- **La cantidad fija como red de seguridad**, que es lo que hubo entre la v0.19.0 y la
  v0.19.3. Suena a que no cuesta nada —si no hay datos, al menos un botón— y cuesta
  precisamente lo que el botón promete: aparecía en el segundo cero de cualquier
  episodio, incluidos los que abren con una escena de tres minutos antes del opening,
  y pulsarlo saltaba a mitad del episodio. Se intentó atar de dos maneras —medir el
  salto desde el principio del episodio en vez de desde la pulsación, y no pasar nunca
  del último segundo— y seguía siendo una conjetura disfrazada de dato. Un botón que
  no está no engaña a nadie.
- **Detectar el opening analizando el audio o el vídeo.** Es la única vía que no
  depende de terceros ni de metadatos, y cuesta más CPU en un móvil de la que vale
  un salto de botón.
- **Quedarse con el resultado más parecido de la búsqueda por título.** Es lo que
  hace un buscador y lo que el usuario espera al teclear, pero aquí nadie ha
  tecleado nada: aceptar el más parecido convierte «One Piece Film: Red» en «One
  Piece» y se lleva por delante minuto y medio del episodio equivocado.
- **Mantener nuestra propia base de tiempos.** Infraestructura y trabajo manual
  eternos para reconstruir peor lo que AniSkip ya tiene.
