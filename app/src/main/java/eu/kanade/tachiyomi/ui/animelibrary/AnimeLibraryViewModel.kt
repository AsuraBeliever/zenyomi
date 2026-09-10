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

/**
 * Backs the anime library tab.
 *
 * Deliberately smaller than Mihon's [eu.kanade.tachiyomi.ui.library.LibraryViewModel]:
 * no categories, filtering, sorting or selection yet. What it does do is real, it reads
 * the anime library straight out of the anime database rather than showing placeholder
 * data, which is also what causes anime.db to be created on first launch.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeLibraryViewModel(
    private val getLibraryAnime: GetLibraryAnime,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getLibraryAnime.subscribe().collect { library ->
                _state.update { it.copy(isLoading = false, library = library) }
            }
        }
    }

    data class State(
        val isLoading: Boolean = true,
        val library: List<LibraryAnime> = emptyList(),
    ) {
        val isEmpty: Boolean get() = library.isEmpty()
    }
}
