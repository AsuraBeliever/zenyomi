package mihon.data.animeextension.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.data.animeextension.service.AnimeExtensionStoreService
import mihon.domain.animeextension.repository.AnimeExtensionStoreRepository
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anime.AnimeDatabase
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AnimeExtensionStoreRepositoryImpl(
    private val service: AnimeExtensionStoreService,
    private val database: AnimeDatabase,
) : AnimeExtensionStoreRepository {
    override suspend fun insert(indexUrl: String): Result<Unit> {
        return service.fetch(indexUrl).mapCatching { upsert(it) }
    }

    override suspend fun insertFromPreference(indexUrl: String, name: String) {
        database.extension_storeQueries.upsert(
            indexUrl = indexUrl,
            name = name,
            badgeLabel = name,
            signingKey = "NO_SIGNING_KEY",
            contactWebsite = indexUrl,
            contactDiscord = null,
            isLegacy = false,
            extensionListUrl = null,
        )
    }

    override suspend fun refreshAll() {
        try {
            database.extension_storeQueries.getAll().awaitAsList().forEach { store ->
                service.fetch(store.index_url)
                    .mapCatching {
                        database.transaction {
                            upsert(it)
                            if (store.index_url != it.indexUrl) {
                                database.extension_storeQueries.delete(store.index_url)
                            }
                        }
                    }
                    .onFailure {
                        logcat(LogPriority.ERROR, it) {
                            "Failed to refresh extension store '${store.name} (${store.index_url})'"
                        }
                    }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    private suspend fun upsert(store: ExtensionStore) {
        database.extension_storeQueries.upsert(
            indexUrl = store.indexUrl,
            name = store.name,
            badgeLabel = store.badgeLabel,
            signingKey = store.signingKey,
            contactWebsite = store.contact.website,
            contactDiscord = store.contact.discord,
            isLegacy = store.isLegacy,
            extensionListUrl = store.extensionListUrl,
        )
    }

    override suspend fun fetchExtensions(): List<AnimeExtension.Available> {
        return try {
            supervisorScope {
                database.extension_storeQueries.getAll(::extensionStoreMapper).awaitAsList().map { store ->
                    async {
                        service.getExtensions(store).onFailure {
                            this@AnimeExtensionStoreRepositoryImpl.logcat(LogPriority.ERROR, it) {
                                "Failed to fetch extensions for store '${store.name} (${store.indexUrl})'"
                            }
                        }
                    }
                }
                    .awaitAll()
                    .flatMap { it.getOrDefault(emptyList()) }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun getAll(): List<ExtensionStore> {
        return database.extension_storeQueries.getAll(::extensionStoreMapper).awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<ExtensionStore>> {
        return database.extension_storeQueries.getAll(::extensionStoreMapper).subscribeToList()
    }

    override fun getCountAsFlow(): Flow<Long> {
        return database.extension_storeQueries
            .getCount()
            .subscribeToOne()
    }

    override suspend fun remove(indexUrl: String) {
        database.extension_storeQueries.delete(indexUrl)
    }

    private fun extensionStoreMapper(
        indexUrl: String,
        name: String,
        badgeLabel: String,
        signingKey: String,
        contactWebsite: String,
        contactDiscord: String?,
        isLegacy: Boolean,
        extensionListUrl: String?,
    ): ExtensionStore = ExtensionStore(
        indexUrl = indexUrl,
        name = name,
        badgeLabel = badgeLabel,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(
            website = contactWebsite,
            discord = contactDiscord,
        ),
        isLegacy = isLegacy,
        extensionListUrl = extensionListUrl,
    )
}
