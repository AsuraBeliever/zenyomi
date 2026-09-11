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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.interactor.GetLibraryAnime
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
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var all: List<LibraryAnime> = emptyList()

    init {
        viewModelScope.launch {
            getLibraryAnime.subscribe().collect { library ->
                all = library
                _state.update { it.copy(isLoading = false, library = it.arrange(all)) }
            }
        }
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
    ) {
        val isEmpty: Boolean get() = library.isEmpty()

        /** No results for a search is a different situation from an empty library. */
        val isFilteredEmpty: Boolean get() = library.isEmpty() && !searchQuery.isNullOrBlank()

        internal fun arrange(source: List<LibraryAnime>): List<LibraryAnime> {
            val query = searchQuery?.trim().orEmpty()
            val filtered = if (query.isEmpty()) {
                source
            } else {
                source.filter { it.anime.title.contains(query, ignoreCase = true) }
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
