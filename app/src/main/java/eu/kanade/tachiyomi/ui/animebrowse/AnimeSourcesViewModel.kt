package eu.kanade.tachiyomi.ui.animebrowse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.presentation.anime.AnimeSourceHealth
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
    private val sourceHealth: AnimeSourceHealth,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sourceManager.sources.collect { sources ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        sources = sources,
                        checkedOn = sourceHealth.checkedOn,
                        broken = sources.mapNotNull { source ->
                            sourceHealth.statusOf(source.id)?.let { reason -> source.id to reason }
                        }.toMap(),
                    )
                }
            }
        }
    }

    fun setLanguage(lang: String?) = _state.update { it.copy(selectedLanguage = lang) }

    data class State(
        val isLoading: Boolean = true,
        val sources: List<AnimeSource> = emptyList(),
        val selectedLanguage: String? = null,
        /** Sources our last sweep found broken, and why. See [AnimeSourceHealth]. */
        val broken: Map<Long, AnimeSourceHealth.Reason> = emptyMap(),
        val checkedOn: String = "",
    ) {
        /** Languages actually present, so the filter never offers one that matches nothing. */
        val languages: List<String>
            get() = sources.map { it.lang }.distinct().sorted()

        val visibleSources: List<AnimeSource>
            get() = sources.filter { selectedLanguage == null || it.lang == selectedLanguage }

        val isEmpty: Boolean get() = sources.isEmpty()

        val isFilteredEmpty: Boolean
            get() = visibleSources.isEmpty() && sources.isNotEmpty()
    }
}
