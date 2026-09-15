package eu.kanade.tachiyomi.ui.animedetails.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.anime.interactor.UpdateAnimeNotes
import tachiyomi.domain.anime.model.Anime
import eu.kanade.presentation.manga.MangaNotesScreen as NotesContent

/**
 * Notas de un anime.
 *
 * La pantalla en si es la de Mihon: ahora pide un titulo y un texto en vez de un `Manga`, asi
 * que aqui no hay interfaz que duplicar, solo el modelo que guarda en la tabla de anime.
 */
class AnimeNotesScreen(
    private val anime: Anime,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = assistedMetroViewModel<Model, Model.Factory> { create(anime = anime) }
        val notes by viewModel.notes.collectAsState()

        NotesContent(
            title = anime.title,
            notes = notes,
            navigateUp = navigator::pop,
            onUpdate = viewModel::updateNotes,
        )
    }

    @AssistedInject
    class Model(
        @Assisted private val anime: Anime,
        private val updateAnimeNotes: UpdateAnimeNotes,
    ) : ViewModel() {

        private val _notes = MutableStateFlow(anime.notes)
        val notes: StateFlow<String> = _notes

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        interface Factory : ManualViewModelAssistedFactory {
            fun create(anime: Anime): Model
        }

        fun updateNotes(content: String) {
            if (content == _notes.value) return
            _notes.update { content }
            // Sin cancelar: salir de la pantalla no debe perder lo ultimo escrito.
            viewModelScope.launchNonCancellable {
                updateAnimeNotes(anime.id, content)
            }
        }
    }
}
