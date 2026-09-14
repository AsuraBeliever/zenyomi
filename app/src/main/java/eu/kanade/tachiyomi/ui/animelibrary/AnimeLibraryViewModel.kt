package eu.kanade.tachiyomi.ui.animelibrary

import android.content.res.Configuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.ui.animelibrary.setting.AnimeLibraryDisplayMode
import eu.kanade.tachiyomi.ui.animelibrary.setting.AnimeLibraryPreferences
import eu.kanade.tachiyomi.ui.animelibrary.setting.AnimeLibrarySort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * The anime library: what is in it, and how the user wants it shown.
 *
 * Searching, filtering and sorting all happen here rather than in SQL. The whole library is
 * already in memory to be drawn, and narrowing a list of tens of entries costs nothing next to
 * another query — and it keeps the order stable whatever else is active.
 *
 * Every choice but the open tab is persisted. A library is arranged once and then lived in;
 * re-picking "unwatched only" on every open is what makes a filter not worth using. The tab is
 * deliberately not persisted: which category you were last looking at is not a setting.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeLibraryViewModel(
    private val getLibraryAnime: GetLibraryAnime,
    private val getAnimeCategories: GetAnimeCategories,
    private val preferences: AnimeLibraryPreferences,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var all: List<LibraryAnime> = emptyList()

    init {
        // One collector for the rows, their categories and the display settings, rather than
        // one for the library and one for the settings.
        //
        // Both arrange the same [all] and both write the same state, so as two coroutines they
        // were two writers racing over one field: whichever landed second decided what the
        // grid showed, and when that was the settings one — reading an [all] the library
        // collector had not filled yet — the answer was an empty library. On the main
        // dispatcher they happened to serialise and it never showed. Combined, there is one
        // writer and the question does not arise.
        //
        // launchIO: the query and the mapping of every library row happen on the collecting
        // thread, and a large library makes that too much work for the main one.
        viewModelScope.launchIO {
            // The library rows carry their category, so both have to arrive before the grid
            // can be drawn: a tab with no name is worse than a moment of loading.
            combine(
                getLibraryAnime.subscribe(),
                getAnimeCategories.subscribe(),
                settingsChanges(),
                categoryTabsChanges(),
            ) { library, categories, settings, categoryTabs ->
                Arrival(library, categories, settings, categoryTabs)
            }
                .collect { (library, categories, settings, categoryTabs) ->
                    all = library
                    _state.update { state ->
                        state
                            .copy(
                                isLoading = false,
                                categories = categories.filterNot { it.hidden },
                                settings = settings,
                                categoryTabs = categoryTabs,
                                // Land on the first tab, and follow the categories if the one
                                // that was open has since been deleted.
                                selectedCategory = state.selectedCategory
                                    ?.takeIf { open -> categories.any { it.id == open } }
                                    ?: categories.minByOrNull { it.order }?.id,
                            )
                            .rearranged()
                    }
                }
        }
    }

    private fun settingsChanges() = combine(
        preferences.sort.changes(),
        preferences.sortAscending.changes(),
        preferences.displayMode.changes(),
        filterChanges(),
    ) { sort, ascending, display, filters ->
        Settings(sort, ascending, display, filters)
    }

    /** One entry at random from the tab that is open, for the menu item of that name. */
    fun randomInCurrentCategory(): LibraryAnime? = state.value.library.randomOrNull()

    /** The shared *Category tabs* switch, so both libraries hide their tabs together. */
    private fun categoryTabsChanges() = libraryPreferences.categoryTabs.changes()

    /**
     * How many columns the grid gets, from the very preference the manga library reads.
     *
     * Shared on purpose rather than duplicated: grid density is part of what makes two
     * libraries look alike, and a separate anime setting would let them drift apart again
     * the first time someone changed one of them. 0 means "fit what you can", the default
     * on both sides.
     */
    fun columnsFor(orientation: Int): Int {
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) {
            libraryPreferences.landscapeColumns
        } else {
            libraryPreferences.portraitColumns
        }.get()
    }

    private fun filterChanges() = combine(
        preferences.filterUnseen.changes(),
        preferences.filterStarted.changes(),
        preferences.filterBookmarked.changes(),
        preferences.filterCompleted.changes(),
    ) { unseen, started, bookmarked, completed ->
        Filters(unseen, started, bookmarked, completed)
    }

    private fun State.rearranged(): State {
        // Counted over everything the search and filters leave, before narrowing to a tab, so
        // a tab's number is what that tab would actually show. A count that disagrees with the
        // grid under it reads as a bug.
        val everything = arrange(all, ignoreCategory = true)
        return copy(
            library = arrange(all),
            countByCategory = everything.groupingBy { it.category }.eachCount(),
            totalCount = everything.distinctBy { it.id }.size,
        )
    }

    fun search(query: String?) {
        _state.update { it.copy(searchQuery = query).rearranged() }
    }

    fun setCategory(categoryId: Long?) {
        _state.update { it.copy(selectedCategory = categoryId).rearranged() }
    }

    /** Tapping the sort already in use flips its direction, which is what a second tap means. */
    fun setSort(sort: AnimeLibrarySort) {
        if (preferences.sort.get() == sort) {
            preferences.sortAscending.set(!preferences.sortAscending.get())
        } else {
            preferences.sort.set(sort)
            preferences.sortAscending.set(true)
        }
    }

    fun setDisplayMode(mode: AnimeLibraryDisplayMode) = preferences.displayMode.set(mode)

    fun cycleFilter(filter: Filter) {
        val preference = when (filter) {
            Filter.UNSEEN -> preferences.filterUnseen
            Filter.STARTED -> preferences.filterStarted
            Filter.BOOKMARKED -> preferences.filterBookmarked
            Filter.COMPLETED -> preferences.filterCompleted
        }
        preference.set(preference.get().next())
    }

    fun clearFilters() {
        preferences.filterUnseen.set(TriState.DISABLED)
        preferences.filterStarted.set(TriState.DISABLED)
        preferences.filterBookmarked.set(TriState.DISABLED)
        preferences.filterCompleted.set(TriState.DISABLED)
    }

    enum class Filter { UNSEEN, STARTED, BOOKMARKED, COMPLETED }

    data class Filters(
        val unseen: TriState = TriState.DISABLED,
        val started: TriState = TriState.DISABLED,
        val bookmarked: TriState = TriState.DISABLED,
        val completed: TriState = TriState.DISABLED,
    ) {
        val any: Boolean
            get() = listOf(unseen, started, bookmarked, completed).any { it != TriState.DISABLED }

        fun of(filter: Filter): TriState = when (filter) {
            Filter.UNSEEN -> unseen
            Filter.STARTED -> started
            Filter.BOOKMARKED -> bookmarked
            Filter.COMPLETED -> completed
        }
    }

    data class Settings(
        val sort: AnimeLibrarySort = AnimeLibrarySort.TITLE,
        val sortAscending: Boolean = true,
        val displayMode: AnimeLibraryDisplayMode = AnimeLibraryDisplayMode.COMFORTABLE_GRID,
        val filters: Filters = Filters(),
    )

    /** Everything one emission of the library carries, named so the collector reads. */
    private data class Arrival(
        val library: List<LibraryAnime>,
        val categories: List<AnimeCategory>,
        val settings: Settings,
        val categoryTabs: Boolean,
    )

    data class State(
        val isLoading: Boolean = true,
        val library: List<LibraryAnime> = emptyList(),
        val searchQuery: String? = null,
        val settings: Settings = Settings(),
        val categories: List<AnimeCategory> = emptyList(),
        /**
         * Which category tab is open. Null until the categories arrive, and then the first
         * one — there is no "everything" tab any more, the same as on the manga side.
         */
        val selectedCategory: Long? = null,
        /**
         * How many entries each tab holds. Counted over the whole library rather than over
         * [library], which is already narrowed to the open tab, the search and the filters.
         */
        val categoryTabs: Boolean = true,
        val countByCategory: Map<Long, Int> = emptyMap(),
        /**
         * Distinct anime across every category, which is not the sum of [countByCategory]: an
         * anime filed in two categories is counted once here and twice there.
         */
        val totalCount: Int = 0,
    ) {
        val isEmpty: Boolean get() = library.isEmpty()

        /** No results for a search is a different situation from an empty library. */
        val isFilteredEmpty: Boolean get() = library.isEmpty() && !searchQuery.isNullOrBlank()

        /** As is a filter that excluded everything: the fix is to relax it, not to add anime. */
        val isFilterEmpty: Boolean
            get() = library.isEmpty() && searchQuery.isNullOrBlank() && settings.filters.any

        /**
         * Whether the current tab is empty because of the tab. Different words again: this one
         * says to file something here.
         */
        val isEmptyCategory: Boolean
            get() = library.isEmpty() &&
                searchQuery.isNullOrBlank() &&
                !settings.filters.any &&
                selectedCategory != null

        /**
         * Mihon's rule, character for character, including the preference.
         *
         * It used to be "any category that is not the default", which ignored the
         * *Category tabs* setting entirely: turning tabs off hid them on the manga library and
         * left them on the anime one. The setting is shared, so both halves now obey it.
         */
        val showCategoryTabs: Boolean
            get() = categoryTabs && categories.isNotEmpty() &&
                (categories.size > 1 || !categories.first().isSystemCategory)

        internal fun arrange(
            source: List<LibraryAnime>,
            ignoreCategory: Boolean = false,
        ): List<LibraryAnime> {
            val query = searchQuery?.trim().orEmpty()

            var result = source
            if (selectedCategory != null && !ignoreCategory) {
                result = result.filter { it.category == selectedCategory }
            }
            if (query.isNotEmpty()) {
                result = result.filter { it.anime.title.contains(query, ignoreCase = true) }
            }
            result = result.filter { settings.filters.keeps(it) }

            // The library view returns one row per (anime, category), so an anime filed in two
            // categories arrives twice. Inside a tab that cannot happen; across all of them it
            // would show the same cover side by side.
            if (selectedCategory == null && !ignoreCategory) {
                result = result.distinctBy { it.id }
            }

            val sorted = when (settings.sort) {
                AnimeLibrarySort.TITLE -> result.sortedBy { it.anime.title.lowercase() }
                // Most recently watched first; anything never opened sorts to the bottom.
                AnimeLibrarySort.LAST_SEEN -> result.sortedByDescending { it.lastSeen }
                AnimeLibrarySort.UNSEEN -> result.sortedByDescending { it.unseenCount }
                AnimeLibrarySort.TOTAL_EPISODES -> result.sortedByDescending { it.totalCount }
                AnimeLibrarySort.LATEST_EPISODE -> result.sortedByDescending { it.latestUpload }
                AnimeLibrarySort.DATE_ADDED -> result.sortedByDescending { it.anime.dateAdded }
            }

            // Every sort but title reads naturally as "most first", so ascending reverses them
            // rather than each one carrying its own idea of which way is up.
            return if (settings.sortAscending) sorted else sorted.reversed()
        }
    }
}

/**
 * Whether an entry survives the active filters.
 *
 * A disabled filter keeps everything; the two enabled states are "is" and "is not", which is
 * what the three-way tap cycles through.
 */
private fun AnimeLibraryViewModel.Filters.keeps(item: LibraryAnime): Boolean {
    fun TriState.keeps(value: Boolean) = when (this) {
        TriState.DISABLED -> true
        TriState.ENABLED_IS -> value
        TriState.ENABLED_NOT -> !value
    }

    return unseen.keeps(item.unseenCount > 0) &&
        started.keeps(item.hasStarted) &&
        bookmarked.keeps(item.hasBookmarks) &&
        // "Completed" is the source's own status for the series, not how much of it is watched.
        completed.keeps(item.anime.status.toInt() == STATUS_COMPLETED)
}

/**
 * [eu.kanade.tachiyomi.animesource.model.SAnime.COMPLETED]. Spelled out rather than imported so
 * the filter does not reach into the source API for one integer.
 */
private const val STATUS_COMPLETED = 2
