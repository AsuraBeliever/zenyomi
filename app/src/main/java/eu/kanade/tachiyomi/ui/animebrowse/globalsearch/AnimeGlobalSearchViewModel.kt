package eu.kanade.tachiyomi.ui.animebrowse.globalsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.ui.animebrowse.setting.AnimeSourcePreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import mihon.domain.anime.model.toDomainAnime
import mihon.domain.migration.anime.MigrateAnimeUseCase
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import kotlin.time.Duration.Companion.seconds

/**
 * One query, every installed anime source.
 *
 * Sources are asked concurrently but not all at once: a phone opening twenty TLS connections
 * to twenty sites at the same moment is slower than doing a few at a time, and some hosts
 * treat the burst as abuse.
 *
 * Each source keeps its own state. That is the whole point — with an ecosystem where most
 * sources are broken at any given moment, one that fails must not take the results of the
 * others with it, and must say so in its own row rather than as a screenful of error.
 *
 * Sources hidden in the source list are not asked at all.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AnimeGlobalSearchViewModel(
    private val sourceManager: AnimeSourceManager,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val sourcePreferences: AnimeSourcePreferences,
    private val getAnime: GetAnime,
    private val migrateAnime: MigrateAnimeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(query: String?) = _state.update { it.copy(query = query) }

    fun search() {
        val query = state.value.query?.trim().orEmpty()
        if (query.isEmpty()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // Hidden sources are skipped. Hiding one is the user saying it is not worth
            // asking, and asking it anyway is slower and adds a row of noise to every search.
            val hidden = sourcePreferences.hiddenSources.get()
            val sources = sourceManager.getAll()
                .filterIsInstance<AnimeCatalogueSource>()
                .filterNot { it.id.toString() in hidden }
            _state.update { state ->
                state.copy(
                    results = sources.map { SourceResult(it.id, it.name, it.lang) },
                    searched = true,
                )
            }

            val gate = Semaphore(CONCURRENCY)
            sources.forEach { source ->
                launch {
                    gate.withPermit {
                        val result = runCatching {
                            withIOContext {
                                withTimeout(TIMEOUT) {
                                    source.getSearchAnime(1, query, AnimeFilterList()).animes
                                        .take(PER_SOURCE)
                                        .map { networkToLocalAnime.await(it.toDomainAnime(source.id)) }
                                }
                            }
                        }.onFailure {
                            logcat(LogPriority.DEBUG, it) { "Global search failed for ${source.name}" }
                        }

                        _state.update { state ->
                            state.copy(
                                results = state.results.map {
                                    if (it.sourceId != source.id) {
                                        it
                                    } else {
                                        it.copy(
                                            loading = false,
                                            anime = result.getOrDefault(emptyList()),
                                            error = result.exceptionOrNull(),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Offers the chosen result as a migration target. The confirmation is not optional: this
     * can take an entry out of the library and delete its downloads.
     */
    fun askToMigrate(from: Long, to: Anime) {
        viewModelScope.launch {
            val current = getAnime.await(from) ?: return@launch
            _state.update { it.copy(migration = Migration(current, to)) }
        }
    }

    fun dismissMigration() = _state.update { it.copy(migration = null) }

    fun migrate(replace: Boolean) {
        val migration = state.value.migration ?: return
        _state.update { it.copy(migration = null, migrating = true) }
        viewModelScope.launch {
            val result = migrateAnime(migration.current, migration.target, replace)
            _state.update { it.copy(migrating = false, migrated = result.isSuccess) }
        }
    }

    fun clearMigrated() = _state.update { it.copy(migrated = false) }

    data class Migration(val current: Anime, val target: Anime)

    data class SourceResult(
        val sourceId: Long,
        val sourceName: String,
        val lang: String,
        val loading: Boolean = true,
        val anime: List<Anime> = emptyList(),
        val error: Throwable? = null,
    )

    data class State(
        val query: String? = null,
        val results: List<SourceResult> = emptyList(),
        val searched: Boolean = false,
        val migration: Migration? = null,
        val migrating: Boolean = false,
        val migrated: Boolean = false,
    ) {
        /**
         * Sources that found nothing are dropped once they are done, so the screen is results
         * rather than a directory of sources with "no results" under most of them.
         */
        val visible: List<SourceResult>
            get() = results.filter { it.loading || it.anime.isNotEmpty() || it.error != null }
    }

    private companion object {
        const val CONCURRENCY = 5
        const val PER_SOURCE = 10
        val TIMEOUT = 30.seconds
    }
}
