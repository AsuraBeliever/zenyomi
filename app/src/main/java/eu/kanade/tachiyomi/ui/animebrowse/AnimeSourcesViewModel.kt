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
import eu.kanade.tachiyomi.ui.animebrowse.setting.AnimeSourcePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Lists the anime sources the loaded extensions provide.
 *
 * Pinned sources come first and hidden ones are left out, because with a couple of dozen
 * installed — most of them broken on any given day — an unordered list of everything is
 * mostly noise. Hiding is not uninstalling: the extension stays, and the list can be shown
 * again from the menu.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeSourcesViewModel(
    private val sourceManager: AnimeSourceManager,
    private val sourceHealth: AnimeSourceHealth,
    private val preferences: AnimeSourcePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sourceManager.sources,
                preferences.pinnedSources.changes(),
                preferences.hiddenSources.changes(),
            ) { sources, pinned, hidden -> Triple(sources, pinned, hidden) }
                .collect { (sources, pinned, hidden) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            sources = sources,
                            pinned = pinned,
                            hidden = hidden,
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

    fun toggleShowHidden() = _state.update { it.copy(showHidden = !it.showHidden) }

    fun togglePinned(source: AnimeSource) = preferences.pinnedSources.toggle(source.id)

    fun toggleHidden(source: AnimeSource) = preferences.hiddenSources.toggle(source.id)

    private fun Preference<Set<String>>.toggle(id: Long) {
        val key = id.toString()
        set(if (key in get()) get() - key else get() + key)
    }

    data class State(
        val isLoading: Boolean = true,
        val sources: List<AnimeSource> = emptyList(),
        val selectedLanguage: String? = null,
        val pinned: Set<String> = emptySet(),
        val hidden: Set<String> = emptySet(),
        val showHidden: Boolean = false,
        /** Sources our last sweep found broken, and why. See [AnimeSourceHealth]. */
        val broken: Map<Long, AnimeSourceHealth.Reason> = emptyMap(),
        val checkedOn: String = "",
    ) {
        /** Languages actually present, so the filter never offers one that matches nothing. */
        val languages: List<String>
            get() = sources.map { it.lang }.distinct().sorted()

        fun isPinned(source: AnimeSource) = source.id.toString() in pinned

        fun isHidden(source: AnimeSource) = source.id.toString() in hidden

        val hiddenCount: Int get() = sources.count { isHidden(it) }

        /**
         * Pinned first, then the rest, each alphabetically. Hidden ones only when asked for,
         * and always last — they are there to be un-hidden, not to be browsed.
         */
        val visibleSources: List<AnimeSource>
            get() {
                val inLanguage = sources.filter {
                    selectedLanguage == null || it.lang == selectedLanguage
                }
                val (hiddenOnes, shown) = inLanguage.partition { isHidden(it) }
                val (pinnedOnes, rest) = shown.partition { isPinned(it) }
                return pinnedOnes.sortedBy { it.name.lowercase() } +
                    rest.sortedBy { it.name.lowercase() } +
                    if (showHidden) hiddenOnes.sortedBy { it.name.lowercase() } else emptyList()
            }

        val isEmpty: Boolean get() = sources.isEmpty()

        /** Nothing shown because of the language filter, rather than because of hiding. */
        val isFilteredEmpty: Boolean
            get() = visibleSources.isEmpty() && sources.isNotEmpty() && hiddenCount == 0

        /** Everything that would show has been hidden, which needs different words. */
        val isAllHidden: Boolean
            get() = visibleSources.isEmpty() && sources.isNotEmpty() && hiddenCount > 0
    }
}
