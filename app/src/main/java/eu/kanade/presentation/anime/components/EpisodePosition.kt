package eu.kanade.presentation.anime.components

/**
 * El equivalente de anime al "Pagina 12" que el manga enseña en un capitulo a medias.
 *
 * La posicion se guarda en segundos, igual que `last_second_seen` en la base de datos y que
 * lo que el reproductor le pasa a mpv al reanudar, asi que aqui no hay ninguna conversion
 * que hacer: dividir entre mil convertia diez minutos en "0:00", que era justo el numero que
 * hacia parecer que no se guardaba nada.
 *
 * "12:34", o "1:02:03" si pasa de la hora.
 */
fun formatEpisodePosition(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
