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
import kotlinx.coroutines.flow.distinctUntilChanged
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
     * Re-pages on every change of query. [flatMapLatest] rather than a Pager built once:
     * the query is part of what is being paged, so a new one is a new pager, and the old
     * one's in-flight page must not land in the new list.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val animeList = _state
        .map { it.searchQuery }
        // Without this the source name arriving would rebuild the pager and refetch page one.
        .distinctUntilChanged()
        .flatMapLatest { query ->
            Pager(PagingConfig(pageSize = 25)) {
                getRemoteAnime.subscribe(
                    sourceId,
                    query?.takeIf { it.isNotBlank() } ?: GetRemoteAnime.QUERY_POPULAR,
                    AnimeFilterList(),
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    fun search(query: String?) = _state.update { it.copy(searchQuery = query) }

    init {
        viewModelScope.launch {
            val source = sourceManager.getOrStub(sourceId)
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
