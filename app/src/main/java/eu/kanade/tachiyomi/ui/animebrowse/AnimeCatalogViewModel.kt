package eu.kanade.tachiyomi.ui.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Browses one anime source's catalogue, and searches it.
 *
 * Takes the listing from [GetRemoteAnime], which decides between popular, latest and
 * search by the sentinel query it is given, exactly as Mihon's browse does. A typed query
 * replaces the sentinel, which is all it takes to turn browsing into searching.
 */
@AssistedInject
class AnimeCatalogViewModel(
    @Assisted private val sourceId: Long,
    private val getRemoteAnime: GetRemoteAnime,
    private val sourceManager: AnimeSourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * The source's own filters, in their default state, once they have been read.
     *
     * Null until then, and the listing waits for them rather than starting without: an
     * extension is handed its filters back and is entitled to read them. KickAssAnime's search
     * picks its language filter out of the list with `first()`, so an empty list threw
     * NoSuchElementException before a single request was made, and searching that source
     * failed with what read like a broken site. Mihon's browse passes them on the manga side
     * for the same reason.
     */
    private val filters = MutableStateFlow<AnimeFilterList?>(null)

    /**
     * Re-pages on every change of query. [flatMapLatest] rather than a Pager built once:
     * the query is part of what is being paged, so a new one is a new pager, and the old
     * one's in-flight page must not land in the new list.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val animeList = combine(
        // Without this the source name arriving would rebuild the pager and refetch page one.
        _state.map { it.searchQuery }.distinctUntilChanged(),
        filters.filterNotNull(),
        ::Pair,
    )
        .distinctUntilChanged()
        .flatMapLatest { (query, sourceFilters) ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteAnime.subscribe(
                    sourceId,
                    query?.takeIf { it.isNotBlank() } ?: GetRemoteAnime.QUERY_POPULAR,
                    sourceFilters,
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    fun search(query: String?) = _state.update { it.copy(searchQuery = query) }

    init {
        viewModelScope.launch {
            val source = sourceManager.getOrStub(sourceId)
            // Building them is the extension's code, so it can throw; an empty list is a
            // worse listing than none, but it is better than no listing at all.
            filters.value = runCatching { source.getFilterList() }
                .getOrDefault(AnimeFilterList())
            _state.update {
                it.copy(
                    sourceName = source.name,
                    // Only an HTTP source has a site to open; a local or stub one does not,
                    // and offering WebView for those would lead nowhere.
                    baseUrl = (source as? AnimeHttpSource)?.baseUrl,
                )
            }
        }
    }

    data class State(
        val sourceName: String = "",
        val baseUrl: String? = null,
        val searchQuery: String? = null,
    )

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): AnimeCatalogViewModel
    }
}
