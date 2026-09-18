# ADR-0007 — De dónde salen los tiempos del opening

**Fecha:** 2026-09-18 · **Estado:** aceptada

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
   MyAnimeList, así que solo responde de un anime que esté vinculado a MAL o a
   AniList —que conoce el id de MAL de lo que tiene y lo dice sin cuenta—.

## Decisión

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

**Sale un dato del dispositivo.** Consultar AniSkip manda el id de MyAnimeList del
anime y el número del episodio a `api.aniskip.com`, un tercero que no somos
nosotros. No va ninguna cuenta, ningún identificador de usuario y nada de la
biblioteca: la petición es indistinguible de la de cualquier otra persona viendo ese
episodio. Aun así es una salida de datos que el usuario no ha pedido
explícitamente, así que tiene su interruptor en Ajustes y está documentada aquí.

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
- **Mantener nuestra propia base de tiempos.** Infraestructura y trabajo manual
  eternos para reconstruir peor lo que AniSkip ya tiene.
