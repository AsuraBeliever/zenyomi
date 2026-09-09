package eu.kanade.tachiyomi.ui.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Lists the anime sources the loaded extensions provide.
 *
 * Reads [AnimeSourceManager.sources] directly rather than going through an enabled or
 * pinned filter: those interactors depend on source preferences that are not ported yet,
 * so every loaded source is shown for now.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeSourcesViewModel(
    private val sourceManager: AnimeSourceManager,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sourceManager.sources.collect { sources ->
                _state.update { it.copy(isLoading = false, sources = sources) }
            }
        }
    }

    data class State(
        val isLoading: Boolean = true,
        val sources: List<AnimeSource> = emptyList(),
    ) {
        val isEmpty: Boolean get() = sources.isEmpty()
    }
}
