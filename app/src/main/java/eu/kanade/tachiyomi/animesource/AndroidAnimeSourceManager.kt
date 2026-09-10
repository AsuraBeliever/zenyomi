package eu.kanade.tachiyomi.animesource

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animeextension.AnimeExtensionManager
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.anime.LocalAnimeSource
import java.util.concurrent.ConcurrentHashMap

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidAnimeSourceManager(
    private val localAnimeSource: LocalAnimeSource,
    private val extensionManager: AnimeExtensionManager,
    private val sourceRepository: AnimeStubSourceRepository,
) : AnimeSourceManager {

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    /**
     * Null until the extensions have loaded, so that nothing observes the empty seed value.
     */
    private val sourcesMapFlow = MutableStateFlow<Map<Long, AnimeSource>?>(null)

    private val stubSourcesMap = ConcurrentHashMap<Long, StubAnimeSource>()

    override val sources: Flow<List<AnimeSource>> = sourcesMapFlow
        .filterNotNull()
        .map { it.values.toList() }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, AnimeSource>(
                        mapOf(LocalAnimeSource.ID to localAnimeSource),
                    )
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubAnimeSource.from(it))
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                }
        }

        scope.launch {
            sourceRepository.subscribeAll()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = it
                    }
                }
        }
    }

    /**
     * Awaits the extensions to have loaded before returning the sources.
     */
    private suspend fun sourcesMap(): Map<Long, AnimeSource> = sourcesMapFlow.filterNotNull().first()

    override suspend fun get(sourceKey: Long): AnimeSource? {
        return sourcesMap()[sourceKey]
    }

    override suspend fun getOrStub(sourceKey: Long): AnimeSource {
        return sourcesMap()[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            createStubSource(sourceKey)
        }
    }

    override suspend fun getAll(): List<AnimeSource> {
        return sourcesMap().values.toList()
    }

    override suspend fun getOnlineSources(): List<AnimeHttpSource> {
        return sourcesMap().values.filterIsInstance<AnimeHttpSource>()
    }

    override suspend fun getStubSources(): List<StubAnimeSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubAnimeSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                // TODO: renombrar las carpetas de descarga cuando exista el gestor de descargas de anime
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubAnimeSource {
        sourceRepository.getStubSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubAnimeSource(id = id, lang = "", name = "")
    }
}
