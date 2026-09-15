package eu.kanade.tachiyomi.data.track.kitsu.dto

import kotlinx.serialization.Serializable

/**
 * The answer to any of the three library-entry mutations.
 *
 * Kitsu replies with the same envelope whether the entry was created, updated or deleted; only
 * the field name under `libraryEntry` changes. One DTO therefore covers all three on the anime
 * side, instead of the near-identical triple the manga side inherited from Mihon.
 *
 * Both error attributes are real and they have different shapes: a 500 sends `error`, a 200
 * carrying validation errors sends `errors`.
 */
@Serializable
data class KitsuLibraryEntryMutationResult(
    val data: KitsuLibraryEntryMutationData?,
    val errors: List<KitsuErrorMessage>?,
    val error: KitsuErrorMessage?,
)

@Serializable
data class KitsuLibraryEntryMutationData(
    val libraryEntry: KitsuLibraryEntryMutations,
)

@Serializable
data class KitsuLibraryEntryMutations(
    val create: KitsuLibraryEntryResult?,
    val update: KitsuLibraryEntryResult?,
    val delete: KitsuLibraryEntryResult?,
) {
    /** A mutation answers under its own name, and one request only ever runs one of them. */
    val entry: KitsuSparseLibraryEntry?
        get() = (create ?: update ?: delete)?.libraryEntry
}
