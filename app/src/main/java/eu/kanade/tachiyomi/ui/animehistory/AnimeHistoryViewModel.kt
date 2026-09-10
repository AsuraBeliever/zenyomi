package eu.kanade.tachiyomi.ui.animehistory

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
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations

/**
 * Recently watched anime, newest first.
 *
 * Kept apart from Mihon's manga history rather than merged into it: the two read from
 * separate databases, and the tabbed layout this project chose keeps them separate in
 * the UI too.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeHistoryViewModel(
    private val getAnimeHistory: GetAnimeHistory,
    private val removeAnimeHistory: RemoveAnimeHistory,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getAnimeHistory.subscribe("").collect { history ->
                _state.update { it.copy(isLoading = false, history = history) }
            }
        }
    }

    fun removeAll() {
        viewModelScope.launch { removeAnimeHistory.awaitAll() }
    }

    data class State(
        val isLoading: Boolean = true,
        val history: List<AnimeHistoryWithRelations> = emptyList(),
    ) {
        val isEmpty: Boolean get() = history.isEmpty()
    }
}
