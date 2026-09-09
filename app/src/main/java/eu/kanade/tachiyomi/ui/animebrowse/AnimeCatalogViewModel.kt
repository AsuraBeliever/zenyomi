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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Browses one anime source's catalogue.
 *
 * Takes the listing from [GetRemoteAnime], which decides between popular, latest and
 * search by the sentinel query it is given, exactly as Mihon's browse does.
 */
@AssistedInject
class AnimeCatalogViewModel(
    @Assisted private val sourceId: Long,
    private val getRemoteAnime: GetRemoteAnime,
    private val sourceManager: AnimeSourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val animeList = Pager(PagingConfig(pageSize = 25)) {
        getRemoteAnime.subscribe(sourceId, listingQuery, AnimeFilterList())
    }.flow.cachedIn(viewModelScope)

    private val listingQuery get() = GetRemoteAnime.QUERY_POPULAR

    init {
        viewModelScope.launch {
            val source = sourceManager.getOrStub(sourceId)
            _state.update { it.copy(sourceName = source.name) }
        }
    }

    data class State(val sourceName: String = "")

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(sourceId: Long): AnimeCatalogViewModel
    }
}
