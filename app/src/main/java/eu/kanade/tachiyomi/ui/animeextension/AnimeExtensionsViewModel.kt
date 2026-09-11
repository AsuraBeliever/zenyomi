package eu.kanade.tachiyomi.ui.animeextension

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.animeextension.AnimeExtensionManager
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.animeextension.repository.AnimeExtensionStoreRepository

/**
 * Backs the anime extensions screen.
 *
 * Injecting [AnimeExtensionManager] here is what first starts the anime extension
 * loader: nothing else in the app asks for it yet, so before this screen existed the
 * loader never ran at all.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeExtensionsViewModel(
    private val extensionManager: AnimeExtensionManager,
    private val storeRepository: AnimeExtensionStoreRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow<String?>(null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Exposed so a search bar hosted by the surrounding tab can drive it. */
    val searchQuery: StateFlow<String?> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                extensionManager.installedExtensionsFlow,
                extensionManager.untrustedExtensionsFlow,
                extensionManager.availableExtensionsFlow,
                storeRepository.getAllAsFlow(),
            ) { installed, untrusted, available, stores ->
                State(
                    isLoading = false,
                    installed = installed,
                    untrusted = untrusted,
                    // An extension already on the device is not something to offer again.
                    available = available.filterNot { remote ->
                        installed.any { it.pkgName == remote.pkgName } ||
                            untrusted.any { it.pkgName == remote.pkgName }
                    },
                    storeCount = stores.size,
                    languages = available.map { it.lang }.distinct().sorted(),
                )
                // The combine rebuilds the whole state, so the refresh flag is carried
                // over by hand or it would be cleared on every emission.
            }.collect { next -> _state.update { current -> next.copy(isRefreshing = current.isRefreshing) } }
        }

        // The available list lives only in memory, so without this the screen opens empty
        // after every restart and looks as though the configured repositories were lost.
        refreshAvailable()
    }

    fun search(query: String?) {
        _searchQuery.value = query
        _state.update { it.copy(searchQuery = query) }
    }

    fun setLanguage(lang: String?) = _state.update { it.copy(selectedLanguage = lang) }

    fun refreshAvailable() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            runCatching { extensionManager.findAvailableExtensions() }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Adds an extension repository by its index url.
     *
     * Reports failure through state rather than throwing: a bad url or an unreachable
     * host is an ordinary thing for a user to hit while typing one in.
     */
    fun addStore(indexUrl: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = storeRepository.insert(indexUrl.trim())
            if (result.isSuccess) extensionManager.findAvailableExtensions()
            onResult(result.isSuccess)
        }
    }

    fun install(extension: AnimeExtension.Available) {
        viewModelScope.launch { extensionManager.installExtension(extension).collect() }
    }

    fun trust(extension: AnimeExtension.Untrusted) {
        viewModelScope.launch { extensionManager.trust(extension) }
    }

    fun uninstall(extension: AnimeExtension) {
        extensionManager.uninstallExtension(extension)
    }

    data class State(
        val isLoading: Boolean = true,
        val installed: List<AnimeExtension.Installed> = emptyList(),
        val untrusted: List<AnimeExtension.Untrusted> = emptyList(),
        val available: List<AnimeExtension.Available> = emptyList(),
        val isRefreshing: Boolean = false,
        val searchQuery: String? = null,
        val selectedLanguage: String? = null,
        val languages: List<String> = emptyList(),
        val storeCount: Int = 0,
    ) {
        val isEmpty: Boolean
            get() = installed.isEmpty() && untrusted.isEmpty() && available.isEmpty()

        /**
         * Available extensions, filtered and grouped by language the way Mihon groups the
         * manga ones. Computed here rather than kept in another flow: the list is already in
         * memory, and re-filtering a few hundred entries costs less than keeping a second
         * copy in sync.
         */
        val groupedAvailable: List<Pair<String, List<AnimeExtension.Available>>>
            get() {
                val query = searchQuery?.trim().orEmpty()
                return available
                    .asSequence()
                    .filter { selectedLanguage == null || it.lang == selectedLanguage }
                    .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
                    .groupBy { it.lang }
                    .toSortedMap(LocaleHelper.comparator)
                    .map { (lang, extensions) -> lang to extensions.sortedBy { it.name } }
            }

        /** True when a search or a language filter is hiding everything there is. */
        val isFilteredEmpty: Boolean
            get() = groupedAvailable.isEmpty() &&
                available.isNotEmpty() &&
                (!searchQuery.isNullOrBlank() || selectedLanguage != null)
    }
}
