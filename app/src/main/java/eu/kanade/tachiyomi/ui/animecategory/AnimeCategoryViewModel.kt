package eu.kanade.tachiyomi.ui.animecategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.anime.model.AnimeCategory

/**
 * Managing the categories of the anime library.
 *
 * The default category is kept out of the list: it is not a category the user made, it is
 * where everything unfiled ends up, and offering to rename or delete it would be offering
 * something the database refuses anyway.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeCategoryViewModel(
    private val getAnimeCategories: GetAnimeCategories,
    private val createCategoryWithName: CreateAnimeCategoryWithName,
    private val renameCategory: RenameAnimeCategory,
    private val deleteCategory: DeleteAnimeCategory,
    private val reorderCategory: ReorderAnimeCategory,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            getAnimeCategories.subscribe().collect { categories ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        categories = categories.filterNot { category -> category.isSystemCategory },
                    )
                }
            }
        }
    }

    fun create(name: String) {
        viewModelScope.launch {
            when (createCategoryWithName.await(name.trim())) {
                CreateAnimeCategoryWithName.Result.NameAlreadyExists ->
                    _events.send(Event.NameAlreadyExists)
                is CreateAnimeCategoryWithName.Result.InternalError ->
                    _events.send(Event.InternalError)
                CreateAnimeCategoryWithName.Result.Success -> Unit
            }
        }
    }

    fun rename(category: AnimeCategory, name: String) {
        viewModelScope.launch {
            when (renameCategory.await(category, name.trim())) {
                RenameAnimeCategory.Result.NameAlreadyExists ->
                    _events.send(Event.NameAlreadyExists)
                is RenameAnimeCategory.Result.InternalError,
                RenameAnimeCategory.Result.CannotRenameSystemCategory,
                -> _events.send(Event.InternalError)
                RenameAnimeCategory.Result.Success -> Unit
            }
        }
    }

    fun delete(category: AnimeCategory) {
        viewModelScope.launch { deleteCategory.await(category.id) }
    }

    fun moveUp(category: AnimeCategory) {
        viewModelScope.launch { reorderCategory.moveUp(category) }
    }

    fun moveDown(category: AnimeCategory) {
        viewModelScope.launch { reorderCategory.moveDown(category) }
    }

    data class State(
        val isLoading: Boolean = true,
        val categories: List<AnimeCategory> = emptyList(),
    ) {
        val isEmpty: Boolean get() = categories.isEmpty()
    }

    sealed interface Event {
        data object NameAlreadyExists : Event
        data object InternalError : Event
    }
}
