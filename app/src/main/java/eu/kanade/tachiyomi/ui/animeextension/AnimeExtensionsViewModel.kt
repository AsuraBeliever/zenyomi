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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                extensionManager.installedExtensionsFlow,
                extensionManager.untrustedExtensionsFlow,
            ) { installed, untrusted -> installed to untrusted }
                .collect { (installed, untrusted) ->
                    _state.update {
                        it.copy(isLoading = false, installed = installed, untrusted = untrusted)
                    }
                }
        }
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
    ) {
        val isEmpty: Boolean get() = installed.isEmpty() && untrusted.isEmpty()
    }
}
