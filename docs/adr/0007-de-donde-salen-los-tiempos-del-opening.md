# ADR-0007 — De dónde salen los tiempos del opening

**Fecha:** 2026-09-18 · **Estado:** aceptada
**Revisada:** 2026-09-19 — cómo se identifica el anime ante AniSkip

## Contexto

Ver una temporada es ver el mismo minuto y medio de opening en cada episodio. El
botón para saltarlo tiene que decidir *a dónde* salta, y hay tres maneras de
saberlo, con precios y precisiones distintas:

1. **Una cantidad fija.** 85 segundos, que es lo que dura un opening de televisión
   menos los segundos de episodio que suelen precederlo. No necesita nada: ni red,
   ni que el anime esté identificado, ni que la fuente colabore. Tampoco acierta:
   deja unos segundos de más o de menos según la serie.
2. **Los capítulos del propio fichero.** Un mkv bien hecho trae capítulos y uno se
   llama «Opening». Eso es una respuesta exacta sobre *este* fichero, gratis, y la
   da mpv al abrirlo. No la trae casi ningún stream.
3. **AniSkip.** Una base de datos comunitaria con los segundos exactos del opening y
   del ending de cada episodio de cada anime. Está indexada por el id de
   MyAnimeList, así que lo primero es ponerle un id de MAL al anime que se está
   viendo.

## Decisión

**El anime se identifica por tres vías, en ese orden: su tracker de MyAnimeList, su
tracker de AniList —que conoce el id de MAL de lo que tiene y lo dice sin cuenta— y,
si no tiene ninguno, su título buscado en el catálogo de AniList.** La tercera es la
que importa en la práctica: casi nadie vincula un anime *antes* de verlo, y sin ella
el botón era una cantidad fija para toda la biblioteca.

Un título es una conjetura donde un id es un hecho, así que solo se cree cuando
**coincide exactamente con una única entrada**: la comparación es una igualdad, no un
parecido, después de quitar mayúsculas, puntuación y las etiquetas que la fuente
cuelga del nombre —`(Dub)`, `[1080p]`—; se compara contra todos los nombres que
AniList tiene de esa entrada (romaji, inglés, nativo y sinónimos); y dos entradas que
coinciden no son respuesta, porque entonces no hay nada que elegir entre una serie y
su recopilatorio. Una temporada nunca casa con la primera: «2nd Season» forma parte
del nombre y no se toca. No encontrar nada es un resultado correcto y sale gratis: se
vuelve a la cantidad fija.

**Las tres conviven, en ese orden de confianza: capítulos → AniSkip → cantidad
fija.** El fichero manda sobre la base de datos porque habla de la copia que se está
viendo; la base de datos manda sobre la cifra fija porque habla del episodio.

**El botón existe siempre.** Cuando hay un intervalo conocido, salta a su final
exacto y solo se ofrece mientras el opening está en pantalla. Cuando no lo hay,
salta la cantidad configurada durante los primeros minutos del episodio. Un usuario
sin trackers, sin red o con una fuente sin capítulos sigue teniendo botón.

**El salto automático es opcional y viene apagado.** Actúa sobre un intervalo que ha
enviado otra persona; cuando está mal, se lleva minuto y medio de episodio por
delante. Se activa en Ajustes → Reproductor y, cuando salta, lo dice en pantalla.
Nunca se automatiza la cantidad fija: eso es una estimación, y una estimación que
mueve el episodio sola no es una función, es un fallo.

**Cuando AniSkip no sabe, no pasa nada.** Un 404 es la respuesta normal para un
episodio que nadie ha cronometrado, y significa «usa la cantidad fija».

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
no deje el botón a ciegas el resto de la sesión.

**La duración del episodio viaja con la consulta**, porque una serie con varios
montajes tiene tiempos para cada uno y así se elige el correcto. Si esa duración no
casa con ninguno, AniSkip contesta 404 en vez de aproximar, y entonces se vuelve a
preguntar sin ella: los tiempos del episodio son mejor respuesta que ninguna, y un
montaje unos segundos más largo que el cronometrado es el caso corriente.

**Dependemos de un servicio que no controlamos.** Si AniSkip desaparece, el botón
sigue funcionando con los capítulos y con la cantidad fija; nada más se rompe.

## Alternativas descartadas

- **Solo la cantidad fija** (lo que hace Aniyomi sin AniSkip). Es lo que ya teníamos
  en la v0.19.0 y sirve, pero deja el salto a ojo cuando hay un dato exacto
  disponible y gratis.
- **Detectar el opening analizando el audio o el vídeo.** Es la única vía que no
  depende de terceros ni de metadatos, y cuesta más CPU en un móvil de la que vale
  un salto de botón.
- **Quedarse con el resultado más parecido de la búsqueda por título.** Es lo que
  hace un buscador y lo que el usuario espera al teclear, pero aquí nadie ha
  tecleado nada: aceptar el más parecido convierte «One Piece Film: Red» en «One
  Piece» y se lleva por delante minuto y medio del episodio equivocado.
- **Mantener nuestra propia base de tiempos.** Infraestructura y trabajo manual
  eternos para reconstruir peor lo que AniSkip ya tiene.
