package eu.kanade.tachiyomi.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference

/**
 * The Browse tab's own layout choices, as state the two lists can read.
 *
 * A view model rather than reaching into the graph from a composable: these are read by two
 * screens and written by both, and a shared owner is the difference between one source of
 * truth and two that drift.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class BrowseLayoutViewModel(
    private val preferences: BrowseLayoutPreferences,
) : ViewModel() {

    val sourcesMedia = preferences.sourcesMedia.state()
    val extensionsMedia = preferences.extensionsMedia.state()
    val mangaSourcesExpanded = preferences.mangaSourcesExpanded.state()
    val animeSourcesExpanded = preferences.animeSourcesExpanded.state()
    val mangaExtensionsExpanded = preferences.mangaExtensionsExpanded.state()
    val animeExtensionsExpanded = preferences.animeExtensionsExpanded.state()

    fun setSourcesMedia(media: BrowseMedia) = preferences.sourcesMedia.set(media)

    fun setExtensionsMedia(media: BrowseMedia) = preferences.extensionsMedia.set(media)

    fun toggleMangaSources() = preferences.mangaSourcesExpanded.toggle()

    fun toggleAnimeSources() = preferences.animeSourcesExpanded.toggle()

    fun toggleMangaExtensions() = preferences.mangaExtensionsExpanded.toggle()

    fun toggleAnimeExtensions() = preferences.animeExtensionsExpanded.toggle()

    private fun Preference<Boolean>.toggle() = set(!get())

    private fun <T> Preference<T>.state(): StateFlow<T> =
        changes().stateIn(viewModelScope, SharingStarted.Eagerly, get())
}
