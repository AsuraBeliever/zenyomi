package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuAccount
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuAddMangaResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuCurrentAccountResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuDeleteMangaResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuLibraryEntryMutationResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchAnimeByIdResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchAnimeByIdWithLibraryResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchAnimeBySlugResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchAnimeByTitleResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchByIdResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchByIdWithLibraryResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchBySlugResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchByTitleResult
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSparseLibraryEntry
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuUpdateMangaResult
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import kotlin.time.Instant
import tachiyomi.domain.track.model.Track as DomainTrack

class KitsuApi(
    private val trackerId: Long,
    private val client: OkHttpClient,
    interceptor: KitsuInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track {
        return withIOContext {
            val query = $$"""
                |mutation AddManga(
                  |$media_id: ID!
                  |$status: LibraryEntryStatusEnum!
                  |$progress: Int!
                  |$private: Boolean!
                  |$rating: Int
                |) {
                  |libraryEntry {
                    |create(
                      |input: {
                        |mediaId: $media_id
                        |mediaType: MANGA
                        |status: $status
                        |progress: $progress
                        |private: $private
                        |rating: $rating
                      |}
                    |) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("media_id", track.remote_id)
                    put("status", track.toKitsuApiStatus())
                    put("progress", track.last_chapter_read.toInt())
                    put("private", track.private)
                    put("rating", track.score.toInt().takeIf { it > 0 })
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuAddMangaResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to add: ${parsed.error.message ?: "(none)"}" }
                    throw Exception("Failed to add manga")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to add: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to add manga")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while adding manga")
                }

                parsed.data.libraryEntry.create.libraryEntry.id.let {
                    track.library_id = it.toLong()
                    track
                }
            }
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            val query = $$"""
                |mutation UpdateManga(
                  |$library_id: ID!
                  |$status: LibraryEntryStatusEnum!
                  |$progress: Int!
                  |$private: Boolean!
                  |$rating: Int
                  |$startedAt: ISO8601DateTime
                  |$finishedAt: ISO8601DateTime
                |) {
                  |libraryEntry {
                    |update(
                      |input: {
                        |id: $library_id
                        |status: $status
                        |progress: $progress
                        |private: $private
                        |rating: $rating
                        |startedAt: $startedAt
                        |finishedAt: $finishedAt
                      |}
                    |) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("library_id", track.library_id)
                    put("status", track.toKitsuApiStatus())
                    put("progress", track.last_chapter_read.toInt())
                    put("private", track.private)
                    put("rating", track.score.toInt().takeIf { it > 0 })
                    put(
                        "startedAt",
                        track.started_reading_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it).toString() },
                    )
                    put(
                        "finishedAt",
                        track.finished_reading_date
                            .takeIf { it > 0 }
                            ?.let { Instant.fromEpochMilliseconds(it).toString() },
                    )
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuUpdateMangaResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to update: ${parsed.error.message ?: "(none)"}" }
                    throw Exception("Failed to update manga")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to update: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to update manga")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while updating manga")
                }

                track
            }
        }
    }

    suspend fun removeLibManga(track: DomainTrack) {
        withIOContext {
            val query = $$"""|
                |mutation DeleteLibEntry(
                  |$library_id: ID!
                |) {
                  |libraryEntry {
                    |delete(
                      |input: {
                        |id: $library_id
                      |}
                    |) {
                      |errors {
                        |message
                      |}
                      |libraryEntry {
                        |id
                      |}
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("library_id", track.libraryId)
                }
            }

            with(json) {
                val parsed = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    // Deleting something not in the library returns a 500 with "Couldn't find LibraryEntry" msg
                    // awaitSuccess would throw with that but user gets their wish of "title not in library" so ignore it
                    .await()
                    .parseAs<KitsuDeleteMangaResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to delete: ${parsed.error.message ?: "(none)"}" }
                    if (parsed.error.message != null && parsed.error.message.startsWith("Couldn't find")) {
                        return@with
                    }
                    throw Exception("Failed to delete manga")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to delete: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to delete manga")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while deleting manga")
                }
            }
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val query = $$"""
                |query Query($query: String!) {
                  |searchMangaByTitle(title: $query, first: 20) {
                    |nodes {
                      $$COMMON_MANGA_DATA
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuSearchByTitleResult>()
                    .data.searchMangaByTitle.nodes
                    .map { it.toTrackSearch(trackerId) }
            }
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withIOContext {
            val query = $$"""
                |query Query($remote_id: ID!) {
                  |findMangaById(id: $remote_id) {
                    |$$COMMON_MANGA_DATA
                    |myLibraryEntry {
                      |id
                      |private
                      |progress
                      |rating
                      |reconsuming
                      |status
                      |startedAt
                      |finishedAt
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("remote_id", track.remote_id)
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuSearchByIdWithLibraryResult>()
                    .data.findMangaById
                    ?.toTrackSearch(trackerId)
            }
        }
    }

    suspend fun login(username: String, password: String): KitsuOAuth {
        return withIOContext {
            val formBody: RequestBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("grant_type", "password")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            with(json) {
                client.newCall(POST(LOGIN_URL, body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): KitsuAccount {
        return withIOContext {
            val query = """
                |query Query {
                  |currentAccount {
                    |id
                    |ratingSystem
                    |profile {
                      |name
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuCurrentAccountResult>()
                    .data.currentAccount
            }
        }
    }

    suspend fun getMangaDetails(search: String): TrackSearch? {
        val isSearchById = search.matches(Regex("\\d+"))

        val query = if (isSearchById) {
            $$"""
                |query Query($query: ID!) {
                  |findMangaById(id: $query) {
                    |$$COMMON_MANGA_DATA
                  |}
                |}
            """
        } else {
            $$"""
                |query Query($query: String!) {
                  |findMangaBySlug(slug: $query) {
                    |$$COMMON_MANGA_DATA
                  |}
                |}
            """
        }

        val payload = buildJsonObject {
            put("query", query.trimMargin())
            putJsonObject("variables") {
                put("query", search)
            }
        }

        return withIOContext {
            with(json) {
                val response = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()

                val kitsuManga = if (isSearchById) {
                    response
                        .parseAs<KitsuSearchByIdResult>()
                        .data.findMangaById
                } else {
                    response
                        .parseAs<KitsuSearchBySlugResult>()
                        .data.findMangaBySlug
                }

                kitsuManga?.toTrackSearch(trackerId)
            }
        }
    }

    // ---- Anime -------------------------------------------------------------------------
    //
    // A Kitsu library is a single list with a media type on each entry, so the mutations are
    // the same GraphQL the manga side sends bar `mediaType: ANIME` when creating one. What
    // genuinely differs is reading: `findAnimeById` and `searchAnimeByTitle` instead of their
    // manga twins, and `episodeCount` instead of `chapterCount`.
    //
    // The error ladder every mutation needs lives in [mutateLibraryEntry] rather than being
    // written out three more times.

    suspend fun addLibAnime(track: AnimeTrack): AnimeTrack {
        val query = $$"""
            |mutation AddAnime(
              |$media_id: ID!
              |$status: LibraryEntryStatusEnum!
              |$progress: Int!
              |$private: Boolean!
              |$rating: Int
            |) {
              |libraryEntry {
                |create(
                  |input: {
                    |mediaId: $media_id
                    |mediaType: ANIME
                    |status: $status
                    |progress: $progress
                    |private: $private
                    |rating: $rating
                  |}
                |) {
                  |errors {
                    |message
                  |}
                  |libraryEntry {
                    |id
                  |}
                |}
              |}
            |}
        """.trimMargin()

        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("media_id", track.remote_id)
                put("status", track.toKitsuApiStatus())
                put("progress", track.last_episode_seen.toInt())
                put("private", track.private)
                put("rating", track.score.toInt().takeIf { it > 0 })
            }
        }

        val entry = mutateLibraryEntry("add", payload)
            ?: throw Exception("Kitsu did not say which library entry it added the anime to")
        track.library_id = entry.id.toLong()
        return track
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack {
        val query = $$"""
            |mutation UpdateAnime(
              |$library_id: ID!
              |$status: LibraryEntryStatusEnum!
              |$progress: Int!
              |$private: Boolean!
              |$rating: Int
              |$startedAt: ISO8601DateTime
              |$finishedAt: ISO8601DateTime
            |) {
              |libraryEntry {
                |update(
                  |input: {
                    |id: $library_id
                    |status: $status
                    |progress: $progress
                    |private: $private
                    |rating: $rating
                    |startedAt: $startedAt
                    |finishedAt: $finishedAt
                  |}
                |) {
                  |errors {
                    |message
                  |}
                  |libraryEntry {
                    |id
                  |}
                |}
              |}
            |}
        """.trimMargin()

        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("library_id", track.library_id)
                put("status", track.toKitsuApiStatus())
                put("progress", track.last_episode_seen.toInt())
                put("private", track.private)
                put("rating", track.score.toInt().takeIf { it > 0 })
                put(
                    "startedAt",
                    track.started_watching_date
                        .takeIf { it > 0 }
                        ?.let { Instant.fromEpochMilliseconds(it).toString() },
                )
                put(
                    "finishedAt",
                    track.finished_watching_date
                        .takeIf { it > 0 }
                        ?.let { Instant.fromEpochMilliseconds(it).toString() },
                )
            }
        }

        mutateLibraryEntry("update", payload)
        return track
    }

    suspend fun removeLibAnime(libraryId: Long) {
        val query = $$"""|
            |mutation DeleteLibEntry(
              |$library_id: ID!
            |) {
              |libraryEntry {
                |delete(
                  |input: {
                    |id: $library_id
                  |}
                |) {
                  |errors {
                    |message
                  |}
                  |libraryEntry {
                    |id
                  |}
                |}
              |}
            |}
        """.trimMargin()

        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("library_id", libraryId)
            }
        }

        mutateLibraryEntry("delete", payload, tolerateMissing = true)
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> {
        return withIOContext {
            val query = $$"""
                |query Query($query: String!) {
                  |searchAnimeByTitle(title: $query, first: 20) {
                    |nodes {
                      $$COMMON_ANIME_DATA
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("query", search)
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuSearchAnimeByTitleResult>()
                    .data.searchAnimeByTitle.nodes
                    .map { it.toAnimeTrackSearch(trackerId) }
            }
        }
    }

    suspend fun findLibAnime(track: AnimeTrack): AnimeTrackSearch? {
        return withIOContext {
            val query = $$"""
                |query Query($remote_id: ID!) {
                  |findAnimeById(id: $remote_id) {
                    |$$COMMON_ANIME_DATA
                    |myLibraryEntry {
                      |id
                      |private
                      |progress
                      |rating
                      |reconsuming
                      |status
                      |startedAt
                      |finishedAt
                    |}
                  |}
                |}
            """.trimMargin()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("remote_id", track.remote_id)
                }
            }

            with(json) {
                authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()
                    .parseAs<KitsuSearchAnimeByIdWithLibraryResult>()
                    .data.findAnimeById
                    ?.toAnimeTrackSearch(trackerId)
            }
        }
    }

    suspend fun getAnimeDetails(search: String): AnimeTrackSearch? {
        val isSearchById = search.matches(Regex("\\d+"))

        val query = if (isSearchById) {
            $$"""
                |query Query($query: ID!) {
                  |findAnimeById(id: $query) {
                    |$$COMMON_ANIME_DATA
                  |}
                |}
            """
        } else {
            $$"""
                |query Query($query: String!) {
                  |findAnimeBySlug(slug: $query) {
                    |$$COMMON_ANIME_DATA
                  |}
                |}
            """
        }

        val payload = buildJsonObject {
            put("query", query.trimMargin())
            putJsonObject("variables") {
                put("query", search)
            }
        }

        return withIOContext {
            with(json) {
                val response = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                    .awaitSuccess()

                val kitsuAnime = if (isSearchById) {
                    response
                        .parseAs<KitsuSearchAnimeByIdResult>()
                        .data.findAnimeById
                } else {
                    response
                        .parseAs<KitsuSearchAnimeBySlugResult>()
                        .data.findAnimeBySlug
                }

                kitsuAnime?.toAnimeTrackSearch(trackerId)
            }
        }
    }

    /**
     * Sends one library-entry mutation and hands back the entry it touched.
     *
     * @param action the verb for the log line, so a failure says which mutation failed.
     * @param tolerateMissing deleting an entry that is not there answers 500 with "Couldn't
     * find LibraryEntry". That is the caller's wish already granted, not a failure, so the
     * response is read with [await] rather than [awaitSuccess] and that one message forgiven.
     */
    private suspend fun mutateLibraryEntry(
        action: String,
        payload: JsonObject,
        tolerateMissing: Boolean = false,
    ): KitsuSparseLibraryEntry? {
        return withIOContext {
            with(json) {
                val call = authClient.newCall(
                    POST(
                        GRAPHQL_API_URL,
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                )
                val parsed = (if (tolerateMissing) call.await() else call.awaitSuccess())
                    .parseAs<KitsuLibraryEntryMutationResult>()

                if (parsed.error != null) {
                    logcat(LogPriority.ERROR) { "Failed to $action: ${parsed.error.message ?: "(none)"}" }
                    if (tolerateMissing && parsed.error.message?.startsWith("Couldn't find") == true) {
                        return@with null
                    }
                    throw Exception("Failed to $action anime")
                } else if (parsed.errors != null) {
                    parsed.errors.forEach {
                        logcat(LogPriority.ERROR) { "Failed to $action: ${it.message ?: "(none)"}" }
                    }
                    throw Exception("Failed to $action anime")
                } else if (parsed.data == null) {
                    logcat(LogPriority.ERROR) { "Kitsu error, errors, and data null?" }
                    throw Exception("Encountered unexpected error while trying to $action anime")
                }

                parsed.data.libraryEntry.entry
            }
        }
    }

    companion object {
        private const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

        private const val GRAPHQL_API_URL = "https://kitsu.app/api/graphql"
        private const val LOGIN_URL = "https://kitsu.app/api/oauth/token"

        fun refreshTokenRequest(token: String) = POST(
            LOGIN_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", token)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build(),
        )

        private val COMMON_MANGA_DATA = """
            |id
            |titles {
              |preferred
            |}
            |chapterCount
            |staff(first: 5) {
              |nodes {
                |role
                |person {
                  |name
                |}
              |}
            |}
            |posterImage {
              |views(names: "small") {
                |name
                |url
              |}
              |original {
                |name
                |url
              |}
            |}
            |description(locales: "en")
            |status
            |subtype
            |startDate
            |endDate
            |slug
            |averageRating
        """.trimMargin()

        /**
         * What every anime query asks for. The manga twin also pulls five staff members; an
         * anime search result has no authors or artists to show, so this does not.
         */
        private val COMMON_ANIME_DATA = """
            |id
            |titles {
              |preferred
            |}
            |episodeCount
            |posterImage {
              |views(names: "small") {
                |name
                |url
              |}
              |original {
                |name
                |url
              |}
            |}
            |description(locales: "en")
            |status
            |subtype
            |startDate
            |endDate
            |slug
            |averageRating
        """.trimMargin()
    }
}
