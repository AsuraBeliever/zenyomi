package eu.kanade.tachiyomi.ui.animelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.model.AnimeCategory
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.i18n.anime.ANMR

/**
 * The anime library: what is in it, and how the user wants it shown.
 *
 * Search and sorting are applied here rather than in SQL. The whole library is already in
 * memory to be drawn, and filtering a list of tens of entries costs nothing next to another
 * query — and it keeps the sort order the same whether or not a search is active.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeLibraryViewModel(
    private val getLibraryAnime: GetLibraryAnime,
    private val getAnimeCategories: GetAnimeCategories,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var all: List<LibraryAnime> = emptyList()

    init {
        viewModelScope.launch {
            // The library rows carry their category, so both have to arrive before the grid
            // can be drawn: a tab with no name is worse than a moment of loading.
            combine(
                getLibraryAnime.subscribe(),
                getAnimeCategories.subscribe(),
            ) { library, categories -> library to categories }
                .collect { (library, categories) ->
                    all = library
                    _state.update { state ->
                        state
                            .copy(
                                isLoading = false,
                                categories = categories.filterNot { it.hidden },
                                countByCategory = all.groupingBy { it.category }.eachCount(),
                            )
                            .let { it.copy(library = it.arrange(all)) }
                    }
                }
        }
    }

    fun setCategory(categoryId: Long?) {
        _state.update { it.copy(selectedCategory = categoryId).let { s -> s.copy(library = s.arrange(all)) } }
    }

    fun search(query: String?) {
        _state.update { it.copy(searchQuery = query).let { s -> s.copy(library = s.arrange(all)) } }
    }

    fun setSort(sort: Sort) {
        _state.update { it.copy(sort = sort).let { s -> s.copy(library = s.arrange(all)) } }
    }

    enum class Sort(val label: dev.icerock.moko.resources.StringResource) {
        TITLE(ANMR.strings.sort_anime_title),
        LAST_SEEN(ANMR.strings.sort_anime_last_seen),
        UNSEEN(ANMR.strings.sort_anime_unseen),
    }

    data class State(
        val isLoading: Boolean = true,
        val library: List<LibraryAnime> = emptyList(),
        val searchQuery: String? = null,
        val sort: Sort = Sort.TITLE,
        val categories: List<AnimeCategory> = emptyList(),
        /** null means "everything", which is the only sensible landing tab. */
        val selectedCategory: Long? = null,
        /**
         * How many entries each tab holds. Counted over the whole library rather than over
         * [library], which is already narrowed to the open tab and to the search.
         */
        val countByCategory: Map<Long, Int> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = library.isEmpty()

        /** No results for a search is a different situation from an empty library. */
        val isFilteredEmpty: Boolean get() = library.isEmpty() && !searchQuery.isNullOrBlank()

        /**
         * Tabs only earn their space once something has been filed. A library with nothing but
         * the default category would show a single tab that does nothing.
         */
        val showCategoryTabs: Boolean get() = categories.any { !it.isSystemCategory }

        /**
         * Whether the current tab is empty because of the tab, not because of a search. The
         * two need different words: one says to file something here, the other says the search
         * matched nothing.
         */
        val isEmptyCategory: Boolean
            get() = library.isEmpty() && searchQuery.isNullOrBlank() && selectedCategory != null

        internal fun arrange(source: List<LibraryAnime>): List<LibraryAnime> {
            val query = searchQuery?.trim().orEmpty()
            val inCategory = if (selectedCategory == null) {
                source
            } else {
                source.filter { it.category == selectedCategory }
            }
            val filtered = if (query.isEmpty()) {
                inCategory
            } else {
                inCategory.filter { it.anime.title.contains(query, ignoreCase = true) }
            }
            return when (sort) {
                Sort.TITLE -> filtered.sortedBy { it.anime.title.lowercase() }
                // Most recently watched first; anything never opened sorts to the bottom.
                Sort.LAST_SEEN -> filtered.sortedByDescending { it.lastSeen }
                Sort.UNSEEN -> filtered.sortedByDescending { it.unseenCount }
            }
        }
    }
}
